package cl.duoc.pedidos360.productos;

import cl.duoc.pedidos360.productos.security.AudienceValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("test-client-id");

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    @Test
    void idToken_conAudCorrecto_esValido() {
        assertFalse(validator.validate(jwt(Map.of("sub", "u1", "aud", List.of("test-client-id")))).hasErrors());
    }

    @Test
    void accessToken_conClientIdCorrecto_esValido() {
        assertFalse(validator.validate(jwt(Map.of("sub", "u1", "client_id", "test-client-id"))).hasErrors());
    }

    @Test
    void tokenDeOtroClient_esRechazado() {
        assertTrue(validator.validate(jwt(Map.of("sub", "u1", "client_id", "otro"))).hasErrors());
    }
}
