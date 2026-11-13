# MOB-011 — Android / OIDC / Keycloak

**Status:** implementacja PKCE, klienta publicznego, odświeżania sesji i logout w kodzie. Logowanie na prawdziwym urządzeniu/emulatorze oraz weryfikacja aud/iss/401/403 **niezrealizowane**.

## Implementacja

- AppAuth 0.11.1: Authorization Code + PKCE S256, state i nonce, zewnętrzna przeglądarka/Custom Tabs. Brak WebView i client secret.
- Osobne publiczne klienty Keycloak `motion-android-dev` i `motion-android` z mapperem audience `motion-api`; dokładne URI przekierowania `com.motionecosystem.moves.mobile.dev:/oauth2redirect` i `com.motionecosystem.moves.mobile:/oauth2redirect`.
- Stan uwierzytelnienia i tokeny przechowywane wyłącznie w RAM. Restart procesu wymaga nowego logowania; brak trwałych tokenów, cache PHI i synchronizacji offline.
- Odświeżanie access tokenu przez AppAuth przed GET `/api/v1/identity/me`; logout natychmiast usuwa lokalną sesję, a zdalne OIDC end-session jest best effort.
- Backend nie został zmieniony: nadal wymaga poprawnego podpisu, issuer, audience `motion-api` i uprawnień.

## Konfiguracja emulatora

Domyślne Compose wystawia issuer `http://localhost:8180/realms/motion-local`, niedostępny z emulatora. Ustaw w lokalnym `.env` **przed startem Compose**:

```dotenv
KEYCLOAK_PUBLIC_BASE_URL=http://10.0.2.2:8180
```

Wartość ta ustawia `KC_HOSTNAME` w Keycloak oraz `OIDC_ISSUER_URI` backendu. Następnie zbuduj APK:

```sh
cd android
./gradlew :app:testDevDebugUnitTest :app:assembleDevDebug -Pmoves.devOidcIssuer=http://10.0.2.2:8180/realms/motion-local
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Android domyślnie odwołuje się do API `http://10.0.2.2:8080/`. Dla telefonu fizycznego użyj jednego adresu LAN odpowiednio w `KEYCLOAK_PUBLIC_BASE_URL`, `-Pmoves.devOidcIssuer` i `-Pmoves.devBaseUrl`.

Realm importowany **po raz pierwszy** zawiera `motion-android-dev`. Już istniejącego realm import nie aktualizuje: skonfiguruj klienta ręcznie w Keycloak bez kasowania wolumenów. Wymagane: public client, standard flow, PKCE S256, wyłączone direct grants, dokładny redirect URI, audience mapper `motion-api`.

Wariant produkcyjny wymaga prawdziwego `-Pmoves.prodOidcIssuer` (HTTPS) i `-Pmoves.prodBaseUrl` (HTTPS). Bootstrap produkcyjny tworzy `motion-android` tylko podczas utworzenia nowego realm; istniejącego realm nie modyfikuje.

## Ograniczenia i kryteria odbioru

Przyjęty custom-scheme redirect nadaje się do testowania z PKCE. Przed produkcyjnym pilotażem zweryfikować HTTPS Android App Links i podpis APK albo formalnie zaakceptować ryzyko custom scheme.

Na urządzeniu wykonać: login trenera i fizjoterapeuty; prawidłowy aud/iss/role i autoryzowany GET; negatywne 401/403; refresh, logout, zmianę konta, anulowanie autoryzacji, restart aplikacji i brak tokenów w dysku/logach. Wynik samych testów JVM i CI nie jest dowodem tej weryfikacji.
