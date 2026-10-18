package cl.riffpass.admin.config;

import cl.riffpass.admin.service.ColaAdminService;
import cl.riffpass.mensajeria.*;
import org.slf4j.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/**
 * Declaración de la topología de mensajería (infraestructura como código).
 * Por cada {@link CasoUso} de riffpass-mensajeria.yml asegura:
 * <pre>
 *   tópico riffpass-eventos ──(suscripción, raw delivery, filtro por "tipo")──▶ cola ──(Redrive Policy)──▶ DLQ
 *                                                                                         └──▶ alarma CloudWatch
 * </pre>
 * Es idempotente: si los recursos existen, se ajustan sus atributos a lo declarado.
 */
@Configuration
@ConditionalOnProperty(name = "riffpass.admin.declarar-topologia", havingValue = "true", matchIfMissing = true)
public class TopologiaMensajeriaConfig {
    private static final Logger log = LoggerFactory.getLogger(TopologiaMensajeriaConfig.class);

    @Bean
    ApplicationRunner declararTopologia(ColaAdminService admin, MensajeriaProperties props) {
        return args -> {
            String topicArn = admin.crearTopico(props.topico()).arn();
            String alertasArn = props.alarmas().habilitadas() ? admin.asegurarTopicoAlertas() : null;
            for (CasoUso caso : CasoUso.values()) {
                MensajeriaProperties.Cola cola = props.cola(caso);
                admin.asegurarColaConDlq(cola);
                admin.asegurarSuscripcion(topicArn, cola);
                if (alertasArn != null) admin.asegurarAlarmaDlq(cola.dlq(), alertasArn);
                log.info("TOPOLOGIA {}: {} -> {} (filtro={}) -> {} (maxReceiveCount={})", caso, props.topico(),
                        cola.nombre(), cola.tipos().isEmpty() ? "ninguno, fanout" : cola.tipos(), cola.dlq(),
                        cola.maxRecepciones());
            }
        };
    }
}
