package cl.duoc.pedidos360.pedidos;

import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TransicionEstadoTest {

    private boolean valida(EstadoPedido a, EstadoPedido b) throws Exception {
        Method m = PedidoService.class.getDeclaredMethod("transicionValida", EstadoPedido.class, EstadoPedido.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, a, b);
    }

    @Test
    void flujoNormal() throws Exception {
        assertTrue(valida(EstadoPedido.CREADO, EstadoPedido.CONFIRMADO));
        assertTrue(valida(EstadoPedido.CONFIRMADO, EstadoPedido.ENVIADO));
    }

    @Test
    void noSePuedeSaltarNiReabrir() throws Exception {
        assertFalse(valida(EstadoPedido.CREADO, EstadoPedido.ENVIADO));
        assertFalse(valida(EstadoPedido.CANCELADO, EstadoPedido.CREADO));
        assertFalse(valida(EstadoPedido.ENVIADO, EstadoPedido.CANCELADO));
    }
}
