package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.exception.PedidoInvalidoException;
import cl.duoc.pedidos360.pedidos.exception.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class HttpProductoClient implements ProductoClient {

    private final RestClient rest;

    public HttpProductoClient(@Value("${productos.service.url}") String baseUrl) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void liberar(Long productoId, int cantidad, String bearerToken) {
        rest.post()
                .uri("/api/productos/{id}/liberar", productoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cantidad", cantidad))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public ProductoInfo reservar(Long productoId, int cantidad, String bearerToken) {
        try {
            return rest.post()
                    .uri("/api/productos/{id}/reservar", productoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve()
                    .body(ProductoInfo.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RecursoNoEncontradoException("Producto " + productoId + " no existe");
            }
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new PedidoInvalidoException("Stock insuficiente para el producto " + productoId);
            }
            throw e;
        }
    }
}
