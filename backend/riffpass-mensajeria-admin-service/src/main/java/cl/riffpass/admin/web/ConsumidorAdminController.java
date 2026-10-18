package cl.riffpass.admin.web;

import cl.riffpass.admin.service.ColaAdminService;
import cl.riffpass.admin.web.AdminDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/consumidores")
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('SCOPE_resource-server-riffpass/mensajeria-admin')")
public class ConsumidorAdminController {
    private final ColaAdminService admin;
    public ConsumidorAdminController(ColaAdminService admin) { this.admin = admin; }

    @GetMapping
    public List<Consumidor> listar(@AuthenticationPrincipal Jwt jwt) { return admin.consumidores(jwt.getTokenValue()); }

    /** Pausa ({@code PAUSADO}) o reanuda ({@code ACTIVO}) el consumidor en todas sus réplicas. */
    @PutMapping("/{id}/estado")
    public List<Consumidor> cambiar(@PathVariable String id, @Valid @RequestBody CambioEstado cambio,
                                    @AuthenticationPrincipal Jwt jwt) {
        return admin.cambiarConsumidor(id, cambio.estado(), jwt.getTokenValue());
    }
}
