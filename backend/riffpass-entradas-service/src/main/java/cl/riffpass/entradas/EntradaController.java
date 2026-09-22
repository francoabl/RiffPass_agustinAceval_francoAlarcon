package cl.riffpass.entradas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class EntradaController {
    private final EntradaService service;
    public EntradaController(EntradaService service) { this.service = service; }
    @PostMapping("/eventos/{id}/inventario")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/entradas-write') and hasAuthority('SCOPE_resource-server-riffpass/eventos-read')")
    public EntradaDtos.Cupos abrir(@PathVariable UUID id, @Valid @RequestBody EntradaDtos.AbrirCupos request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.abrir(id, request.capacidad(), jwt.getSubject(), jwt.getTokenValue());
    }
    @GetMapping("/eventos/{id}/inventario")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-riffpass/entradas-read')")
    public EntradaDtos.Cupos cupos(@PathVariable UUID id) { return service.cupos(id); }
    @PostMapping("/eventos/{id}/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ASISTENTE','ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/entradas-write') and hasAuthority('SCOPE_resource-server-riffpass/eventos-read')")
    public EntradaDtos.Respuesta emitir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.emitir(id, jwt.getSubject(), jwt.getTokenValue());
    }
    @GetMapping("/entradas/mias")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-riffpass/entradas-read')")
    public List<EntradaDtos.Respuesta> mias(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue="0") @Min(0) int page,
                                           @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.mias(jwt.getSubject(), page, size);
    }
    @PostMapping("/entradas/validar")
    @PreAuthorize("hasRole('ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/entradas-write') and hasAuthority('SCOPE_resource-server-riffpass/eventos-read')")
    public EntradaDtos.Respuesta validar(@Valid @RequestBody EntradaDtos.Validar request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.validar(request.codigo(), jwt.getSubject(), jwt.getTokenValue());
    }
}
