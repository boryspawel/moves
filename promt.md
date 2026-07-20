1)
Zaimplementuj Lombok, podmień konstruktory, obowiązuje constructor dependancy injection

2) jebc jest zakazane. Wyszukaj wszystkie przypadku użycia, obowiązuje cię jpa, hibernate. Utwórz oddzielne pliki repozytoriów.

3) Dokończ implementację front end, wykonaj przerwany prompt 7


Prompt 7 — nowy Angular i selektywny port UI
W repo `moves` utwórz docelowy frontend web w Angularze i selektywnie wykorzystaj doświadczenia z frontendu `gra-mateusza`.

Najpierw sprawdź oba frontendowe drzewa w `gra-mateusza`:

- `frontend/power-rpg-frontend` — aktywne;
- `frontend/src` — legacy;
- komponenty profilu;
- katalog ćwiczeń;
- plan i wykonanie treningu;
- gamifikację;
- panel administracyjny;
- autoryzację;
- klienty API;
- testy.

Nie kopiuj całego projektu Angular 17. Nie kopiuj legacy frontendu. Użyj istniejących komponentów wyłącznie jako źródła zachowań, informacji i ewentualnie niewielkich neutralnych fragmentów.

Docelowo

- Angular 22;
- TypeScript 6;
- standalone components;
- Angular Material/CDK;
- responsywny web;
- wygenerowany klient TypeScript z OpenAPI;
- OIDC/PKCE z Keycloak;
- route guards wyłącznie jako UX — backend pozostaje źródłem autoryzacji;
- dostępność WCAG 2.2 AA;
- brak nazw `power-rpg`, `gra-mateusza` i `ruszsie` w kodzie technicznym;
- neutralny branding `moves` jako nazwa robocza.

Pierwszy zakres UI

1. Logowanie.
2. Onboarding zależny od roli.
3. Profil uczestnika.
4. Profil specjalisty.
5. Katalog ćwiczeń.
6. Utworzenie prostego planu przez specjalistę.
7. Lista sesji uczestnika.
8. Deklaracja wykonania, bólu i trudności.
9. Alert w panelu specjalisty.
10. Podstawowy opt-in i wynik gamifikacji.

Nie implementuj teraz pełnej aplikacji mobilnej. Architektura i design system mają jednak umożliwiać późniejsze wykorzystanie w Ionic Angular + Capacitor.

Testy

- testy komponentów;
- routing i guards;
- kontrakty klienta OpenAPI;
- Playwright dla głównego vertical slice;
- dostępność kluczowych ekranów;
- brak odwołań do mock API, jeśli istnieje realny endpoint;
- build produkcyjny.

Przed użyciem backendu aktywuj Java 25 przez SDKMAN. Korzystaj maksymalnie z MCP IntelliJ i własnych skills.

Na końcu przedstaw:
- elementy UI portowane funkcjonalnie;
- elementy napisane od nowa;
- odrzucone elementy legacy;
- wyniki testów i builda;
- stan Git;
- rekomendację następnego kroku.

Nie wykonuj push.
Prompt 8 — końcowy audyt migracji
Wykonaj końcowy audyt repo `moves` po migracji z `ruszsie` i `gra-mateusza`.

Jest to zadanie diagnostyczne. Nie implementuj nowych funkcji, chyba że znajdziesz mały, jednoznaczny błąd uniemożliwiający walidację. Każdą taką poprawkę wyraźnie opisz.

Sprawdź:

1. Granice modularnego monolitu.
2. Brak zależności od pakietów i nazw:
    - powerrpg;
    - gra-mateusza;
    - ruszsie;
    - WspólnyRuch, poza dokumentacją pochodzenia.
3. Brak API Gateway, Spring Cloud i nieużywanych mikroserwisów.
4. Java 25 i Spring Boot 4.1.x.
5. Angular 22 i jeden aktywny frontend.
6. Keycloak/OIDC bez równoległego własnego systemu sesji.
7. Spójność migracji Flyway.
8. Relacje i uprawnienia uczestnik–specjalista.
9. Izolację danych medycznych od gamifikacji.
10. Wersjonowanie ćwiczeń.
11. Powiązanie plan → sesja → wykonanie.
12. Append-only execution i point ledger.
13. Zgody, audyt i widoczność danych.
14. Testy PostgreSQL/Testcontainers.
15. OpenAPI i wygenerowany klient Angular.
16. Brak martwego, mockowego i legacy kodu.
17. Dokumentację migracji i wskazanie źródłowych SHA.
18. Brak modyfikacji w repozytoriach źródłowych.

Przed buildem/testami aktywuj Java 25 przez SDKMAN. Wykorzystaj MCP IntelliJ do pełnej inspekcji, wyszukiwania użyć, analizy zależności i uruchomienia testów.

Przeprowadź walidację proporcjonalną do ryzyka:

- pełny backend;
- pełny frontend;
- testy integracyjne;
- testy architektury;
- migracje;
- główny E2E;
- analiza zależności;
- kontrola working tree wszystkich trzech repozytoriów.

Raport końcowy musi zawierać:

- co rzeczywiście zostało przeniesione z każdego repo;
- co zostało napisane od nowa;
- co odrzucono;
- aktualne możliwości produktu;
- niespełnione wymagania;
- regresje lub sprzeczności;
- aktywny dług techniczny;
- gotowość do dalszego rozwoju;
- stan Git każdego repo;
- rekomendację, czy `moves` może stać się nowym głównym repozytorium.

Nie wykonuj push ani nie archiwizuj repo źródłowych.