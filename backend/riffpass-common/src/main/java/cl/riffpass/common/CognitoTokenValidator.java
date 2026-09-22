package cl.riffpass.common;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** Además de firma, issuer y fechas que comprueba el decoder. */
public class CognitoTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String clientId;
    private final String audience;
    public CognitoTokenValidator(String clientId, String audience) {
        this.clientId = clientId;
        this.audience = audience;
    }
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))
                || !clientId.equals(jwt.getClaimAsString("client_id"))
                || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return invalid();
        }
        // aud es la API destinataria cuando se usa resource binding, no el App Client.
        if (!audience.isBlank() && (jwt.getAudience() == null || !jwt.getAudience().contains(audience))) return invalid();
        return OAuth2TokenValidatorResult.success();
    }
    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "Token de acceso no válido para RiffPass.", null));
    }
}
