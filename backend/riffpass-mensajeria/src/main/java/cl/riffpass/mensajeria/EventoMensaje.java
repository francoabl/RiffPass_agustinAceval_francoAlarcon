package cl.riffpass.mensajeria;

import java.time.Instant;
import java.util.*;

/**
 * Contrato JSON publicado en el tópico. {@code id} identifica el hecho de negocio y permite
 * descartar duplicados aunque SQS entregue el mismo mensaje más de una vez.
 * {@code simulacion} solo lo completa el administrador para demostrar fallos; los consumidores
 * lo ignoran salvo que {@code riffpass.mensajeria.simulacion-fallos} esté activo.
 */
public record EventoMensaje(UUID id, TipoEvento tipo, Instant ocurridoEn, String origen,
                            Map<String, String> datos, String simulacion) {
    public static EventoMensaje nuevo(TipoEvento tipo, Map<String, String> datos) {
        return new EventoMensaje(UUID.randomUUID(), tipo, Instant.now(), null, Map.copyOf(datos), null);
    }
    public EventoMensaje conOrigen(String origen) {
        return new EventoMensaje(id, tipo, ocurridoEn, origen, datos, simulacion);
    }
    public EventoMensaje conSimulacion(String simulacion) {
        return new EventoMensaje(id, tipo, ocurridoEn, origen, datos, simulacion);
    }
    /** Dato obligatorio para el consumidor; si falta, el mensaje no se podrá procesar nunca. */
    public String dato(String nombre) {
        String valor = datos == null ? null : datos.get(nombre);
        if (valor == null || valor.isBlank())
            throw new IllegalArgumentException("Falta el dato " + nombre + " en " + tipo);
        return valor;
    }
}
