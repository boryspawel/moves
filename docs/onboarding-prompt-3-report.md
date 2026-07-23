# Prompt 3 — raport kontroli runtime onboardingu

## Status końcowy

**NOT ACCEPTABLE FOR RELEASE** — blokada do czasu wykonania rzeczywistego QA
na uruchomionym Compose z lokalnym OIDC, przygotowanymi kontami i zebranymi
artefaktami przeglądarkowymi.

## Zakres wykonany statycznie

Dodano statyczny harness Playwright dla rzeczywistego OIDC i backendu, bez
interceptowania requestów i bez tożsamości lub sekretów w repozytorium:

- `web/playwright.config.ts` — konfiguracja Chromium, artefaktów i
  konfigurowalnego `E2E_BASE_URL`;
- `web/e2e/fixtures.ts` — logowanie OIDC oraz odczyt poświadczeń wyłącznie ze
  zmiennych środowiskowych;
- `web/e2e/authenticated-navigation.spec.ts` — sprawdzenia gotowego
  uczestnika, refreshu sesji, nawigacji, klawiatury i axe, widoków 390/320 px,
  rzeczywistego błędu/retry oraz opcjonalnych kont specjalisty i wznowienia;
- `web/package.json` i `web/README.md` — polecenia oraz instrukcje bez
  ujawniania poświadczeń.

Statycznie sprawdzono, że wymagane poświadczenia są pobierane wyłącznie z
`E2E_*_USERNAME`/`E2E_*_PASSWORD`, opcjonalne scenariusze jawnie się pomijają
bez ich par, a suite nie tworzy planu, sesji ani potwierdzeń prawnych. Nie
uruchomiono kompilacji ani Playwrighta, więc nie ma wyniku tych sprawdzeń w
środowisku wykonawczym.

## Runtime, przeglądarka i artefakty

**NOT EXECUTED.** Compose i przeglądarka nie zostały uruchomione. W związku z
tym nie wykonano ani nie potwierdzono:

- pierwszego wejścia nowego użytkownika; refreshu na każdym kroku;
  wylogowania i ponownego logowania; przerwania i wznowienia onboardingu;
  profilu uczestnika i specjalisty; niepoprawnego pola profilu oraz
  niepoprawnego przedziału godzin; błędu początkowego GET state i błędu zapisu
  każdego kroku; podwójnego szybkiego kliknięcia głównego przycisku; READY i
  przejścia do katalogu; bezpośredniego wejścia na inne trasy przed ukończeniem
  onboardingu; sesji po wygaśnięciu albo odświeżeniu tokenu;
- kontroli 1440×900, 1024×768, 768×1024, 390×844 i 320×700: poziomego scrolla,
  uciętych przycisków, czytelności pól, kolejności treści, celów dotykowych,
  nachodzenia toolbara i scrollowania do błędnego pola;
- pełnego flow samą klawiaturą, kolejności tabulatora, focusu po zmianie kroku,
  axe dla każdego kroku, nazw przycisków, `aria-live`, `aria-current`,
  kontrastu i reduced motion;
- konsoli, requestów/statusów API, payloadów `participantProfile.timeZoneId`
  i availability, formatu godzin, 401/403/404/5xx oraz zachowania Keycloak;
- wymaganych zrzutów każdego kroku desktop i mobile, loadingu, initial-load
  error, validation error, API error, READY, focusu klawiaturowego i
  rozwiniętego formularza kilku przedziałów dostępności.

Nie istnieją zatem ścieżki do zrzutów, trace’ów, filmów, raportu axe ani
wyników E2E dla tego raportu.

## Blockery

- Próba uzyskania eskalacji dostępu do socketu Docker/uruchomienia Compose
  przekroczyła czas oczekiwania; bez działającego stosu nie można wykonać
  rzeczywistego OIDC ani QA runtime.
- Nie udostępniono niezależnego konta specjalisty ani świeżego/oddzielnego
  konta uczestnika z wymaganym przygotowanym stanem, potrzebnych do pełnego
  scenariusza specjalisty i wznowienia. Żadne poświadczenia ani sekrety nie
  zostały pozyskane, zapisane lub ujawnione.

## Git

Stan bazowy obserwowany podczas przygotowania raportu: `main...origin/main
[ahead 1]`. Working tree zawierał niezatwierdzone zmiany w `web/README.md` i
`web/package.json` oraz nieśledzone `web/e2e/` i `web/playwright.config.ts`;
niniejszy raport dokumentuje ten stan i nie wykonuje commita. Ostateczny status
Git oraz pełne `git diff --check` wymagają weryfikacji po integracji wszystkich
zmian.

## Wymagana walidacja przed wydaniem

Uruchomić Compose z lokalnym Keycloak, przekazać poświadczenia przez środowisko,
przygotować niezależne fixtures specjalisty i uczestnika, wykonać pełny zestaw
Prompt 3 w rzeczywistej przeglądarce, zachować wymagane zrzuty/artefakty i
wykonać końcowe sprawdzenie Git oraz walidację frontendu.
