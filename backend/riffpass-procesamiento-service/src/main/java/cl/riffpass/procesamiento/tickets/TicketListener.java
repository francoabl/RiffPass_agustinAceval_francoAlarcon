package cl.riffpass.procesamiento.tickets;

import cl.riffpass.mensajeria.CasoUso;
import cl.riffpass.procesamiento.mensajeria.ManejadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Consumidor de la cola de tickets: genera el comprobante de cada entrada emitida. */
@Component
public class TicketListener {
    private final ManejadorMensajes manejador;
    private final TicketService tickets;
    public TicketListener(ManejadorMensajes manejador, TicketService tickets) {
        this.manejador = manejador; this.tickets = tickets;
    }
    @SqsListener(value = "${riffpass.mensajeria.colas.tickets.nombre}",
            id = "${riffpass.mensajeria.colas.tickets.consumidor}", acknowledgementMode = "MANUAL")
    public void recibir(Message<String> mensaje, Acknowledgement ack, Visibility visibilidad) {
        manejador.procesar(CasoUso.TICKETS, mensaje, ack, visibilidad, tickets::generar);
    }
}
