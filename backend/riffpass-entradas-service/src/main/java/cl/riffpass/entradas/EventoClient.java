package cl.riffpass.entradas;

import cl.riffpass.common.ApiException;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class EventoClient {
    private final RestClient client;
    public EventoClient(RestClient.Builder builder, @Value("${riffpass.eventos-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = builder.baseUrl(url).requestFactory(factory).build();
    }
    public Evento obtener(UUID id, String token) {
        try {
            Evento evento = client.get().uri("/api/eventos/{id}", id)
                    .headers(headers -> headers.setBearerAuth(token)).retrieve().body(Evento.class);
            if (evento == null || !id.equals(evento.id()) || evento.fecha() == null || evento.organizadorSub() == null)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Respuesta inválida del servicio de eventos.");
            return evento;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404)
                throw new ApiException(HttpStatus.NOT_FOUND, "Evento no encontrado.");
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
                throw new ApiException(HttpStatus.FORBIDDEN, "El token debe permitir consultar eventos (eventos-read).");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el evento.");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de eventos temporalmente no disponible.");
        }
    }
    public record Evento(UUID id, Instant fecha, String organizadorSub) {}
}
