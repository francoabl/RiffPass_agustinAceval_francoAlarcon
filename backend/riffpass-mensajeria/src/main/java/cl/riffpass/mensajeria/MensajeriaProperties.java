package cl.riffpass.mensajeria;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Único punto donde se leen los nombres del tópico, colas, DLQ, suscripciones y consumidores.
 * Los valores viven en {@code riffpass-mensajeria.yml}, compartido por todos los microservicios.
 */
@ConfigurationProperties("riffpass.mensajeria")
public record MensajeriaProperties(boolean habilitada, String topico, boolean simulacionFallos,
                                   Map<CasoUso, Cola> colas, Alarmas alarmas) {
    public MensajeriaProperties {
        colas = colas == null ? Map.of() : Map.copyOf(colas);
        for (CasoUso caso : CasoUso.values())
            if (!colas.containsKey(caso)) throw new IllegalStateException("Falta la configuración de la cola " + caso);
    }
    public Cola cola(CasoUso caso) { return colas.get(caso); }
    /** Busca la ruta a la que pertenece una cola principal o su DLQ. */
    public Optional<CasoUso> casoDe(String nombreCola) {
        return colas.entrySet().stream()
                .filter(e -> e.getValue().nombre().equals(nombreCola) || e.getValue().dlq().equals(nombreCola))
                .map(Map.Entry::getKey).findFirst();
    }

    /**
     * Una ruta de mensajería: suscripción tópico → cola, cola → DLQ y consumidor.
     * @param tipos filtro de la suscripción; vacío significa recibir todos los tipos
     * @param maxRecepciones maxReceiveCount de la Redrive Policy
     */
    public record Cola(String nombre, String dlq, String consumidor, String descripcion, List<TipoEvento> tipos,
                       int maxRecepciones, Duration visibilidad, Duration retencion, Duration retencionDlq,
                       Duration esperaLargoSondeo) {
        public Cola {
            tipos = tipos == null ? List.of() : List.copyOf(tipos);
        }
        public boolean acepta(TipoEvento tipo) { return tipos.isEmpty() || tipos.contains(tipo); }
    }

    public record Alarmas(boolean habilitadas, String prefijo, String topicoAlertas, String email) {}
}
