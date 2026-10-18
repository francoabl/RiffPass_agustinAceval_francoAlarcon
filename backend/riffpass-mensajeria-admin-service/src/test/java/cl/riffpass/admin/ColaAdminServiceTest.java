package cl.riffpass.admin;

import cl.riffpass.admin.service.*;
import cl.riffpass.common.ApiException;
import cl.riffpass.mensajeria.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ColaAdminServiceTest {
    SqsClient sqs = mock(SqsClient.class);
    SnsClient sns = mock(SnsClient.class);
    ObjectMapper mapper = new ObjectMapper();
    MensajeriaProperties.Cola tickets = new MensajeriaProperties.Cola("riffpass-tickets", "riffpass-tickets-dlq",
            "consumidor-tickets", "tickets", List.of(TipoEvento.ENTRADA_EMITIDA), 3, Duration.ofSeconds(30),
            Duration.ofDays(4), Duration.ofDays(14), Duration.ofSeconds(20));
    MensajeriaProperties props = new MensajeriaProperties(true, "riffpass-eventos", false, Map.of(
            CasoUso.TICKETS, tickets, CasoUso.NOTIFICACIONES, tickets, CasoUso.AUDITORIA, tickets),
            new MensajeriaProperties.Alarmas(true, "alarma", "riffpass-alertas", ""));
    ColaAdminService admin = new ColaAdminService(sqs, sns, mock(CloudWatchClient.class), props,
            mock(ConsumidoresClient.class), mapper);

    @SuppressWarnings("unchecked")
    @BeforeEach void colasExistentes() {
        when(sqs.getQueueUrl(any(Consumer.class))).thenAnswer(inv -> {
            var builder = GetQueueUrlRequest.builder();
            ((Consumer<GetQueueUrlRequest.Builder>) inv.getArgument(0)).accept(builder);
            return GetQueueUrlResponse.builder().queueUrl("https://sqs/123/" + builder.build().queueName()).build();
        });
        when(sqs.getQueueAttributes(any(Consumer.class))).thenAnswer(inv -> {
            var builder = GetQueueAttributesRequest.builder();
            ((Consumer<GetQueueAttributesRequest.Builder>) inv.getArgument(0)).accept(builder);
            String nombre = builder.build().queueUrl().substring("https://sqs/123/".length());
            return GetQueueAttributesResponse.builder().attributesWithStrings(
                    Map.of("QueueArn", "arn:aws:sqs:us-east-1:123:" + nombre)).build();
        });
    }

    @SuppressWarnings("unchecked")
    @Test void declaresQueueWithRetentionAndRedrivePolicy() throws Exception {
        admin.asegurarColaConDlq(tickets);
        ArgumentCaptor<Consumer<SetQueueAttributesRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqs, atLeast(2)).setQueueAttributes(captor.capture());
        var solicitudes = captor.getAllValues().stream().map(c -> {
            var b = SetQueueAttributesRequest.builder(); c.accept(b); return b.build(); }).toList();
        var principal = solicitudes.stream().filter(r -> r.queueUrl().endsWith("/riffpass-tickets")).findFirst().orElseThrow();
        assertEquals("345600", principal.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD));
        assertEquals("30", principal.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT));
        Map<?, ?> redrive = mapper.readValue(principal.attributes().get(QueueAttributeName.REDRIVE_POLICY), Map.class);
        assertEquals("arn:aws:sqs:us-east-1:123:riffpass-tickets-dlq", redrive.get("deadLetterTargetArn"));
        assertEquals("3", redrive.get("maxReceiveCount"));
        assertTrue(solicitudes.stream().anyMatch(r -> r.queueUrl().endsWith("-dlq")
                && r.attributes().containsKey(QueueAttributeName.REDRIVE_ALLOW_POLICY)));
    }

    @SuppressWarnings("unchecked")
    @Test void subscribesWithRawDeliveryAndTypeFilter() throws Exception {
        var topicos = mock(software.amazon.awssdk.services.sns.paginators.ListTopicsIterable.class);
        when(topicos.topics()).thenReturn(() -> List.of(Topic.builder().topicArn("arn:aws:sns:us-east-1:123:riffpass-eventos").build()).iterator());
        when(sns.listTopicsPaginator(any(Consumer.class))).thenReturn(topicos);
        when(sns.subscribe(any(Consumer.class))).thenReturn(SubscribeResponse.builder().subscriptionArn("pendiente").build());
        admin.suscribirCola("riffpass-eventos", "riffpass-tickets", List.of(TipoEvento.ENTRADA_EMITIDA));
        ArgumentCaptor<Consumer<SubscribeRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sns).subscribe(captor.capture());
        var b = SubscribeRequest.builder(); captor.getValue().accept(b);
        SubscribeRequest solicitud = b.build();
        assertEquals("sqs", solicitud.protocol());
        assertEquals("arn:aws:sqs:us-east-1:123:riffpass-tickets", solicitud.endpoint());
        assertEquals("true", solicitud.attributes().get("RawMessageDelivery"));
        assertEquals("{\"tipo\":[\"ENTRADA_EMITIDA\"]}", solicitud.attributes().get("FilterPolicy"));
    }

    @Test void declaredResourcesCannotBeDeletedByAccident() {
        assertEquals(409, assertThrows(ApiException.class, () -> admin.eliminarCola("riffpass-tickets-dlq", false))
                .getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> admin.eliminarTopico("riffpass-eventos", false))
                .getStatus().value());
    }
}
