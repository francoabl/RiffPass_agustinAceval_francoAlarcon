package cl.riffpass.admin.service;

import cl.riffpass.admin.web.AdminDtos.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/**
 * Llama a la API de control de cada réplica del consumidor reenviando el JWT del administrador.
 * Una réplica detenida se informa como no disponible, sin impedir operar las demás.
 */
@Component
public class ConsumidoresClient {
    private record Remoto(String id, String estado, String instancia) {}
    private final RestClient client;
    private final List<String> instancias;

    public ConsumidoresClient(RestClient.Builder builder, @Value("${riffpass.admin.consumidores}") String urls) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(30));
        client = builder.requestFactory(factory).build();
        instancias = Arrays.stream(urls.split(",")).map(String::trim).filter(u -> !u.isEmpty()).toList();
    }

    public List<Consumidor> listar(String token) {
        List<Consumidor> resultado = new ArrayList<>();
        for (String url : instancias) {
            try {
                Remoto[] remotos = client.get().uri(url + "/api/consumidores")
                        .headers(h -> h.setBearerAuth(token)).retrieve().body(Remoto[].class);
                for (Remoto r : Objects.requireNonNull(remotos)) resultado.add(new Consumidor(r.instancia(), url, r.id(), r.estado(), null));
            } catch (RestClientException ex) {
                resultado.add(new Consumidor(null, url, null, "NO_DISPONIBLE", ex.getClass().getSimpleName()));
            }
        }
        return resultado;
    }

    public List<Consumidor> cambiar(String id, EstadoConsumidor estado, String token) {
        List<Consumidor> resultado = new ArrayList<>();
        for (String url : instancias) {
            try {
                Remoto r = client.put().uri(url + "/api/consumidores/{id}/estado", id)
                        .headers(h -> h.setBearerAuth(token)).body(Map.of("estado", estado.name()))
                        .retrieve().body(Remoto.class);
                resultado.add(new Consumidor(r.instancia(), url, r.id(), r.estado(), null));
            } catch (RestClientException ex) {
                resultado.add(new Consumidor(null, url, id, "NO_DISPONIBLE", ex.getClass().getSimpleName()));
            }
        }
        return resultado;
    }
}
