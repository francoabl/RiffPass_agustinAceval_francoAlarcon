package cl.riffpass.procesamiento.mensajeria;

import cl.riffpass.mensajeria.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.*;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Política común de confirmación y errores para todos los consumidores:
 * <ul>
 *   <li>Éxito o duplicado: ACK explícito (SQS elimina el mensaje).</li>
 *   <li>Error recuperable: sin ACK; se aplaza con visibilidad creciente y SQS lo vuelve a entregar.
 *       Al superar maxReceiveCount, la Redrive Policy lo mueve a la DLQ.</li>
 *   <li>Error no recuperable: se envía a la DLQ con su causa y luego se confirma, sin reintentos inútiles.</li>
 * </ul>
 * Cada mensaje no entregado queda en el log con su id, intento y causa.
 */
@Component
public class ManejadorMensajes {
    private static final Logger log = LoggerFactory.getLogger(ManejadorMensajes.class);
    private final MensajeriaProperties props;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaccion;
    private final RegistroIdempotencia idempotencia;
    private final ReenvioDlq dlq;
    private final String instancia;

    public ManejadorMensajes(MensajeriaProperties props, ObjectMapper mapper, TransactionTemplate transaccion,
                             RegistroIdempotencia idempotencia, ReenvioDlq dlq,
                             @Value("${riffpass.instancia}") String instancia) {
        this.props = props; this.mapper = mapper; this.transaccion = transaccion;
        this.idempotencia = idempotencia; this.dlq = dlq; this.instancia = instancia;
    }

    public void procesar(CasoUso caso, Message<String> mensaje, Acknowledgement ack, Visibility visibilidad,
                         Consumer<EventoMensaje> accion) {
        MensajeriaProperties.Cola cola = props.cola(caso);
        String sqsId = String.valueOf(mensaje.getHeaders().getId());
        int intento = entero(mensaje.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT), 1);
        int reprocesos = entero(mensaje.getHeaders().get(AtributosMensaje.REPROCESOS), 0);
        EventoMensaje evento = null;
        try {
            evento = leer(mensaje.getPayload(), cola);
            EventoMensaje actual = evento;
            boolean nuevo = Boolean.TRUE.equals(transaccion.execute(estado -> {
                if (!idempotencia.registrar(cola.nombre(), actual.id(), instancia)) return false;
                simularFallo(actual, caso, intento, reprocesos);
                accion.accept(actual);
                return true;
            }));
            ack.acknowledge();
            if (nuevo) log.info("PROCESADO instancia={} cola={} sqsId={} eventoId={} tipo={} intento={}",
                    instancia, cola.nombre(), sqsId, evento.id(), evento.tipo(), intento);
            else log.info("DUPLICADO DESCARTADO instancia={} cola={} sqsId={} eventoId={} intento={}",
                    instancia, cola.nombre(), sqsId, evento.id(), intento);
        } catch (ErrorNoRecuperable | IllegalArgumentException | DataIntegrityViolationException ex) {
            noRecuperable(cola, mensaje.getPayload(), sqsId, evento, intento, causa(ex), ack);
        } catch (RuntimeException ex) {
            recuperable(cola, sqsId, evento, intento, causa(ex), visibilidad);
        }
    }

    private EventoMensaje leer(String cuerpo, MensajeriaProperties.Cola cola) {
        try {
            EventoMensaje evento = mapper.readValue(cuerpo, EventoMensaje.class);
            if (evento.id() == null || evento.tipo() == null)
                throw new ErrorNoRecuperable("El mensaje no tiene id o tipo");
            if (!cola.acepta(evento.tipo()))
                throw new ErrorNoRecuperable("El tipo " + evento.tipo() + " no corresponde a la cola " + cola.nombre());
            return evento;
        } catch (JsonProcessingException ex) {
            throw new ErrorNoRecuperable("JSON inválido: " + ex.getOriginalMessage(), ex);
        }
    }

    private void recuperable(MensajeriaProperties.Cola cola, String sqsId, EventoMensaje evento, int intento,
                             String causa, Visibility visibilidad) {
        if (intento >= cola.maxRecepciones()) {
            log.error("NO ENTREGADO instancia={} cola={} sqsId={} eventoId={} intento={}/{} causa=\"{}\" destino={} via=RedrivePolicy",
                    instancia, cola.nombre(), sqsId, id(evento), intento, cola.maxRecepciones(), causa, cola.dlq());
            // Visible de inmediato: en la próxima recepción SQS supera maxReceiveCount y lo mueve a la DLQ.
            cambiarVisibilidad(visibilidad, 0, sqsId);
            return;
        }
        int espera = Math.min(5 * (1 << (intento - 1)), (int) cola.visibilidad().toSeconds() * 4);
        log.warn("REINTENTO instancia={} cola={} sqsId={} eventoId={} intento={}/{} causa=\"{}\" proximoEnSeg={}",
                instancia, cola.nombre(), sqsId, id(evento), intento, cola.maxRecepciones(), causa, espera);
        cambiarVisibilidad(visibilidad, espera, sqsId);
    }

    private void noRecuperable(MensajeriaProperties.Cola cola, String cuerpo, String sqsId, EventoMensaje evento,
                               int intento, String causa, Acknowledgement ack) {
        try {
            String dlqId = dlq.enviar(cola.dlq(), cuerpo, ReenvioDlq.atributos(
                    evento == null ? null : evento.tipo().name(), causa, intento, cola.nombre(), sqsId));
            ack.acknowledge();
            log.error("NO ENTREGADO instancia={} cola={} sqsId={} eventoId={} intento={} causa=\"{}\" destino={} dlqId={} via=NoRecuperable",
                    instancia, cola.nombre(), sqsId, id(evento), intento, causa, cola.dlq(), dlqId);
        } catch (RuntimeException ex) {
            // Sin ACK: el mensaje se reintentará y, en último caso, la Redrive Policy lo moverá a la DLQ.
            log.error("FALLO AL ENVIAR A DLQ instancia={} cola={} sqsId={} intento={} causa=\"{}\" error=\"{}\"",
                    instancia, cola.nombre(), sqsId, intento, causa, causa(ex));
        }
    }

    /**
     * Fallos controlados para la demostración. Formato: MODO o MODO@CASO (por ejemplo PERMANENTE@TICKETS).
     * TRANSITORIO falla solo el primer intento; PERMANENTE falla hasta que el administrador reprocesa el
     * mensaje desde la DLQ (simula que se corrigió la causa); NO_RECUPERABLE va directo a la DLQ.
     */
    private void simularFallo(EventoMensaje evento, CasoUso caso, int intento, int reprocesos) {
        if (!props.simulacionFallos() || evento.simulacion() == null) return;
        String[] partes = evento.simulacion().split("@");
        if (partes.length > 1 && !partes[1].equalsIgnoreCase(caso.name())) return;
        switch (partes[0].toUpperCase()) {
            case "TRANSITORIO" -> { if (intento < 2) throw new ErrorRecuperable("Timeout simulado de una dependencia"); }
            case "PERMANENTE" -> { if (reprocesos == 0) throw new ErrorRecuperable("Dependencia no disponible (simulado)"); }
            case "NO_RECUPERABLE" -> throw new ErrorNoRecuperable("Datos inconsistentes (simulado)");
            default -> { }
        }
    }

    private void cambiarVisibilidad(Visibility visibilidad, int segundos, String sqsId) {
        try {
            visibilidad.changeTo(segundos);
        } catch (RuntimeException ex) {
            log.warn("No se pudo cambiar la visibilidad de {}; se usará el visibility timeout de la cola", sqsId);
        }
    }

    private static int entero(Object valor, int porDefecto) {
        try { return valor == null ? porDefecto : Integer.parseInt(valor.toString()); }
        catch (NumberFormatException ex) { return porDefecto; }
    }
    private static String id(EventoMensaje evento) { return evento == null ? "desconocido" : evento.id().toString(); }
    private static String causa(Throwable ex) {
        Throwable raiz = ex;
        while (raiz.getCause() != null && raiz.getCause() != raiz && !(raiz instanceof ErrorNoRecuperable)) raiz = raiz.getCause();
        return raiz.getClass().getSimpleName() + ": " + Objects.toString(raiz.getMessage(), "sin detalle");
    }
}
