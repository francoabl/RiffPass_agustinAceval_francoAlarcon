package cl.duoc.pedidos360.pedidos.web;

import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.Pedido;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import cl.duoc.pedidos360.pedidos.service.PedidoService.ItemPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    public record ItemRequest(@NotNull Long productoId, @Min(1) int cantidad) {}
    public record CrearPedidoRequest(@NotEmpty List<@Valid ItemRequest> items) {}
    public record CambiarEstadoRequest(@NotNull EstadoPedido estado) {}

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    private static boolean esAdmin(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crear(JwtAuthenticationToken auth, @Valid @RequestBody CrearPedidoRequest req) {
        List<ItemPedido> items = req.items().stream()
                .map(i -> new ItemPedido(i.productoId(), i.cantidad())).toList();
        return service.crear(auth.getToken(), items);
    }

    /** ADMIN ve todos los pedidos; CLIENTE solo los suyos. */
    @GetMapping
    public List<Pedido> listar(JwtAuthenticationToken auth) {
        return service.listar(auth.getToken().getSubject(), esAdmin(auth));
    }

    @GetMapping("/{id}")
    public Pedido obtener(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.obtener(id, auth.getToken().getSubject(), esAdmin(auth));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public Pedido cambiarEstado(JwtAuthenticationToken auth, @PathVariable Long id,
                                @Valid @RequestBody CambiarEstadoRequest req) {
        return service.cambiarEstado(id, req.estado(), auth.getToken().getTokenValue());
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.cancelarPropio(id, auth.getToken().getSubject(), esAdmin(auth), auth.getToken().getTokenValue());
    }
}
