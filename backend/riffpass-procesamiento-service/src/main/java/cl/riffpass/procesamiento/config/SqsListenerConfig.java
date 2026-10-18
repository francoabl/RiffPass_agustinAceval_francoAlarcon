package cl.riffpass.procesamiento.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/** Comportamiento común de todos los consumidores; la lógica de negocio no conoce estos valores. */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.aws.sqs.enabled", matchIfMissing = true)
public class SqsListenerConfig {
    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqs) {
        return SqsMessageListenerContainerFactory.builder().sqsAsyncClient(sqs)
                .configure(options -> options
                        // El consumidor confirma explícitamente; sin ACK el mensaje vuelve a la cola.
                        .acknowledgementMode(AcknowledgementMode.MANUAL)
                        // Las colas las declara el administrador; no se crean colas sin DLQ por error.
                        .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL)
                        .maxConcurrentMessages(5)
                        .maxMessagesPerPoll(5)
                        // Long polling: menos solicitudes vacías y menor costo.
                        .pollTimeout(Duration.ofSeconds(20))
                        // Al detener una réplica termina los mensajes en curso antes de salir.
                        .listenerShutdownTimeout(Duration.ofSeconds(20)))
                .build();
    }
}
