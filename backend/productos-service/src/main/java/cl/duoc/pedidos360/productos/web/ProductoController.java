package cl.duoc.pedidos360.productos.web;

import cl.duoc.pedidos360.productos.model.Producto;
import cl.duoc.pedidos360.productos.service.ProductoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    public record ReservaRequest(@Min(1) int cantidad) {}

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    /** Cualquier usuario autenticado puede ver el catalogo. */
    @GetMapping
    public List<Producto> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public Producto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Producto crear(@Valid @RequestBody Producto producto) {
        return service.crear(producto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody Producto producto) {
        return service.actualizar(id, producto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Long id) {
        service.desactivar(id);
    }

    /** Usado por pedidos-service al cancelar un pedido: devuelve las unidades al stock. */
    @PostMapping("/{id}/liberar")
    public Producto liberar(@PathVariable Long id, @Valid @RequestBody ReservaRequest req) {
        return service.liberar(id, req.cantidad());
    }

    /** Usado por pedidos-service al crear un pedido: descuenta stock y devuelve precio vigente. */
    @PostMapping("/{id}/reservar")
    public Producto reservar(@PathVariable Long id, @Valid @RequestBody ReservaRequest req) {
        return service.reservar(id, req.cantidad());
    }
}
