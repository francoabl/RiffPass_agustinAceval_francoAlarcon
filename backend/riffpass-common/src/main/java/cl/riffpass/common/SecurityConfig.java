package cl.riffpass.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${riffpass.security.issuer}") String issuer,
                          @Value("${riffpass.security.client-id}") String clientId,
                          @Value("${riffpass.security.audience:}") String audience) {
        // URL de claves explícita: no necesita discovery al iniciar; verificación real al recibir JWT.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), new CognitoTokenValidator(clientId, audience)));
        return decoder;
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups != null) groups.stream().filter(g -> Set.of("ASISTENTE", "ORGANIZADOR", "ADMINISTRADOR").contains(g))
                    .map(g -> new SimpleGrantedAuthority("ROLE_" + g)).forEach(authorities::add);
            return authorities;
        });
        return converter;
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                           ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setHeader("WWW-Authenticate", "Bearer");
                            res.setContentType("application/problem+json");
                            mapper.writeValue(res.getOutputStream(), Map.of("status", 401,
                                    "title", "Unauthorized", "detail", "Se requiere un access token válido de Cognito."));
                        }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/problem+json");
                    mapper.writeValue(res.getOutputStream(), Map.of("status", 403,
                            "title", "Forbidden", "detail", "No tienes los permisos necesarios."));
                })).build();
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${riffpass.cors-origins:http://localhost:5173}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
