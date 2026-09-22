package cl.riffpass.common;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

class CognitoSecurityTest {
    private static RSAKey key;
    private static HttpServer server;
    private static String issuer;
    private JwtDecoder decoder;
    @BeforeAll static void start() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/jwks.json", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void setup() { decoder = new SecurityConfig().jwtDecoder(issuer, "client", ""); }
    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(issuer).subject("usuario")
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("client_id", "client").claim("token_use", "access")
                .claim("scope", "resource-server-riffpass/eventos-read")
                .claim("cognito:groups", List.of("ORGANIZADOR"));
    }
    private String sign(JWTClaimsSet.Builder claims, RSAKey signingKey) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
        token.sign(new RSASSASigner(signingKey));
        return token.serialize();
    }
    @Test void acceptsSignedCognitoAccessTokenAndMapsScopesAndGroups() throws Exception {
        Jwt jwt = decoder.decode(sign(claims(), key));
        var auth = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZADOR")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_resource-server-riffpass/eventos-read")));
    }
    @Test void rejectsForgedSignature() throws Exception {
        String token = sign(claims(), new RSAKeyGenerator(2048).generate());
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsExpiredToken() throws Exception {
        String token = sign(claims().expirationTime(Date.from(Instant.now().minusSeconds(300))), key);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsDifferentIssuer() throws Exception {
        String token = sign(claims().issuer("https://otro-pool.example"), key);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsDifferentClient() throws Exception {
        String token = sign(claims().claim("client_id", "otro-client"), key);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsIdToken() throws Exception {
        String token = sign(claims().claim("token_use", "id").audience("client"), key);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsMissingExpiry() throws Exception {
        String token = sign(claims().expirationTime(null), key);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void validatesOptionalResourceAudience() throws Exception {
        JwtDecoder boundDecoder = new SecurityConfig().jwtDecoder(issuer, "client", "https://riffpass.example");
        String missing = sign(claims(), key);
        assertThrows(JwtException.class, () -> boundDecoder.decode(missing));
        String bad = sign(claims().audience("https://otra-api.example"), key);
        assertThrows(JwtException.class, () -> boundDecoder.decode(bad));
        assertNotNull(boundDecoder.decode(sign(claims().audience("https://riffpass.example"), key)));
    }
}
