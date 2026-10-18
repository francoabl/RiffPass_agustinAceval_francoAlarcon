package cl.riffpass.admin.service;

import cl.riffpass.admin.web.AdminDtos.*;
import cl.riffpass.common.ApiException;
import cl.riffpass.mensajeria.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/**
 * Toda la operación de SNS, SQS y CloudWatch del sistema. Los controladores y la declaración de
 * topología solo invocan estos métodos de alto nivel.
 */
@Service
public class ColaAdminService {
    private static final Logger log = LoggerFactory.getLogger(ColaAdminService.class);
    private static final int MAX_REPROCESO = 100;
    private final SqsClient sqs;
    private final SnsClient sns;
    private final CloudWatchClient cloudWatch;
    private final MensajeriaProperties props;
    private final ConsumidoresClient consumidores;
    private final ObjectMapper mapper;

    public ColaAdminService(SqsClient sqs, SnsClient sns, CloudWatchClient cloudWatch, MensajeriaProperties props,
                            ConsumidoresClient consumidores, ObjectMapper mapper) {
        this.sqs = sqs; this.sns = sns; this.cloudWatch = cloudWatch; this.props = props;
        this.consumidores = consumidores; this.mapper = mapper;
    }

    // ---------------------------------------------------------------- Tópicos

    public List<Topico> listarTopicos() {
        return sns.listTopicsPaginator(r -> {}).topics().stream().map(Topic::topicArn)
                .map(arn -> new Topico(ultimo(arn), arn)).sorted(Comparator.comparing(Topico::nombre)).toList();
    }

    public Topico crearTopico(String nombre) {
        String arn = sns.createTopic(r -> r.name(nombre)).topicArn();
        log.info("ADMIN topico asegurado {}", arn);
        return new Topico(nombre, arn);
    }

    public void eliminarTopico(String nombre, boolean forzar) {
        protegido(nombre, forzar);
        String arn = arnTopico(nombre);
        sns.deleteTopic(r -> r.topicArn(arn));
        log.warn("ADMIN topico eliminado {}", arn);
    }

    /** Publica un mensaje de prueba con el atributo {@code tipo}, para demostrar fanout y filtros. */
    public Publicado publicarPrueba(String topico, PublicacionPrueba prueba) {
        EventoMensaje evento = EventoMensaje.nuevo(prueba.tipo(), prueba.datos())
                .conOrigen("riffpass-mensajeria-admin-service").conSimulacion(prueba.simulacion());
        String arn = arnTopico(topico);
        String id = sns.publish(r -> r.topicArn(arn).message(json(evento)).messageAttributes(Map.of(
                AtributosMensaje.TIPO, atributoSns(evento.tipo().name()),
                AtributosMensaje.ORIGEN, atributoSns(evento.origen())))).messageId();
        log.info("ADMIN publicado snsId={} eventoId={} tipo={} simulacion={}", id, evento.id(), evento.tipo(),
                evento.simulacion());
        return new Publicado(id, evento.id());
    }

    // ---------------------------------------------------------------- Suscripciones

    public List<Suscripcion> listarSuscripciones(String topico) {
        String arn = arnTopico(topico);
        return sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(arn)).subscriptions().stream()
                .map(s -> suscripcion(s.subscriptionArn(), s.protocol(), s.endpoint())).toList();
    }

    /** Suscribe una cola al tópico con entrega raw y, si se indican tipos, un filtro por atributo. */
    public Suscripcion suscribirCola(String topico, String cola, List<TipoEvento> tipos) {
        String topicArn = arnTopico(topico);
        String colaArn = permitirTopico(url(cola), topicArn);
        Map<String, String> atributos = new HashMap<>();
        atributos.put("RawMessageDelivery", "true");
        if (tipos != null && !tipos.isEmpty()) {
            atributos.put("FilterPolicyScope", "MessageAttributes");
            atributos.put("FilterPolicy", filtro(tipos));
        }
        String arn = sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(colaArn)
                .attributes(atributos).returnSubscriptionArn(true)).subscriptionArn();
        log.info("ADMIN suscripcion {} -> {} filtro={}", topico, cola, tipos);
        return suscripcion(arn, "sqs", colaArn);
    }

    public Suscripcion cambiarFiltro(String topico, String id, List<TipoEvento> tipos) {
        String arn = arnTopico(topico) + ":" + id;
        existeSuscripcion(topico, arn);
        sns.setSubscriptionAttributes(r -> r.subscriptionArn(arn).attributeName("FilterPolicyScope")
                .attributeValue("MessageAttributes"));
        sns.setSubscriptionAttributes(r -> r.subscriptionArn(arn).attributeName("FilterPolicy").attributeValue(filtro(tipos)));
        log.info("ADMIN filtro {} = {}", arn, tipos);
        return suscripcion(arn, "sqs", null);
    }

    public void eliminarSuscripcion(String topico, String id) {
        String arn = arnTopico(topico) + ":" + id;
        existeSuscripcion(topico, arn);
        sns.unsubscribe(r -> r.subscriptionArn(arn));
        log.warn("ADMIN suscripcion eliminada {}", arn);
    }

    // ---------------------------------------------------------------- Colas

    public List<EstadoCola> listarColas() {
        return sqs.listQueuesPaginator(r -> r.queueNamePrefix("riffpass")).queueUrls().stream()
                .map(url -> estado(ultimo(url))).sorted(Comparator.comparing(EstadoCola::nombre)).toList();
    }

    /** Crea una cola nueva. Con DLQ, crea primero la DLQ y conecta ambas con una Redrive Policy. */
    public EstadoCola crearCola(NuevaCola nueva) {
        if (existe(nueva.nombre())) throw new ApiException(HttpStatus.CONFLICT, "La cola ya existe: " + nueva.nombre());
        int maxRecepciones = Optional.ofNullable(nueva.maxRecepciones()).orElse(3);
        int visibilidad = Optional.ofNullable(nueva.visibilidadSeg()).orElse(30);
        int retencion = Optional.ofNullable(nueva.retencionSeg()).orElse(345_600);
        int retencionDlq = Optional.ofNullable(nueva.retencionDlqSeg()).orElse(1_209_600);
        String dlq = Boolean.TRUE.equals(nueva.conDlq()) ? nueva.nombre() + "-dlq" : null;
        asegurarCola(nueva.nombre(), dlq, maxRecepciones, visibilidad, retencion, retencionDlq, 20);
        return estado(nueva.nombre());
    }

    public void eliminarCola(String nombre, boolean forzar) {
        protegido(nombre, forzar);
        String url = url(nombre);
        sqs.deleteQueue(r -> r.queueUrl(url));
        log.warn("ADMIN cola eliminada {}", nombre);
    }

    /** Estado operativo: contadores de SQS, antigüedad desde CloudWatch y la DLQ asociada. */
    public EstadoCola estado(String nombre) {
        String url = url(nombre);
        Map<String, String> a = sqs.getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.ALL))
                .attributesAsStrings();
        String dlq = null; Integer maxRecepciones = null; Long enDlq = null;
        if (a.containsKey("RedrivePolicy")) {
            Map<?, ?> redrive = leer(a.get("RedrivePolicy"));
            dlq = ultimo(String.valueOf(redrive.get("deadLetterTargetArn")));
            maxRecepciones = Integer.valueOf(String.valueOf(redrive.get("maxReceiveCount")));
            String dlqUrl = urlSiExiste(dlq);
            if (dlqUrl != null) enDlq = Long.valueOf(sqs.getQueueAttributes(r -> r.queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).attributesAsStrings()
                    .get("ApproximateNumberOfMessages"));
        }
        return new EstadoCola(nombre, url, a.get("QueueArn"), largo(a, "ApproximateNumberOfMessages"),
                largo(a, "ApproximateNumberOfMessagesNotVisible"), largo(a, "ApproximateNumberOfMessagesDelayed"),
                antiguedad(nombre), (int) largo(a, "VisibilityTimeout"), (int) largo(a, "MessageRetentionPeriod"),
                (int) largo(a, "ReceiveMessageWaitTimeSeconds"), dlq, maxRecepciones, enDlq, a);
    }

    // ---------------------------------------------------------------- DLQ

    /** Lee sin consumir: VisibilityTimeout 0 deja los mensajes disponibles en la DLQ. */
    public List<MensajeDlq> verDlq(String cola, int maximo) {
        String dlqUrl = url(dlqDe(cola));
        return sqs.receiveMessage(r -> r.queueUrl(dlqUrl).maxNumberOfMessages(Math.min(Math.max(maximo, 1), 10))
                        .visibilityTimeout(0).waitTimeSeconds(1).messageAttributeNames("All")
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)).messages().stream()
                .map(m -> new MensajeDlq(m.messageId(), m.body(), texto(m.messageAttributes()), m.attributesAsStrings()))
                .toList();
    }

    /**
     * Devuelve los mensajes de la DLQ a su cola original conservando cuerpo y atributos,
     * incrementa {@code reprocesos} y solo entonces los elimina de la DLQ.
     */
    public Reproceso reprocesarDlq(String cola, int maximo) {
        String dlq = dlqDe(cola);
        String dlqUrl = url(dlq);
        String destinoUrl = url(cola);
        int limite = Math.min(Math.max(maximo, 1), MAX_REPROCESO);
        List<String> ids = new ArrayList<>();
        while (ids.size() < limite) {
            List<Message> lote = sqs.receiveMessage(r -> r.queueUrl(dlqUrl).maxNumberOfMessages(Math.min(10, limite - ids.size()))
                    .visibilityTimeout(60).waitTimeSeconds(1).messageAttributeNames("All")).messages();
            if (lote.isEmpty()) break;
            for (Message m : lote) {
                Map<String, MessageAttributeValue> atributos = new HashMap<>(m.messageAttributes());
                int reprocesos = Integer.parseInt(Optional.ofNullable(atributos.get(AtributosMensaje.REPROCESOS))
                        .map(MessageAttributeValue::stringValue).orElse("0")) + 1;
                atributos.put(AtributosMensaje.REPROCESOS, MessageAttributeValue.builder().dataType("Number")
                        .stringValue(Integer.toString(reprocesos)).build());
                String nuevoId = sqs.sendMessage(r -> r.queueUrl(destinoUrl).messageBody(m.body())
                        .messageAttributes(atributos)).messageId();
                sqs.deleteMessage(r -> r.queueUrl(dlqUrl).receiptHandle(m.receiptHandle()));
                log.info("ADMIN REPROCESADO dlq={} sqsId={} -> cola={} nuevoSqsId={} reprocesos={}", dlq,
                        m.messageId(), cola, nuevoId, reprocesos);
                ids.add(m.messageId());
            }
        }
        return new Reproceso(dlq, cola, ids.size(), ids);
    }

    public Purga purgarDlq(String cola) {
        String dlq = dlqDe(cola);
        String dlqUrl = url(dlq);
        try {
            sqs.purgeQueue(r -> r.queueUrl(dlqUrl));
        } catch (PurgeQueueInProgressException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SQS permite una purga por minuto; espera e inténtalo de nuevo.");
        }
        log.warn("ADMIN DLQ purgada {}", dlq);
        return new Purga(dlq, "Purga solicitada; SQS elimina los mensajes en hasta 60 segundos.");
    }

    // ---------------------------------------------------------------- Consumidores

    public List<Consumidor> consumidores(String token) { return consumidores.listar(token); }

    public List<Consumidor> cambiarConsumidor(String id, EstadoConsumidor estado, String token) {
        if (props.colas().values().stream().noneMatch(c -> c.consumidor().equals(id)))
            throw new ApiException(HttpStatus.NOT_FOUND, "Consumidor no declarado: " + id);
        return consumidores.cambiar(id, estado, token);
    }

    // ---------------------------------------------------------------- Topología declarada

    public void asegurarColaConDlq(MensajeriaProperties.Cola cola) {
        asegurarCola(cola.nombre(), cola.dlq(), cola.maxRecepciones(), (int) cola.visibilidad().toSeconds(),
                (int) cola.retencion().toSeconds(), (int) cola.retencionDlq().toSeconds(),
                (int) cola.esperaLargoSondeo().toSeconds());
    }

    /** Crea la suscripción si falta o ajusta raw delivery y filtro si ya existe. */
    public void asegurarSuscripcion(String topicArn, MensajeriaProperties.Cola cola) {
        String colaArn = permitirTopico(url(cola.nombre()), topicArn);
        Optional<String> existente = sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicArn)).subscriptions()
                .stream().filter(s -> colaArn.equals(s.endpoint())).map(s -> s.subscriptionArn()).findFirst();
        if (existente.isEmpty()) {
            suscribirCola(ultimo(topicArn), cola.nombre(), cola.tipos());
            return;
        }
        String arn = existente.get();
        sns.setSubscriptionAttributes(r -> r.subscriptionArn(arn).attributeName("RawMessageDelivery").attributeValue("true"));
        if (!cola.tipos().isEmpty()) {
            sns.setSubscriptionAttributes(r -> r.subscriptionArn(arn).attributeName("FilterPolicyScope")
                    .attributeValue("MessageAttributes"));
            sns.setSubscriptionAttributes(r -> r.subscriptionArn(arn).attributeName("FilterPolicy")
                    .attributeValue(filtro(cola.tipos())));
        } else if (sns.getSubscriptionAttributes(r -> r.subscriptionArn(arn)).attributes().containsKey("FilterPolicy")) {
            // Un filtro sobrante rompería el fanout declarado: se recrea la suscripción sin filtro.
            sns.unsubscribe(r -> r.subscriptionArn(arn));
            suscribirCola(ultimo(topicArn), cola.nombre(), List.of());
        }
    }

    public String asegurarTopicoAlertas() {
        MensajeriaProperties.Alarmas alarmas = props.alarmas();
        String arn = crearTopico(alarmas.topicoAlertas()).arn();
        String email = alarmas.email();
        if (email != null && !email.isBlank() && sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(arn))
                .subscriptions().stream().noneMatch(s -> email.equalsIgnoreCase(s.endpoint()))) {
            sns.subscribe(r -> r.topicArn(arn).protocol("email").endpoint(email));
            log.info("ADMIN suscripcion de correo pendiente de confirmación en {}", alarmas.topicoAlertas());
        }
        return arn;
    }

    /** Alarma cuando la DLQ tiene al menos un mensaje visible: hay mensajes que el sistema no pudo entregar. */
    public void asegurarAlarmaDlq(String dlq, String alertasArn) {
        String nombre = props.alarmas().prefijo() + "-" + dlq;
        cloudWatch.putMetricAlarm(r -> r.alarmName(nombre)
                .alarmDescription("Mensajes no entregados en " + dlq + ". Revisar logs NO ENTREGADO y reprocesar o purgar.")
                .namespace("AWS/SQS").metricName("ApproximateNumberOfMessagesVisible")
                .dimensions(Dimension.builder().name("QueueName").value(dlq).build())
                .statistic(Statistic.MAXIMUM).period(60).evaluationPeriods(1).threshold(1.0)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData("notBreaching").alarmActions(alertasArn));
        log.info("ADMIN alarma asegurada {}", nombre);
    }

    // ---------------------------------------------------------------- Apoyo

    private void asegurarCola(String nombre, String dlq, int maxRecepciones, int visibilidad, int retencion,
                              int retencionDlq, int esperaSondeo) {
        Map<QueueAttributeName, String> atributos = new EnumMap<>(QueueAttributeName.class);
        atributos.put(QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(visibilidad));
        atributos.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(retencion));
        atributos.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, Integer.toString(esperaSondeo));
        String colaUrl = crearOAjustar(nombre, Map.of());
        if (dlq != null) {
            String dlqUrl = crearOAjustar(dlq, Map.of(
                    QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(retencionDlq),
                    QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20"));
            String dlqArn = arnCola(dlqUrl);
            atributos.put(QueueAttributeName.REDRIVE_POLICY, json(Map.of("deadLetterTargetArn", dlqArn,
                    "maxReceiveCount", Integer.toString(maxRecepciones))));
            // Solo la cola de origen puede usar esta DLQ.
            sqs.setQueueAttributes(r -> r.queueUrl(dlqUrl).attributes(Map.of(QueueAttributeName.REDRIVE_ALLOW_POLICY,
                    json(Map.of("redrivePermission", "byQueue", "sourceQueueArns", List.of(arnCola(colaUrl)))))));
        }
        sqs.setQueueAttributes(r -> r.queueUrl(colaUrl).attributes(atributos));
        log.info("ADMIN cola asegurada {} dlq={} maxReceiveCount={} visibilidad={}s retencion={}s retencionDlq={}s",
                nombre, dlq, maxRecepciones, visibilidad, retencion, retencionDlq);
    }

    private String crearOAjustar(String nombre, Map<QueueAttributeName, String> atributos) {
        String url = urlSiExiste(nombre);
        if (url == null) return sqs.createQueue(r -> r.queueName(nombre).attributes(atributos)).queueUrl();
        if (!atributos.isEmpty()) sqs.setQueueAttributes(r -> r.queueUrl(url).attributes(atributos));
        return url;
    }

    /** Política de la cola que autoriza solo a este tópico a enviarle mensajes. Devuelve el ARN de la cola. */
    private String permitirTopico(String colaUrl, String topicArn) {
        String colaArn = arnCola(colaUrl);
        String politica = json(Map.of("Version", "2012-10-17", "Statement", List.of(Map.of(
                "Sid", "PermitirTopicoRiffPass", "Effect", "Allow",
                "Principal", Map.of("Service", "sns.amazonaws.com"), "Action", "sqs:SendMessage",
                "Resource", colaArn, "Condition", Map.of("ArnEquals", Map.of("aws:SourceArn", topicArn))))));
        sqs.setQueueAttributes(r -> r.queueUrl(colaUrl).attributes(Map.of(QueueAttributeName.POLICY, politica)));
        return colaArn;
    }

    private Long antiguedad(String cola) {
        try {
            Instant ahora = Instant.now();
            return cloudWatch.getMetricStatistics(r -> r.namespace("AWS/SQS").metricName("ApproximateAgeOfOldestMessage")
                            .dimensions(Dimension.builder().name("QueueName").value(cola).build())
                            .startTime(ahora.minus(15, ChronoUnit.MINUTES)).endTime(ahora).period(60)
                            .statistics(Statistic.MAXIMUM)).datapoints().stream()
                    .max(Comparator.comparing(Datapoint::timestamp)).map(d -> d.maximum().longValue()).orElse(null);
        } catch (RuntimeException ex) {
            log.debug("Sin métrica de antigüedad para {}: {}", cola, ex.toString());
            return null;
        }
    }

    private String dlqDe(String cola) {
        String dlq = estado(cola).dlq();
        if (dlq == null) throw new ApiException(HttpStatus.CONFLICT, "La cola " + cola + " no tiene DLQ configurada.");
        return dlq;
    }

    private void protegido(String nombre, boolean forzar) {
        boolean declarado = nombre.equals(props.topico()) || nombre.equals(props.alarmas().topicoAlertas())
                || props.casoDe(nombre).isPresent();
        if (declarado && !forzar)
            throw new ApiException(HttpStatus.CONFLICT, nombre + " forma parte de la topología declarada; usa forzar=true.");
    }

    private void existeSuscripcion(String topico, String arn) {
        if (listarSuscripciones(topico).stream().noneMatch(s -> arn.equals(s.arn())))
            throw new ApiException(HttpStatus.NOT_FOUND, "Suscripción no encontrada.");
    }

    private Suscripcion suscripcion(String arn, String protocolo, String destino) {
        if (arn == null || !arn.startsWith("arn:")) return new Suscripcion(arn, arn, protocolo, destino, null, null);
        Map<String, String> a = sns.getSubscriptionAttributes(r -> r.subscriptionArn(arn)).attributes();
        return new Suscripcion(ultimo(arn), arn, protocolo, destino != null ? destino : a.get("Endpoint"),
                Boolean.valueOf(a.getOrDefault("RawMessageDelivery", "false")), a.get("FilterPolicy"));
    }

    private String arnTopico(String nombre) {
        return listarTopicos().stream().filter(t -> t.nombre().equals(nombre)).map(Topico::arn).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tópico no encontrado: " + nombre));
    }

    private String url(String cola) {
        String url = urlSiExiste(cola);
        if (url == null) throw new ApiException(HttpStatus.NOT_FOUND, "Cola no encontrada: " + cola);
        return url;
    }

    private String urlSiExiste(String cola) {
        try { return sqs.getQueueUrl(r -> r.queueName(cola)).queueUrl(); }
        catch (QueueDoesNotExistException ex) { return null; }
    }

    private boolean existe(String cola) { return urlSiExiste(cola) != null; }

    private String arnCola(String url) {
        return sqs.getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributesAsStrings().get("QueueArn");
    }

    private String filtro(List<TipoEvento> tipos) {
        return json(Map.of(AtributosMensaje.TIPO, tipos.stream().map(Enum::name).toList()));
    }

    private String json(Object valor) {
        try { return mapper.writeValueAsString(valor); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private Map<?, ?> leer(String json) {
        try { return mapper.readValue(json, Map.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private static software.amazon.awssdk.services.sns.model.MessageAttributeValue atributoSns(String valor) {
        return software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder().dataType("String")
                .stringValue(valor).build();
    }

    private static Map<String, String> texto(Map<String, MessageAttributeValue> atributos) {
        Map<String, String> valores = new TreeMap<>();
        atributos.forEach((k, v) -> valores.put(k, v.stringValue()));
        return valores;
    }

    private static long largo(Map<String, String> atributos, String nombre) {
        String valor = atributos.get(nombre);
        return valor == null ? 0 : Long.parseLong(valor);
    }

    private static String ultimo(String arnOUrl) {
        return arnOUrl.substring(Math.max(arnOUrl.lastIndexOf(':'), arnOUrl.lastIndexOf('/')) + 1);
    }
}
