package cl.riffpass.eventos;

import cl.riffpass.mensajeria.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class EventoIntegrationTest {
    @Autowired ApplicationEvents mensajes;
    @Autowired MockMvc mvc;
    @Autowired EventoRepository repository;
    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String sub, String role, String... scopes) {
        var authorities = new ArrayList<org.springframework.security.core.GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String scope : scopes) authorities.add(new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/" + scope));
        return jwt().jwt(j -> j.subject(sub)).authorities(authorities);
    }
    private String body() {
        return """
                {"nombre":"Rock local","artista":"Banda Sur","recinto":"Sala 1","ciudad":"Santiago",
                 "descripcion":"Concierto de bandas emergentes","fecha":"%s"}
                """.formatted(Instant.now().plusSeconds(86400));
    }
    @Test void rejectsAnonymousAndMalformedToken() throws Exception {
        mvc.perform(get("/api/eventos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/eventos").header("Authorization", "Bearer malformed")).andExpect(status().isUnauthorized());
    }
    @Test void scopeAndRoleAreBothRequired() throws Exception {
        mvc.perform(post("/api/eventos").with(actor("asistente", "ASISTENTE", "eventos-write"))
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/eventos").with(actor("owner", "ORGANIZADOR"))
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(get("/api/eventos").with(actor("owner", "ORGANIZADOR"))).andExpect(status().isForbidden());
    }
    @Test void createsWithTokenOwnerAndProtectsOwnership() throws Exception {
        String content = mvc.perform(post("/api/eventos").with(actor("owner", "ORGANIZADOR", "eventos-write"))
                .contentType("application/json").content(body())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizadorSub").value("owner"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(content).get("id").asText());
        assertTrue(repository.existsById(id));
        EventoMensaje creado = mensajes.stream(EventoMensaje.class).findFirst().orElseThrow();
        assertEquals(TipoEvento.EVENTO_CREADO, creado.tipo());
        assertEquals(id.toString(), creado.dato("eventoId"));
        assertEquals("owner", creado.dato("organizadorSub"));
        mvc.perform(put("/api/eventos/" + id).with(actor("intruso", "ORGANIZADOR", "eventos-write"))
                .contentType("application/json").content("{\"nombre\":\"Otro\",\"descripcion\":\"Cambio\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/eventos/" + id).with(actor("owner", "ORGANIZADOR", "eventos-write"))
                .contentType("application/json").content("{\"nombre\":\"Rock local actualizado\",\"descripcion\":\"Cambio\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Rock local actualizado"));
        mvc.perform(get("/api/eventos/" + id).with(actor("a", "ASISTENTE", "eventos-read"))).andExpect(status().isOk());
    }
    @Test void validatesBodyAndMissingId() throws Exception {
        mvc.perform(post("/api/eventos").with(actor("owner", "ORGANIZADOR", "eventos-write"))
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/eventos/" + UUID.randomUUID()).with(actor("a", "ASISTENTE", "eventos-read")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/eventos?size=1000").with(actor("a", "ASISTENTE", "eventos-read")))
                .andExpect(status().isBadRequest());
    }
    @Test void healthAndCorsPreflightDoNotRequireLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(options("/api/eventos").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","POST")
                .header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
}
