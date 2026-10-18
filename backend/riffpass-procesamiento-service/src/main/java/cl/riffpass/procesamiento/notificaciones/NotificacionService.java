package cl.riffpass.procesamiento.notificaciones;

import cl.riffpass.mensajeria.EventoMensaje;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arma y registra la notificación. El canal de envío (correo) se simula con el log. */
@Service
public class NotificacionService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    static final String TODOS = "TODOS_LOS_ASISTENTES";
    private final NotificacionRepository notificaciones;
    public NotificacionService(NotificacionRepository notificaciones) { this.notificaciones = notificaciones; }

    @Transactional
    public void notificar(EventoMensaje evento) {
        Notificacion notificacion = switch (evento.tipo()) {
            case ENTRADA_EMITIDA -> new Notificacion(evento.id(), evento.dato("titularSub"),
                    "Tu entrada RiffPass está lista",
                    "Tu entrada para el concierto " + evento.dato("eventoId") + " fue emitida. Código: " + evento.dato("codigo"));
            case EVENTO_CREADO -> new Notificacion(evento.id(), TODOS,
                    "Nuevo concierto: " + evento.dato("artista"),
                    evento.dato("nombre") + " en " + evento.dato("ciudad") + " el " + evento.dato("fecha"));
            default -> throw new IllegalArgumentException("Tipo sin notificación: " + evento.tipo());
        };
        notificaciones.save(notificacion);
        log.info("NOTIFICACION enviada tipo={} eventoId={}", evento.tipo(), evento.id());
    }
}
