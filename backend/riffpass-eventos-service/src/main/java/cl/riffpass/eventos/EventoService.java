package cl.riffpass.eventos;

import cl.riffpass.common.ApiException;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventoService {
    private final EventoRepository repository;
    public EventoService(EventoRepository repository) { this.repository = repository; }
    @Transactional(readOnly=true)
    public List<EventoDtos.Respuesta> listar(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by("fecha").ascending().and(Sort.by("id"))))
                .map(Evento::respuesta).getContent();
    }
    @Transactional(readOnly=true)
    public EventoDtos.Respuesta obtener(UUID id) { return buscar(id).respuesta(); }
    @Transactional
    public EventoDtos.Respuesta crear(EventoDtos.Crear request, String sub) {
        return repository.save(new Evento(request, sub)).respuesta();
    }
    @Transactional
    public EventoDtos.Respuesta editar(UUID id, EventoDtos.Editar request, String sub) {
        Evento evento = buscar(id);
        if (!evento.getOrganizadorSub().equals(sub))
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo puedes modificar tus propios eventos.");
        evento.editar(request);
        return evento.respuesta();
    }
    private Evento buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Evento no encontrado."));
    }
}
