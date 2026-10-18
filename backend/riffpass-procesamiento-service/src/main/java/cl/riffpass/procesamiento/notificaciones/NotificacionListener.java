package cl.riffpass.procesamiento.notificaciones;

import cl.riffpass.mensajeria.CasoUso;
import cl.riffpass.procesamiento.mensajeria.ManejadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Consumidor de la cola de notificaciones: avisos al titular y anuncios de conciertos. */
@Component
public class NotificacionListener {
    private final ManejadorMensajes manejador;
    private final NotificacionService notificaciones;
    public NotificacionListener(ManejadorMensajes manejador, NotificacionService notificaciones) {
        this.manejador = manejador; this.notificaciones = notificaciones;
    }
    @SqsListener(value = "${riffpass.mensajeria.colas.notificaciones.nombre}",
            id = "${riffpass.mensajeria.colas.notificaciones.consumidor}", acknowledgementMode = "MANUAL")
    public void recibir(Message<String> mensaje, Acknowledgement ack, Visibility visibilidad) {
        manejador.procesar(CasoUso.NOTIFICACIONES, mensaje, ack, visibilidad, notificaciones::notificar);
    }
}
