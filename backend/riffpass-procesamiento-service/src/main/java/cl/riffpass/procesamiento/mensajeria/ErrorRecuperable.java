package cl.riffpass.procesamiento.mensajeria;

/** Fallo transitorio (dependencia caída, timeout): el mensaje se reintenta. */
public class ErrorRecuperable extends RuntimeException {
    public ErrorRecuperable(String mensaje) { super(mensaje); }
}
