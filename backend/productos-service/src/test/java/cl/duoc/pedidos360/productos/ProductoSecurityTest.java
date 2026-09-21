package cl.duoc.pedidos360.productos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductoSecurityTest {

    @Autowired
    MockMvc mvc;

    private static final String BODY =
            "{\"nombre\":\"Teclado\",\"descripcion\":\"Mecanico\",\"precio\":25990,\"stock\":10,\"activo\":true}";

    @Test
    void sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenBasura_devuelve401() throws Exception {
        mvc.perform(get("/api/productos").header("Authorization", "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteAutenticado_puedeListar() throws Exception {
        mvc.perform(get("/api/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());
    }

    @Test
    void cliente_noPuedeCrear_devuelve403() throws Exception {
        mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void admin_creaYReserva() throws Exception {
        String resp = mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(resp, "$.id");

        mvc.perform(post("/api/productos/" + id + "/reservar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":3}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));

        mvc.perform(post("/api/productos/" + id + "/reservar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":99}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void liberar_devuelveStock() throws Exception {
        String resp = mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(resp, "$.id");
        mvc.perform(post("/api/productos/" + id + "/liberar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(15));
    }
}
