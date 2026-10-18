package cl.riffpass.procesamiento.mensajeria;

/** El mensaje nunca podrá procesarse (formato o datos inválidos): va directo a la DLQ. */
public class ErrorNoRecuperable extends RuntimeException {
    public ErrorNoRecuperable(String mensaje) { super(mensaje); }
    public ErrorNoRecuperable(String mensaje, Throwable causa) { super(mensaje, causa); }
}
