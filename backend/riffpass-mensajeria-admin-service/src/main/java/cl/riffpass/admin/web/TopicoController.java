package cl.riffpass.admin.web;

import cl.riffpass.admin.service.ColaAdminService;
import cl.riffpass.admin.web.AdminDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/topicos")
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('SCOPE_resource-server-riffpass/mensajeria-admin')")
public class TopicoController {
    private final ColaAdminService admin;
    public TopicoController(ColaAdminService admin) { this.admin = admin; }

    @GetMapping
    public List<Topico> listar() { return admin.listarTopicos(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Topico crear(@Valid @RequestBody NuevoTopico nuevo) { return admin.crearTopico(nuevo.nombre()); }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String nombre, @RequestParam(defaultValue = "false") boolean forzar) {
        admin.eliminarTopico(nombre, forzar);
    }

    @PostMapping("/{nombre}/mensajes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Publicado publicar(@PathVariable String nombre, @Valid @RequestBody PublicacionPrueba prueba) {
        return admin.publicarPrueba(nombre, prueba);
    }

    @GetMapping("/{nombre}/suscripciones")
    public List<Suscripcion> suscripciones(@PathVariable String nombre) { return admin.listarSuscripciones(nombre); }

    @PostMapping("/{nombre}/suscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Suscripcion suscribir(@PathVariable String nombre, @Valid @RequestBody NuevaSuscripcion nueva) {
        return admin.suscribirCola(nombre, nueva.cola(), nueva.tipos());
    }

    @PutMapping("/{nombre}/suscripciones/{id}/filtro")
    public Suscripcion cambiarFiltro(@PathVariable String nombre, @PathVariable String id, @Valid @RequestBody Filtro filtro) {
        return admin.cambiarFiltro(nombre, id, filtro.tipos());
    }

    @DeleteMapping("/{nombre}/suscripciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarSuscripcion(@PathVariable String nombre, @PathVariable String id) {
        admin.eliminarSuscripcion(nombre, id);
    }
}
