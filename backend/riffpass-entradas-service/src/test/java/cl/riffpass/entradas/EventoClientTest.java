package cl.riffpass.entradas;

import cl.riffpass.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class EventoClientTest {
    private HttpServer server;
    private String baseUrl;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0),0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void forwardsTokenAndParsesEvent() {
        UUID id = UUID.randomUUID();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/eventos/" + id, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] json = ("{\"id\":\""+id+"\",\"fecha\":\"2030-01-01T00:00:00Z\",\"organizadorSub\":\"owner\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,json.length);
            try (var output = exchange.getResponseBody()) { output.write(json); }
        });
        var client = new EventoClient(RestClient.builder(),baseUrl);
        assertEquals("owner",client.obtener(id,"original-token").organizadorSub());
        assertEquals("Bearer original-token",authorization.get());
    }
    @Test void handlesDownstreamFailuresWithoutLeakingResponse() {
        server.createContext("/api/eventos", exchange -> { exchange.sendResponseHeaders(500,-1); exchange.close(); });
        var client = new EventoClient(RestClient.builder(),baseUrl);
        assertEquals(503,assertThrows(ApiException.class,()->client.obtener(UUID.randomUUID(),"token")).getStatus().value());
    }
}
