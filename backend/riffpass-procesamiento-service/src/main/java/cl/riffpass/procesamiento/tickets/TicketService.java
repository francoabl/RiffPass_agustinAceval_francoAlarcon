package cl.riffpass.procesamiento.tickets;

import cl.riffpass.mensajeria.EventoMensaje;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private final TicketRepository tickets;
    public TicketService(TicketRepository tickets) { this.tickets = tickets; }

    @Transactional
    public void generar(EventoMensaje evento) {
        UUID entradaId = UUID.fromString(evento.dato("entradaId"));
        if (tickets.existsById(entradaId)) return;
        UUID eventoId = UUID.fromString(evento.dato("eventoId"));
        UUID codigo = UUID.fromString(evento.dato("codigo"));
        String contenido = "RIFFPASS | ENTRADA DIGITAL\nConcierto: " + eventoId + "\nCódigo de acceso: " + codigo
                + "\nEmitida: " + evento.dato("emitidaEn") + "\nPresenta este código en la puerta. Es válido una sola vez.";
        tickets.save(new Ticket(entradaId, eventoId, evento.dato("titularSub"), codigo, contenido));
        log.info("TICKET generado entradaId={} codigo={}", entradaId, codigo);
    }
}
