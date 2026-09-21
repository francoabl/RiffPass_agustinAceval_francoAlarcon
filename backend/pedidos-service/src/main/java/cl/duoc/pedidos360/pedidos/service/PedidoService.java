package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.exception.PedidoInvalidoException;
import cl.duoc.pedidos360.pedidos.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.pedidos.model.DetallePedido;
import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.Pedido;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PedidoService {

    public record ItemPedido(Long productoId, int cantidad) {}

    private final PedidoRepository repository;
    private final ProductoClient productoClient;

    public PedidoService(PedidoRepository repository, ProductoClient productoClient) {
        this.repository = repository;
        this.productoClient = productoClient;
    }

    /**
     * Regla: el total lo calcula el servidor con el precio vigente de productos-service;
     * el cliente nunca envia precios.
     */
    @Transactional
    public Pedido crear(Jwt jwt, List<ItemPedido> items) {
        if (items == null || items.isEmpty()) {
            throw new PedidoInvalidoException("El pedido debe tener al menos un producto");
        }
        Pedido pedido = new Pedido();
        pedido.setClienteSub(jwt.getSubject());
        pedido.setClienteEmail(jwt.getClaimAsString("email"));

        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido item : items) {
            if (item.cantidad() < 1) {
                throw new PedidoInvalidoException("La cantidad debe ser al menos 1");
            }
            ProductoClient.ProductoInfo info =
                    productoClient.reservar(item.productoId(), item.cantidad(), jwt.getTokenValue());
            DetallePedido d = new DetallePedido();
            d.setProductoId(info.id());
            d.setNombreProducto(info.nombre());
            d.setCantidad(item.cantidad());
            d.setPrecioUnitario(info.precio());
            pedido.agregarDetalle(d);
            total = total.add(info.precio().multiply(BigDecimal.valueOf(item.cantidad())));
        }
        pedido.setTotal(total);
        return repository.save(pedido);
    }

    public List<Pedido> listar(String sub, boolean esAdmin) {
        return esAdmin ? repository.findAll() : repository.findByClienteSubOrderByFechaDesc(sub);
    }

    /** Un cliente solo puede ver sus propios pedidos; un ADMIN puede ver todos. */
    public Pedido obtener(Long id, String sub, boolean esAdmin) {
        Pedido p = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no existe"));
        if (!esAdmin && !p.getClienteSub().equals(sub)) {
            throw new AccessDeniedException("El pedido pertenece a otro usuario");
        }
        return p;
    }

    /** Transiciones validas: CREADO->CONFIRMADO->ENVIADO. Cancelar solo desde CREADO o CONFIRMADO. */
    @Transactional
    public Pedido cambiarEstado(Long id, EstadoPedido nuevo, String bearerToken) {
        Pedido p = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no existe"));
        if (!transicionValida(p.getEstado(), nuevo)) {
            throw new PedidoInvalidoException("Transicion invalida: " + p.getEstado() + " -> " + nuevo);
        }
        if (nuevo == EstadoPedido.CANCELADO) {
            // Regla: al cancelar, las unidades vuelven al stock
            p.getDetalles().forEach(d -> productoClient.liberar(d.getProductoId(), d.getCantidad(), bearerToken));
        }
        p.setEstado(nuevo);
        return repository.save(p);
    }

    /** El cliente puede cancelar su propio pedido solo mientras esta CREADO. */
    @Transactional
    public Pedido cancelarPropio(Long id, String sub, boolean esAdmin, String bearerToken) {
        Pedido p = obtener(id, sub, esAdmin);
        if (!esAdmin && p.getEstado() != EstadoPedido.CREADO) {
            throw new PedidoInvalidoException("Solo puedes cancelar pedidos en estado CREADO");
        }
        return cambiarEstado(id, EstadoPedido.CANCELADO, bearerToken);
    }

    static boolean transicionValida(EstadoPedido actual, EstadoPedido nuevo) {
        return switch (actual) {
            case CREADO -> nuevo == EstadoPedido.CONFIRMADO || nuevo == EstadoPedido.CANCELADO;
            case CONFIRMADO -> nuevo == EstadoPedido.ENVIADO || nuevo == EstadoPedido.CANCELADO;
            case ENVIADO, CANCELADO -> false;
        };
    }
}
