package cl.riffpass.entradas;

import cl.riffpass.common.ApiException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntradaIntegrationTest {
    @Autowired EntradaService service;
    @Autowired EntradaRepository entradas;
    @Autowired InventarioRepository inventarios;
    @Autowired MockMvc mvc;
    @MockitoBean EventoClient eventos;
    private UUID eventoId;
    @BeforeEach void setup() {
        entradas.deleteAll(); inventarios.deleteAll();
        eventoId = UUID.randomUUID();
        when(eventos.obtener(eq(eventoId), anyString())).thenReturn(
                new EventoClient.Evento(eventoId, Instant.now().plusSeconds(86400), "owner"));
    }
    private void open(int cupos) { service.abrir(eventoId, cupos, "owner", "token"); }
    @Test void emitsListsOwnTicketsAndRejectsDuplicates() {
        open(2);
        var entrada = service.emitir(eventoId, "ana", "token");
        assertEquals(Entrada.Estado.EMITIDA, entrada.estado());
        assertEquals(1, service.mias("ana",0,20).size());
        assertEquals(0, service.mias("otro",0,20).size());
        assertEquals(409, assertThrows(ApiException.class, () -> service.emitir(eventoId,"ana","token")).getStatus().value());
        assertEquals(1, service.cupos(eventoId).disponibles());
    }
    @Test void validatesOnlyByOwnerAndOnlyOnce() {
        open(1);
        var entrada = service.emitir(eventoId, "ana", "token");
        assertEquals(403, assertThrows(ApiException.class, () -> service.validar(entrada.codigo(),"intruso","token")).getStatus().value());
        assertEquals(Entrada.Estado.UTILIZADA, service.validar(entrada.codigo(),"owner","token").estado());
        assertEquals(409, assertThrows(ApiException.class, () -> service.validar(entrada.codigo(),"owner","token")).getStatus().value());
    }
    @Test void cannotResetInventoryOrOpenSomeoneElsesEvent() {
        assertEquals(403, assertThrows(ApiException.class, () -> service.abrir(eventoId,2,"intruso","token")).getStatus().value());
        open(1);
        service.emitir(eventoId, "ana", "token");
        assertEquals(409, assertThrows(ApiException.class, () -> service.abrir(eventoId,20,"owner","token")).getStatus().value());
        assertEquals(0,service.cupos(eventoId).disponibles());
    }
    @Test void blocksPastEventsAndMissingInventory() {
        assertThrows(ApiException.class, () -> service.emitir(eventoId,"ana","token"));
        when(eventos.obtener(eq(eventoId),anyString())).thenReturn(new EventoClient.Evento(eventoId,Instant.now().minusSeconds(10),"owner"));
        assertEquals(409,assertThrows(ApiException.class, () -> service.abrir(eventoId,1,"owner","token")).getStatus().value());
    }
    @Test void simultaneousRequestsCannotOversell() throws Exception {
        open(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i=0;i<8;i++) {
                String sub = "user-" + i;
                attempts.add(executor.submit(() -> {
                    start.await();
                    try { service.emitir(eventoId,sub,"token"); return true; }
                    catch (ApiException ex) { assertEquals(409,ex.getStatus().value()); return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var attempt : attempts) if (attempt.get(20,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
            assertEquals(1,entradas.count());
            assertEquals(0,service.cupos(eventoId).disponibles());
        }
    }
    @Test void concurrentValidationSucceedsOnce() throws Exception {
        open(1);
        var entrada = service.emitir(eventoId,"ana","token");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                try { service.validar(entrada.codigo(),"owner","token"); return true; }
                catch (ApiException ex) { assertEquals(409,ex.getStatus().value()); return false; }
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentInventoryInitializationCannotOverwriteCapacity() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> action = () -> {
                start.await();
                try { service.abrir(eventoId, 1, "owner", "token"); return true; }
                catch (ApiException ex) { assertEquals(409, ex.getStatus().value()); return false; }
                catch (org.springframework.dao.DataIntegrityViolationException ex) { return false; }
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS), b.get(15,TimeUnit.SECONDS));
            assertEquals(1, service.cupos(eventoId).capacidad());
        }
    }
    @Test void endpointsEnforceAuthenticationRoleAndScopes() throws Exception {
        mvc.perform(get("/api/entradas/mias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/entradas/mias").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(post("/api/entradas/validar").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ASISTENTE"),
                        new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/entradas-write"),
                        new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/eventos-read")))
                .contentType("application/json").content("{\"codigo\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/entradas/mias").with(jwt().jwt(j -> j.subject("ana"))
                .authorities(new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/entradas-read"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
