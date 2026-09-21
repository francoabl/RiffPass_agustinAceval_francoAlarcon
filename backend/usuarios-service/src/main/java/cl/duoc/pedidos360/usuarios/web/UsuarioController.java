package cl.duoc.pedidos360.usuarios.web;

import cl.duoc.pedidos360.usuarios.model.Usuario;
import cl.duoc.pedidos360.usuarios.service.UsuarioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    /** Perfil del usuario autenticado (cualquier rol). */
    @GetMapping("/me")
    public Usuario me(JwtAuthenticationToken auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return service.obtenerOCrear(auth.getToken(), admin);
    }

    /** Claims relevantes del token (util para depurar y mostrar en la presentacion). */
    @GetMapping("/me/claims")
    public Map<String, Object> claims(Authentication authentication) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return Map.of(
                "sub", jwt.getSubject(),
                "issuer", String.valueOf(jwt.getIssuer()),
                "expira", String.valueOf(jwt.getExpiresAt()),
                "roles", authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}
