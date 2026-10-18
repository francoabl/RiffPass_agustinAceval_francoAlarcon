package cl.riffpass.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

/**
 * Productor SNS compartido. Los servicios de negocio solo publican un {@link EventoMensaje} como evento
 * de Spring; este componente lo envía al tópico después del commit, para no anunciar cambios revertidos.
 */
@Component
public class PublicadorEventos {
    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
    private final SnsClient sns;
    private final ObjectMapper mapper;
    private final MensajeriaProperties props;
    private final String origen;
    private final AtomicReference<String> topicArn = new AtomicReference<>();

    public PublicadorEventos(SnsClient sns, ObjectMapper mapper, MensajeriaProperties props,
                             @Value("${spring.application.name}") String origen) {
        this.sns = sns; this.mapper = mapper; this.props = props; this.origen = origen;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void alConfirmar(EventoMensaje evento) {
        if (!props.habilitada()) {
            log.debug("Mensajería deshabilitada; no se publica {} {}", evento.tipo(), evento.id());
            return;
        }
        try {
            publicar(evento);
        } catch (RuntimeException ex) {
            // La operación de negocio ya se confirmó; el fallo queda registrado sin afectar al usuario.
            log.error("NO PUBLICADO eventoId={} tipo={} topico={} causa={}", evento.id(), evento.tipo(),
                    props.topico(), ex.toString());
        }
    }

    /** Publica de forma síncrona en el tópico configurado y devuelve el MessageId de SNS. */
    public String publicar(EventoMensaje evento) {
        EventoMensaje mensaje = evento.origen() == null ? evento.conOrigen(origen) : evento;
        try {
            PublishResponse respuesta = sns.publish(PublishRequest.builder().topicArn(arn())
                    .message(mapper.writeValueAsString(mensaje))
                    .messageAttributes(Map.of(
                            AtributosMensaje.TIPO, texto(mensaje.tipo().name()),
                            AtributosMensaje.ORIGEN, texto(mensaje.origen())))
                    .build());
            log.info("PUBLICADO snsId={} eventoId={} tipo={} topico={}", respuesta.messageId(), mensaje.id(),
                    mensaje.tipo(), props.topico());
            return respuesta.messageId();
        } catch (NotFoundException ex) {
            topicArn.set(null);
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el mensaje", ex);
        }
    }

    private String arn() {
        // CreateTopic es idempotente: devuelve el ARN del tópico ya declarado por el administrador.
        return topicArn.updateAndGet(actual -> actual != null ? actual
                : sns.createTopic(r -> r.name(props.topico())).topicArn());
    }

    private static MessageAttributeValue texto(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }
}
