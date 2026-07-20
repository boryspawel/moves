# ADR-002: Keycloak zamiast własnego systemu sesji

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

`ruszsie` emituje prototypowe tokeny HS256 i przechowuje sesje/refresh tokeny. Docelowa specyfikacja wymaga OIDC/OAuth 2.1, PKCE i centralnego MFA.

## Decyzja

Keycloak jest jedynym wystawcą tokenów i właścicielem sesji. Backend działa jako OAuth2 Resource Server, waliduje podpis, issuer, audience i czas, a następnie mapuje `sub` do lokalnego konta. Federacja Google/Apple jest konfiguracją Keycloak, nie kodem domenowym.

## Konsekwencje

Nie migrujemy AccessTokenService, refresh token family ani login API. Autoryzacja zasobowa nadal pozostaje w backendzie i nie kończy się na roli tokenu.
