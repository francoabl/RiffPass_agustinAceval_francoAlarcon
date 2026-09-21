package cl.duoc.pedidos360.pedidos.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convierte los claims de Cognito en authorities de Spring Security:
 * - "cognito:groups" -> ROLE_ADMIN, ROLE_CLIENTE, ...
 * - "scope"          -> SCOPE_xxx
 */
@Component
public class CognitoJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            groups.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g.toUpperCase())));
        }

        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.split(" ")) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
