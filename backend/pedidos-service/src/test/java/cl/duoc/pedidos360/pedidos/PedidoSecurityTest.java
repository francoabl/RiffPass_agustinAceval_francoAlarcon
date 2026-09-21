package cl.duoc.pedidos360.pedidos;

import cl.duoc.pedidos360.pedidos.service.ProductoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProductoClient productoClient;

    private static final String PEDIDO = "{\"items\":[{\"productoId\":1,\"cantidad\":2}]}";

    @Test
    void sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    void cliente_creaPedido_totalCalculadoEnServidor() throws Exception {
        when(productoClient.reservar(eq(1L), eq(2), anyString()))
                .thenReturn(new ProductoClient.ProductoInfo(1L, "Teclado", new BigDecimal("10000"), 8));

        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(PEDIDO)
                        .with(jwt().jwt(j -> j.subject("cliente-1")).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(20000))
                .andExpect(jsonPath("$.estado").value("CREADO"));
    }

    @Test
    void cliente_noPuedeCambiarEstado_devuelve403() throws Exception {
        mvc.perform(patch("/api/pedidos/1/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADO\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pedidoSinItems_devuelve400() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isBadRequest());
    }
}
