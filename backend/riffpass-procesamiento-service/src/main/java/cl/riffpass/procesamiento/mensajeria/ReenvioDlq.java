package cl.riffpass.procesamiento.mensajeria;

import cl.riffpass.mensajeria.AtributosMensaje;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/** Envía a la DLQ los mensajes no recuperables, conservando el cuerpo original y la causa del fallo. */
@Component
public class ReenvioDlq {
    private final SqsAsyncClient sqs;
    private final Map<String, String> urls = new ConcurrentHashMap<>();
    public ReenvioDlq(SqsAsyncClient sqs) { this.sqs = sqs; }

    /** @return MessageId asignado por SQS en la DLQ */
    public String enviar(String dlq, String cuerpo, Map<String, String> atributos) {
        String url = urls.computeIfAbsent(dlq, nombre -> sqs.getQueueUrl(r -> r.queueName(nombre)).join().queueUrl());
        Map<String, MessageAttributeValue> sqsAtributos = new HashMap<>();
        atributos.forEach((clave, valor) -> {
            if (valor != null) sqsAtributos.put(clave, MessageAttributeValue.builder().dataType("String")
                    .stringValue(valor.length() > 250 ? valor.substring(0, 250) : valor).build());
        });
        return sqs.sendMessage(r -> r.queueUrl(url).messageBody(cuerpo).messageAttributes(sqsAtributos))
                .join().messageId();
    }

    public static Map<String, String> atributos(String tipo, String causa, int intento, String colaOrigen, String sqsId) {
        Map<String, String> valores = new HashMap<>();
        valores.put(AtributosMensaje.TIPO, tipo);
        valores.put(AtributosMensaje.CAUSA, causa);
        valores.put(AtributosMensaje.INTENTO, Integer.toString(intento));
        valores.put(AtributosMensaje.COLA_ORIGEN, colaOrigen);
        valores.put(AtributosMensaje.MENSAJE_ORIGINAL, sqsId);
        return valores;
    }
}
