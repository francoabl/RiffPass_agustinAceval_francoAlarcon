package cl.riffpass.admin;

import cl.riffpass.admin.service.ColaAdminService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ColaAdminService admin;

    @Test void requiresAdministratorRoleAndScope() throws Exception {
        when(admin.listarColas()).thenReturn(List.of());
        mvc.perform(get("/api/admin/colas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZADOR"),
                new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/mensajeria-admin")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"),
                new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/mensajeria-admin")))).andExpect(status().isOk());
    }

    @Test void validatesRequestBodies() throws Exception {
        mvc.perform(post("/api/admin/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"),
                        new SimpleGrantedAuthority("SCOPE_resource-server-riffpass/mensajeria-admin")))
                .contentType("application/json").content("{\"nombre\":\"mal nombre!\",\"conDlq\":true}"))
                .andExpect(status().isBadRequest());
    }
}
