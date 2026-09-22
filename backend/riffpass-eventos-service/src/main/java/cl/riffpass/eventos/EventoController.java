package cl.riffpass.eventos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eventos")
@Validated
public class EventoController {
    private final EventoService service;
    public EventoController(EventoService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-riffpass/eventos-read')")
    public List<EventoDtos.Respuesta> listar(@RequestParam(defaultValue="0") @Min(0) int page,
                                           @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.listar(page, size);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-riffpass/eventos-read')")
    public EventoDtos.Respuesta obtener(@PathVariable UUID id) { return service.obtener(id); }
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/eventos-write')")
    public ResponseEntity<EventoDtos.Respuesta> crear(@Valid @RequestBody EventoDtos.Crear request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var response = service.crear(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("eventos/" + response.id())).body(response);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/eventos-write')")
    public EventoDtos.Respuesta editar(@PathVariable UUID id, @Valid @RequestBody EventoDtos.Editar request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.editar(id, request, jwt.getSubject());
    }
}
