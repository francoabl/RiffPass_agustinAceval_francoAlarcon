package cl.riffpass.procesamiento.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API interna que usa el microservicio administrador; no se publica en API Gateway. */
@RestController
@RequestMapping("/api/consumidores")
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('SCOPE_resource-server-riffpass/mensajeria-admin')")
public class ConsumidorController {
    public record CambioEstado(@NotNull ConsumidorControlService.Estado estado) {}
    private final ConsumidorControlService service;
    public ConsumidorController(ConsumidorControlService service) { this.service = service; }
    @GetMapping
    public List<ConsumidorControlService.EstadoConsumidor> listar() { return service.listar(); }
    @PutMapping("/{id}/estado")
    public ConsumidorControlService.EstadoConsumidor cambiar(@PathVariable String id, @Valid @RequestBody CambioEstado cambio) {
        return service.cambiar(id, cambio.estado());
    }
}
