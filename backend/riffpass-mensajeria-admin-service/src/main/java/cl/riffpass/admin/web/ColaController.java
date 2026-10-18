package cl.riffpass.admin.web;

import cl.riffpass.admin.service.ColaAdminService;
import cl.riffpass.admin.web.AdminDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/colas")
@Validated
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('SCOPE_resource-server-riffpass/mensajeria-admin')")
public class ColaController {
    private final ColaAdminService admin;
    public ColaController(ColaAdminService admin) { this.admin = admin; }

    @GetMapping
    public List<EstadoCola> listar() { return admin.listarColas(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstadoCola crear(@Valid @RequestBody NuevaCola nueva) { return admin.crearCola(nueva); }

    @GetMapping("/{nombre}")
    public EstadoCola estado(@PathVariable String nombre) { return admin.estado(nombre); }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String nombre, @RequestParam(defaultValue = "false") boolean forzar) {
        admin.eliminarCola(nombre, forzar);
    }

    @GetMapping("/{nombre}/dlq/mensajes")
    public List<MensajeDlq> verDlq(@PathVariable String nombre,
                                   @RequestParam(defaultValue = "10") @Min(1) @Max(10) int max) {
        return admin.verDlq(nombre, max);
    }

    @PostMapping("/{nombre}/dlq/reprocesos")
    public Reproceso reprocesar(@PathVariable String nombre,
                                @RequestParam(defaultValue = "10") @Min(1) @Max(100) int max) {
        return admin.reprocesarDlq(nombre, max);
    }

    @DeleteMapping("/{nombre}/dlq/mensajes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Purga purgar(@PathVariable String nombre) { return admin.purgarDlq(nombre); }
}
