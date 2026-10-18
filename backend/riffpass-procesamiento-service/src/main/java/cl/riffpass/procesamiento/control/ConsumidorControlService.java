package cl.riffpass.procesamiento.control;

import cl.riffpass.common.ApiException;
import io.awspring.cloud.sqs.listener.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Pausa y reanuda los listener containers de esta réplica sin detener el proceso. */
@Service
public class ConsumidorControlService {
    private static final Logger log = LoggerFactory.getLogger(ConsumidorControlService.class);
    public enum Estado { ACTIVO, PAUSADO }
    public record EstadoConsumidor(String id, Estado estado, String instancia) {}

    private final ObjectProvider<MessageListenerContainerRegistry> registry;
    private final String instancia;
    public ConsumidorControlService(ObjectProvider<MessageListenerContainerRegistry> registry,
                                    @Value("${riffpass.instancia}") String instancia) {
        this.registry = registry; this.instancia = instancia;
    }

    public List<EstadoConsumidor> listar() {
        MessageListenerContainerRegistry contenedores = registry.getIfAvailable();
        if (contenedores == null) return List.of();
        return contenedores.getListenerContainers().stream().map(this::estado)
                .sorted(Comparator.comparing(EstadoConsumidor::id)).toList();
    }

    public EstadoConsumidor cambiar(String id, Estado estado) {
        MessageListenerContainerRegistry contenedores = registry.getIfAvailable();
        MessageListenerContainer<?> contenedor = contenedores == null ? null : contenedores.getContainerById(id);
        if (contenedor == null) throw new ApiException(HttpStatus.NOT_FOUND, "Consumidor no encontrado: " + id);
        // stop() deja de sondear y espera los mensajes en curso; los pendientes quedan seguros en la cola.
        if (estado == Estado.PAUSADO && contenedor.isRunning()) contenedor.stop();
        if (estado == Estado.ACTIVO && !contenedor.isRunning()) contenedor.start();
        log.info("CONSUMIDOR {} instancia={} estado={}", id, instancia, estado);
        return estado(contenedor);
    }

    private EstadoConsumidor estado(MessageListenerContainer<?> contenedor) {
        return new EstadoConsumidor(contenedor.getId(), contenedor.isRunning() ? Estado.ACTIVO : Estado.PAUSADO, instancia);
    }
}
