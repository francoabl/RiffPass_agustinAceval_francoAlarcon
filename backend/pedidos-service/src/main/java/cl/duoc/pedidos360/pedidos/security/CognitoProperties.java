package cl.duoc.pedidos360.pedidos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Datos del User Pool de Cognito contra el que se valida el JWT. */
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(String region, String userPoolId, String appClientId) {

    /** Claim "iss" esperado en el token. */
    public String issuer() {
        return "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
    }

    /** Endpoint publico con las llaves para verificar la firma. */
    public String jwksUri() {
        return issuer() + "/.well-known/jwks.json";
    }
}
