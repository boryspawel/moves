# Moves Mobile — architektura SOTA, bezpieczeństwo i prywatność

**Status:** rekomendacje projektowe; wymagają potwierdzenia w M0 przed zmianami produkcyjnymi.

## Proponowana architektura Android

- Kotlin, Jetpack Compose, Material Design 3, adaptive layouts.
- Warstwy: Compose UI → ViewModel z StateFlow + unidirectional data flow → repository → klient REST/OpenAPI → istniejący Spring Backend.
- Na początek umiarkowany podział (app, core network/auth/ui, feature today/clients/appointment/documentation). Nie tworzyć nadmiarowych modułów Gradle bez potrzeby.
- Klient Kotlin generowany ze snapshotu OpenAPI, z weryfikacją obsługi oneOf, enumów, UUID, Instant, stref czasowych, opcjonalności i Problem Details. Nie kopiować DTO z TypeScript.
- Backend zachowuje własność reguł: plan, snapshot, safety, zgoda, dostęp, status spotkania, wersjonowanie, warunki closeout.
- Wariant mobilny i tabletowy: nawigacja kontekstowa, TalkBack, dynamic font size, semantyka, touch targets, dark theme, zredukowany ruch.

## OIDC / Keycloak

- Natywny public client: Authorization Code + PKCE przez systemową przeglądarkę / Custom Tabs, nie przez embedded WebView.
- Nie umieszczać client secret w aplikacji. Sprawdzić redirect URI / App Links.
- Backend OAuth2 Resource Server waliduje issuer i audience — nie wyłączać kontroli JWT, aby uruchomić mobile. Zaprojektować poprawne mapowanie audience i ról w Keycloak.
- Refresh token, wylogowanie i zmiana konta z bezpiecznym usuwaniem sesji; nie zapisywać tokenów w logach/telemetrii, URL, schowku ani crash reportach.
- Lokalne odblokowanie biometryczne może chronić UI, ale nie zastępuje backendowej autoryzacji.
- Każdy odczyt/zapis uczestnika za aktywną relacją, capability, purpose, ActingContext i zgodą (ADR-008). Trener widzi tylko effective safety envelope; clinical rationale pozostaje chronione osobnym kontekstem i zgodą.

## PHI na urządzeniu i utrata sieci

- Strategia **online-first**. Brak domyślnej pełnej repliki kartotek medycznych.
- Minimalizacja cache, bezpieczna pamięć sesji i polityka jego czyszczenia po 401/403, logout, zmianie konta, utracie relacji/zgody.
- Serwerowe szkice dokumentacji wykorzystywać w pierwszej kolejności, gdy API je oferuje (wywiad, notatki).
- Formularze bez serwerowego szkicu (np. jednorazowy zapis wykonania) wymagają jawnej decyzji w M0: bez trwałego szkicu z ostrzeżeniem lub szyfrowany krótkotrwały szkic z Android Keystore, TTL i zasadami czyszczenia. Nie zakładać automatycznie local persistence.
- Żadnej kolejki cichych mutacji medycznych po powrocie internetu; po reconnect odświeżyć status, wersję i pozwolić na jawne ponowienie z tym samym kluczem idempotencji.
- Konflikt 409 nie uruchamia automatycznego overwrite; ponowny odczyt i decyzja użytkownika.
- Brak PHI w powiadomieniach, logach, analytics, backupie systemowym i podglądzie recent apps zgodnie z przyjętą polityką prywatności.
- Data/czas: API UTC/Instant, widok w utrwalonej strefie IANA specjalisty, w tym DST.

## Bezpieczne MVP — minimalna walidacja

- OIDC: prawidłowy aud/iss, niepoprawny aud, 401, 403, logout/refresh, utrata poświadczeń.
- Izolacja uczestników i specjalistów, przypadki trenera i fizjoterapeuty, cofnięcie zgody i relacji.
- Brak PHI w lokalnej telemetrii/backupie oraz po wylogowaniu; audyt pozostaje po stronie serwera.
- Retry/idempotencja i optimistic locking; snapshot planu i finalnej dokumentacji nie ulega nadpisaniu.
- Jakość: wydajność list, poprawność stanu na background/foreground, accessibility TalkBack i skalowanie tekstu.
- Odwołanie do OWASP MASVS: STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, PRIVACY; nie deklarować certyfikacji bez weryfikacji.

## Źródła SOTA

- https://developer.android.com/topic/architecture/recommendations
- https://developer.android.com/topic/architecture/data-layer
- https://developer.android.com/topic/architecture/data-layer/offline-first
- https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout
- https://www.rfc-editor.org/rfc/rfc8252
- https://mas.owasp.org/MASVS/
