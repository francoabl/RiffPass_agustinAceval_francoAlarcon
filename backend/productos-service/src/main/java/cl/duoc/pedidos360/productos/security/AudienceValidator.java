package cl.duoc.pedidos360.productos.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Valida el "audience" del token.
 * - ID token de Cognito: el App Client ID viene en el claim "aud".
 * - Access token de Cognito: el App Client ID viene en el claim "client_id".
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String appClientId;

    public AudienceValidator(String appClientId) {
        this.appClientId = appClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> aud = jwt.getAudience();
        String clientId = jwt.getClaimAsString("client_id");
        if ((aud != null && aud.contains(appClientId)) || appClientId.equals(clientId)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "El token no fue emitido para este App Client (audience invalido)", null));
    }
}
