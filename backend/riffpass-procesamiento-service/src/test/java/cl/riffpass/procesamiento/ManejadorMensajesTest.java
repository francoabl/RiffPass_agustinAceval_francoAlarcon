package cl.riffpass.procesamiento;

import cl.riffpass.mensajeria.*;
import cl.riffpass.procesamiento.mensajeria.*;
import cl.riffpass.procesamiento.notificaciones.NotificacionRepository;
import cl.riffpass.procesamiento.tickets.*;
import cl.riffpass.procesamiento.auditoria.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.*;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManejadorMensajesTest {
    @Autowired ManejadorMensajes manejador;
    @Autowired TicketService tickets;
    @Autowired TicketRepository ticketRepository;
    @Autowired NotificacionRepository notificaciones;
    @Autowired RegistroAuditoriaRepository auditoria;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean ReenvioDlq dlq;
    Acknowledgement ack;
    Visibility visibilidad;

    @BeforeEach void limpiar() {
        jdbc.update("DELETE FROM mensajes_procesados");
        ticketRepository.deleteAll(); notificaciones.deleteAll(); auditoria.deleteAll();
        ack = mock(Acknowledgement.class);
        visibilidad = mock(Visibility.class);
        when(dlq.enviar(anyString(), anyString(), anyMap())).thenReturn("dlq-id");
    }

    private EventoMensaje emitida(String simulacion) {
        return EventoMensaje.nuevo(TipoEvento.ENTRADA_EMITIDA, Map.of("entradaId", UUID.randomUUID().toString(),
                "eventoId", UUID.randomUUID().toString(), "titularSub", "ana", "codigo", UUID.randomUUID().toString(),
                "emitidaEn", Instant.now().toString())).conOrigen("test").conSimulacion(simulacion);
    }
    private Message<String> mensaje(Object cuerpo, int intento) throws Exception {
        String json = cuerpo instanceof String texto ? texto : mapper.writeValueAsString(cuerpo);
        return MessageBuilder.withPayload(json)
                .setHeader(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT, Integer.toString(intento)).build();
    }
    private void tickets(Message<String> mensaje) {
        manejador.procesar(CasoUso.TICKETS, mensaje, ack, visibilidad, tickets::generar);
    }

    @Test void processesAndAcknowledgesExplicitly() throws Exception {
        tickets(mensaje(emitida(null), 1));
        assertEquals(1, ticketRepository.count());
        verify(ack).acknowledge();
        verifyNoInteractions(visibilidad, dlq);
    }

    @Test void duplicatedMessageIsAcknowledgedWithoutReprocessing() throws Exception {
        EventoMensaje evento = emitida(null);
        tickets(mensaje(evento, 1));
        tickets(mensaje(evento, 2));
        assertEquals(1, ticketRepository.count());
        verify(ack, times(2)).acknowledge();
    }

    @Test void recoverableErrorIsRetriedWithBackoffAndRolledBack() throws Exception {
        EventoMensaje evento = emitida("TRANSITORIO");
        tickets(mensaje(evento, 1));
        verify(ack, never()).acknowledge();
        verify(visibilidad).changeTo(5);
        assertEquals(0, ticketRepository.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mensajes_procesados", Integer.class));
        tickets(mensaje(evento, 2));
        verify(ack).acknowledge();
        assertEquals(1, ticketRepository.count());
    }

    @Test void lastRecoverableAttemptIsLeftForTheRedrivePolicy() throws Exception {
        tickets(mensaje(emitida("PERMANENTE"), 3));
        verify(ack, never()).acknowledge();
        verify(visibilidad).changeTo(0);
        verifyNoInteractions(dlq);
    }

    @Test void nonRecoverableErrorsGoToTheDlqAndAreAcknowledged() throws Exception {
        tickets(mensaje(emitida("NO_RECUPERABLE"), 1));
        tickets(mensaje("{no es json", 1));
        tickets(mensaje(EventoMensaje.nuevo(TipoEvento.EVENTO_CREADO, Map.of("eventoId", "x")), 1));
        tickets(mensaje(EventoMensaje.nuevo(TipoEvento.ENTRADA_EMITIDA, Map.of("eventoId", "x")), 1));
        verify(dlq, times(4)).enviar(eq("riffpass-tickets-dlq"), anyString(), argThat(a ->
                "1".equals(a.get(AtributosMensaje.INTENTO)) && a.get(AtributosMensaje.CAUSA) != null
                        && "riffpass-tickets".equals(a.get(AtributosMensaje.COLA_ORIGEN))));
        verify(ack, times(4)).acknowledge();
        assertEquals(0, ticketRepository.count());
    }

    @Test void simulationCanTargetOneQueueOnly() throws Exception {
        EventoMensaje evento = emitida("NO_RECUPERABLE@AUDITORIA");
        tickets(mensaje(evento, 1));
        assertEquals(1, ticketRepository.count());
        verifyNoInteractions(dlq);
    }

    @Test void controlApiRequiresAdministratorToken() throws Exception {
        mvc.perform(get("/api/consumidores")).andExpect(status().isUnauthorized());
    }
}
