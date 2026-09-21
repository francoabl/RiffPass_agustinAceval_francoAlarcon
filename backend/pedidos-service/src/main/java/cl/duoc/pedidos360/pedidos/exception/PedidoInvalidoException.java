package cl.duoc.pedidos360.pedidos.exception;

/** Regla de negocio violada (stock insuficiente, transicion de estado invalida, etc.). */
public class PedidoInvalidoException extends RuntimeException {
    public PedidoInvalidoException(String message) { super(message); }
}
