package cl.riffpass.procesamiento.auditoria;

import cl.riffpass.mensajeria.CasoUso;
import cl.riffpass.procesamiento.mensajeria.ManejadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Consumidor de la cola de auditoría: recibe todos los tipos de evento (fanout sin filtro). */
@Component
public class AuditoriaListener {
    private final ManejadorMensajes manejador;
    private final AuditoriaService auditoria;
    public AuditoriaListener(ManejadorMensajes manejador, AuditoriaService auditoria) {
        this.manejador = manejador; this.auditoria = auditoria;
    }
    @SqsListener(value = "${riffpass.mensajeria.colas.auditoria.nombre}",
            id = "${riffpass.mensajeria.colas.auditoria.consumidor}", acknowledgementMode = "MANUAL")
    public void recibir(Message<String> mensaje, Acknowledgement ack, Visibility visibilidad) {
        manejador.procesar(CasoUso.AUDITORIA, mensaje, ack, visibilidad, auditoria::registrar);
    }
}
