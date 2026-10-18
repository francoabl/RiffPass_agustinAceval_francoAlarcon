package cl.riffpass.entradas;

import cl.riffpass.common.ApiException;
import cl.riffpass.mensajeria.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EntradaService {
    private final InventarioRepository inventarios;
    private final EntradaRepository entradas;
    private final EventoClient eventos;
    private final ApplicationEventPublisher mensajes;
    public EntradaService(InventarioRepository inventarios, EntradaRepository entradas, EventoClient eventos,
                          ApplicationEventPublisher mensajes) {
        this.inventarios = inventarios; this.entradas = entradas; this.eventos = eventos; this.mensajes = mensajes;
    }
    @Transactional
    public EntradaDtos.Cupos abrir(UUID eventoId, int capacidad, String sub, String token) {
        var evento = eventos.obtener(eventoId, token);
        propietario(evento, sub);
        futuro(evento);
        if (inventarios.existsById(eventoId))
            throw new ApiException(HttpStatus.CONFLICT, "Los cupos ya están definidos; no se pueden reiniciar.");
        // INSERT estricto: nunca merge/upsert, que podría reiniciar un inventario existente.
        Inventario inventario = new Inventario(eventoId, capacidad);
        inventarios.insertar(eventoId, capacidad);
        return inventario.respuesta();
    }
    @Transactional(readOnly=true)
    public EntradaDtos.Cupos cupos(UUID eventoId) {
        return inventarios.findById(eventoId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "El evento todavía no tiene cupos habilitados.")).respuesta();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public EntradaDtos.Respuesta emitir(UUID eventoId, String sub, String token) {
        var evento = eventos.obtener(eventoId, token);
        futuro(evento);
        // Bloqueo de una fila por evento, compartido entre todas las instancias del servicio.
        Inventario inventario = inventarios.bloquear(eventoId).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "El organizador todavía no habilita los cupos."));
        if (entradas.existsByEventoIdAndTitularSub(eventoId, sub))
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes una entrada para este concierto.");
        if (inventario.disponibles() <= 0)
            throw new ApiException(HttpStatus.CONFLICT, "No quedan cupos.");
        inventario.consumir();
        EntradaDtos.Respuesta entrada = entradas.saveAndFlush(new Entrada(eventoId, sub)).respuesta();
        // Fanout: tickets, notificaciones y auditoría reciben este mismo mensaje después del commit.
        mensajes.publishEvent(EventoMensaje.nuevo(TipoEvento.ENTRADA_EMITIDA, Map.of(
                "entradaId", entrada.id().toString(), "eventoId", eventoId.toString(), "titularSub", sub,
                "codigo", entrada.codigo().toString(), "emitidaEn", entrada.emitidaEn().toString())));
        return entrada;
    }
    @Transactional(readOnly=true)
    public List<EntradaDtos.Respuesta> mias(String sub, int page, int size) {
        return entradas.findByTitularSubOrderByEmitidaEnDesc(sub, PageRequest.of(page, size))
                .stream().map(Entrada::respuesta).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public EntradaDtos.Respuesta validar(UUID codigo, String sub, String token) {
        Entrada entrada = entradas.bloquearCodigo(codigo).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Entrada no encontrada."));
        propietario(eventos.obtener(entrada.getEventoId(), token), sub);
        if (entrada.getEstado() != Entrada.Estado.EMITIDA)
            throw new ApiException(HttpStatus.CONFLICT, "La entrada ya fue utilizada.");
        entrada.utilizar();
        EntradaDtos.Respuesta respuesta = entrada.respuesta();
        // Entrega selectiva: por filtro de suscripción solo la auditoría recibe este tipo.
        mensajes.publishEvent(EventoMensaje.nuevo(TipoEvento.ENTRADA_VALIDADA, Map.of(
                "entradaId", respuesta.id().toString(), "eventoId", respuesta.eventoId().toString(),
                "codigo", respuesta.codigo().toString(), "validadaPor", sub,
                "utilizadaEn", respuesta.utilizadaEn().toString())));
        return respuesta;
    }
    private void propietario(EventoClient.Evento evento, String sub) {
        if (!evento.organizadorSub().equals(sub))
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo el organizador de este concierto puede realizar la operación.");
    }
    private void futuro(EventoClient.Evento evento) {
        if (!evento.fecha().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.CONFLICT, "El concierto ya comenzó; no se emiten más entradas.");
    }
}
