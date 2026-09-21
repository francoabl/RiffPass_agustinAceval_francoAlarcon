package cl.duoc.pedidos360.pedidos.service;

import java.math.BigDecimal;

/** Cliente hacia productos-service (comunicacion entre microservicios). */
public interface ProductoClient {

    record ProductoInfo(Long id, String nombre, BigDecimal precio, int stock) {}

    /**
     * Reserva (descuenta) stock y devuelve el precio vigente.
     * Reenvia el JWT del usuario para que productos-service tambien valide el token.
     */
    ProductoInfo reservar(Long productoId, int cantidad, String bearerToken);

    /** Devuelve unidades al stock (cancelacion de pedido). */
    void liberar(Long productoId, int cantidad, String bearerToken);
}
