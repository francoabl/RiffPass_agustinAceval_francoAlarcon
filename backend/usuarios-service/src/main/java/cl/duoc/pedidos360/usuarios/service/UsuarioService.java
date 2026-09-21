package cl.duoc.pedidos360.usuarios.service;

import cl.duoc.pedidos360.usuarios.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.usuarios.model.Usuario;
import cl.duoc.pedidos360.usuarios.repository.UsuarioRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    /** Devuelve el perfil del usuario del token; lo crea la primera vez (aprovisionamiento just-in-time). */
    @Transactional
    public Usuario obtenerOCrear(Jwt jwt, boolean esAdmin) {
        return repository.findByCognitoSub(jwt.getSubject()).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCognitoSub(jwt.getSubject());
            u.setEmail(jwt.getClaimAsString("email"));
            String nombre = jwt.getClaimAsString("name");
            if (nombre == null) nombre = jwt.getClaimAsString("cognito:username");
            if (nombre == null) nombre = jwt.getClaimAsString("username");
            u.setNombre(nombre);
            u.setRol(esAdmin ? "ADMIN" : "CLIENTE");
            return repository.save(u);
        });
    }

    public List<Usuario> listar() {
        return repository.findAll();
    }

    public Usuario obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no existe"));
    }
}
