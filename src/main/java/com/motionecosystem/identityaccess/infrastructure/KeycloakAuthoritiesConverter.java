package com.motionecosystem.identityaccess.infrastructure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

final class KeycloakAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String clientId;

    KeycloakAuthoritiesConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(rolesFrom(map(jwt.getClaim("realm_access"))));

        Map<String, Object> resourceAccess = map(jwt.getClaim("resource_access"));
        roles.addAll(rolesFrom(map(resourceAccess.get(clientId))));

        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private static Collection<String> rolesFrom(Map<String, Object> claims) {
        Object value = claims.get("roles");
        if (!(value instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
