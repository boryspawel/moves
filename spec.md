SPECYFIKACJA TECHNICZNA
I ARCHITEKTURA

Ekosystem Ruchu

Silnik Treningowy • Aplikacja Uczestnika • Panel Specjalisty • Gamifikacja opt-in

Decyzja bazowa: Java 25 + Spring Boot 4, Angular 22 oraz Ionic Angular + Capacitor 8 dla aplikacji mobilnej uczestnika. Start jako modularny monolit z gotowością do kontrolowanego wydzielania usług.

Stan na 20 lipca 2026 r.  •  dokument do walidacji przed rozpoczęciem implementacji


EKOSYSTEM RUCHU  |  SPECYFIKACJA TECHNICZNA

1. Cel, status i decyzje architektoniczne

Cel dokumentu. Zdefiniowanie spójnej architektury produktu, granic domenowych, stosu technologicznego, modelu danych, bezpieczeństwa oraz kolejności wdrożenia. Dokument stanowi punkt odniesienia dla backlogu, wycen, umów z wykonawcami i przeglądów technicznych.

Rekomendacja: budować najpierw dobrze rozdzielony modularny monolit. Mikroserwisy, Kafka i Kubernetes nie są wymaganiem MVP; mogą zostać wprowadzone wyłącznie po pojawieniu się mierzalnej potrzeby skalowania lub niezależnych zespołów.

Najważniejsze decyzje

Obszar

Decyzja

Uzasadnienie

Architektura

Modularny monolit + DDD/hexagonal

Mniejszy koszt operacyjny, transakcje w jednym rdzeniu, jasne granice i łatwiejszy audyt.

Backend

Java 25 LTS, Spring Boot 4.1.x

Dojrzały ekosystem, bezpieczeństwo, utrzymanie i zgodność z preferowanym stosem.

Web

Angular 22 + TypeScript 6

Jeden standard dla paneli, silne typowanie i dobra organizacja dużej aplikacji.

Mobile

Ionic Angular + Capacitor 8

Wspólne kompetencje Angular, natywna dystrybucja i szybszy start na iOS/Android.

API

REST/JSON + OpenAPI

Czytelne kontrakty, generowanie klientów i prostsza integracja niż GraphQL w MVP.

Dane

PostgreSQL + storage obiektowy

Relacyjna spójność danych domenowych i oddzielenie mediów/plików.

Tożsamość

Keycloak, OIDC/OAuth 2.1, PKCE

Centralne logowanie, MFA, standardowe tokeny i niezależność aplikacji.

Integracje

Zdarzenia wewnętrzne + transactional outbox

Niezawodność bez kosztu brokera; możliwość późniejszego dołączenia kolejki.

Zasada przewodnia

Silnik Treningowy jest źródłem prawdy dla ćwiczeń, planów, wykonania, progresji i bezpieczeństwa.

Interfejsy uczestnika i specjalisty są oddzielne, lecz korzystają ze wspólnych kontraktów API.

Gamifikacja otrzymuje wyłącznie zdarzenia dopuszczone do punktacji; nie odczytuje notatek medycznych.

RuszSię pozostaje poza zakresem początkowego wdrożenia i otrzyma osobny kontekst domenowy.

2. Zakres systemu i nomenklatura

Role i aplikacje

Nazwa

Definicja techniczna

Uczestnik

Osoba ćwicząca: pacjent, klient lub zawodnik. Kontekst zależy od relacji ze specjalistą.

Specjalista

Trener albo fizjoterapeuta. Uprawnienia wynikają z roli, relacji, organizacji i aktualnych zgód.

Aplikacja Uczestnika

Aplikacja mobilna i PWA: plan, wykonanie, postęp, dane, kalendarz, specjaliści i zgody.

Panel Specjalisty

Responsywna aplikacja webowa: klienci, plany, alerty, kalendarz, notatki i status płatności.

Silnik Treningowy

Moduły domenowe katalogu ćwiczeń, planowania, sesji, wykonania, bezpieczeństwa i alertów.

Paszport Ruchu

Część profilu uczestnika: dane, cele, postęp, relacje i zgody — nie osobna aplikacja.

Gra Mateusza

Dobrowolny moduł gamifikacji i społeczności: punkty, rankingi, drużyny, wyzwania.

RuszSię

Przyszły moduł dopasowania 1:1 do aktywności; ostatni etap roadmapy.

Hierarchia treningu

CEL  →  CYKL  →  MIKROCYKL  →  PLANOWANA SESJA  →  ZESTAW ĆWICZEŃ  →  WYKONANIE

Ćwiczenie jest wersjonowaną pozycją katalogu, nie jednorazowym wpisem w planie.

Zestaw ćwiczeń zawiera konkretne parametry: serie, powtórzenia, czas, intensywność, przerwy i kolejność.

Sesja samodzielna oznacza zaplanowane wykonanie poza spotkaniem ze specjalistą.

Spotkanie offline jest terminem w kalendarzu i może obejmować trening, terapię albo ocenę; nie jest tym samym co wykonanie sesji samodzielnej.

Zakres MVP

konto, profil, relacja uczestnik–specjalista, zgody i kontrola widoczności;

katalog ćwiczeń, plan, przydział, harmonogram i deklaracja wykonania;

ból, trudność, alerty, zaległości, postęp i podstawowe raporty;

kalendarz specjalisty, spotkania offline, notatki/wywiad i ręczny status płatności;

powiadomienia e-mail/push oraz operacyjny panel administracyjny.

3. Kontekst systemu i kanały dostępu

Rysunek 1. Docelowy kontekst systemu



Kanały

Kanał

Użytkownik

Pierwsza wersja

Mobile uczestnika

pacjent / klient / zawodnik

iOS i Android w Ionic Angular + Capacitor; PWA jako kanał pomocniczy

Web specjalisty

trener / fizjoterapeuta

responsywny Angular; desktop-first, tablet wspierany

Web administracyjny

operator / administrator organizacji

Angular, oddzielne route’y i role

API partnerskie

zatwierdzone integracje

brak publicznego API w MVP; kontrakty wewnętrzne OpenAPI

Granica zewnętrzna: aplikacje nigdy nie łączą się bezpośrednio z bazą danych ani storage. Każda operacja przechodzi przez API, autoryzację domenową, walidację i audyt.

Środowiska

lokalne — Docker Compose, dane syntetyczne, Mailpit i emulatory usług;

development — automatyczne wdrożenie gałęzi głównej;

staging — konfiguracja możliwie zgodna z produkcją, testy akceptacyjne i bezpieczeństwa;

production — dane rzeczywiste, kontrola dostępu, backup, monitoring, alertowanie i plan odtworzenia.

4. Architektura backendu

Wzorzec: modularny monolit oparty o granice domenowe, porty i adaptery oraz weryfikację zależności modułów. Każdy moduł ma własny model, API aplikacyjne i repozytoria; inne moduły nie odwołują się bezpośrednio do jego tabel.

Struktura kodu

backend/
application/          uruchomienie Spring Boot
modules/
training-catalog/   ćwiczenia i wersje
training-plan/      cykle, mikrocykle, sesje
execution/          wykonanie i wyniki
safety/             reguły i alerty
participant/        profil i postęp
specialist/         relacje, notatki, wywiad
consent/            zgody i widoczność
calendar/           terminy i dostępność
gamification/       punkty, rankingi, drużyny
notification/       e-mail, push, in-app
shared-kernel/        wyłącznie stabilne typy techniczne

Warstwy pojedynczego modułu

Warstwa

Odpowiedzialność

Zakazy

domain

encje/agregaty, value objects, reguły i zdarzenia domenowe

brak Spring MVC, JPA repository i integracji sieciowych

application

przypadki użycia, transakcje, autoryzacja zasobowa, orkiestracja

brak logiki UI i bezpośrednich zapytań do obcych tabel

infrastructure

JPA, storage, e-mail, push, adaptery zewnętrzne

brak reguł biznesowych ukrytych w adapterach

api

REST DTO, walidacja wejścia, mapowanie błędów i OpenAPI

brak zwracania encji JPA i danych poza zakresem zgody

Technologie backendowe

Java 25 LTS, Spring Boot 4.1.x, Spring Framework 7 i Spring Modulith;

Spring MVC, Spring Security, Spring Data JPA/Hibernate, Bean Validation i Flyway;

Maven multi-module, ArchUnit/Modulith do kontroli zależności;

Testcontainers do testów z PostgreSQL i usługami zależnymi;

Micrometer + OpenTelemetry do metryk, logów skorelowanych i śledzenia żądań.

5. Katalog modułów domenowych

Rysunek 2. Granice modułów oraz dozwolony przepływ do gamifikacji



Moduł

Główna odpowiedzialność

Identity & Access

konto, sesje, role techniczne, organizacje i mapowanie podmiotu z Keycloak

Participant / Passport

profil uczestnika, cele, własne dane, postęp i ustawienia widoczności

Specialist & Relationship

profil specjalisty, aktywność, relacje z uczestnikami, zakres pracy

Exercise Catalog

kanoniczne ćwiczenia, wersje, instrukcje, warianty i ograniczenia

Training Planning

plany, cykle, mikrocykle, zestawy, parametry i przydziały

Session & Execution

terminy samodzielne, deklaracje wykonania, rezultaty i korekty

Safety & Alerts

reguły obciążenia, ból/trudność, blokady, ostrzeżenia i eskalacje

Calendar & Appointment

dostępność specjalisty, rezerwacje i spotkania offline

Consent & Data Sharing

wersjonowane zgody, cel przetwarzania, odbiorca i ważność

Notes & Interview

notatki robocze, wywiad i dane o podwyższonej wrażliwości

Billing Status

informacyjny status należności; bez obsługi kart w MVP

Notification

szablony, preferencje, push/e-mail/in-app i historia dostarczeń

Gamification & Teams

ledger punktów, rankingi, drużyny, członkostwo i wyzwania

Audit & Reporting

niezmienny ślad audytowy, raporty operacyjne i agregaty sponsorskie

6. Model danych i niezmienniki

Kluczowe encje

Kontekst

Encje / agregaty

Tożsamość i relacje

UserAccount, ParticipantProfile, SpecialistProfile, Organization, ParticipantSpecialistRelationship

Zgody

ConsentGrant, ConsentTemplateVersion, DataSharingPolicy, ConsentAuditEvent

Katalog

Exercise, ExerciseVersion, ExerciseMedia, ExerciseConstraint, ExerciseTag

Planowanie

TrainingPlan, TrainingCycle, Microcycle, PlannedSession, ExercisePrescription

Wykonanie

SessionExecution, ExerciseResult, PainDifficultyReport, ExecutionCorrection

Bezpieczeństwo

SafetyRuleVersion, SafetyAssessment, SafetyAlert, SpecialistOverride

Kalendarz

SpecialistAvailability, Appointment, AppointmentParticipant

Dokumentacja

Interview, ClinicalNote, Goal, ProgressMeasurement, PaymentStatus

Gamifikacja

PointLedgerEntry, RankingProjection, Team, TeamMembership, Challenge, Achievement

Techniczne

Notification, OutboxEvent, IdempotencyKey, AuditEvent

Niezmienniki biznesowe

Wersja ćwiczenia jest niezmienna po użyciu w planie; edycja tworzy nową wersję.

Plan i wykonanie zawsze wskazują konkretną wersję ćwiczenia oraz reguł bezpieczeństwa.

Wykonanie jest zapisem append-only; korekta tworzy wpis korygujący i ślad audytu, nie nadpisuje historii.

Deklarowane wykonanie jest jawnie oznaczone jako deklaracja użytkownika, a nie pomiar automatyczny.

Zgoda określa odbiorcę, zakres danych, cel, podstawę, wersję treści i okres ważności.

Ranking jest projekcją ledgeru punktów; nie stanowi źródła prawdy i można go odbudować.

Usunięcie relacji ze specjalistą natychmiast odbiera dostęp do danych, z wyjątkiem prawnie wymaganej retencji.

Identyfikatory i czas

UUIDv7/ULID dla encji domenowych; identyfikator Keycloak przechowywany jako referencja zewnętrzna.

Czas w bazie w UTC; strefa użytkownika zapisana oddzielnie; kalendarz wyświetlany lokalnie.

Każda komenda podatna na ponowienie przyjmuje klucz idempotencji.

7. Silnik bezpieczeństwa i alertów

Zakres odpowiedzialności: system nie diagnozuje i nie zastępuje specjalisty. Sprawdza plan oraz wykonanie względem jawnych, wersjonowanych reguł i przekazuje człowiekowi sygnały wymagające decyzji.

Przepływ oceny

PLAN / ZMIANA PLANU  →  WALIDACJA REGUŁ  →  BLOKADA lub OSTRZEŻENIE  →  ZAPIS DECYZJI
WYKONANIE  →  BÓL / TRUDNOŚĆ / ZALEGŁOŚĆ  →  ALERT  →  TRIAGE SPECJALISTY

Poziom

Zachowanie systemu

Przykład

HARD_BLOCK

operacja niedozwolona; brak możliwości punktacji

niedopuszczalna kombinacja obciążeń lub przekroczony twardy limit

WARNING

wymaga potwierdzenia; specjalista może uzasadnić decyzję

szybsza niż zalecana progresja lub nietypowa objętość

ALERT

sesja może być zapisana, lecz specjalista otrzymuje zadanie do przeglądu

zgłoszony ból, wysoka trudność, seria niewykonanych sesji

INFO

informacja do obserwacji bez blokowania

pojedyncze opóźnienie lub łagodna zmiana trendu

Wymagania implementacyjne

reguły są danymi wersjonowanymi z datą obowiązywania, autorem i uzasadnieniem;

wynik walidacji zapisuje wejście, wersję reguł, rezultat i decyzję użytkownika;

override jest dostępny tylko uprawnionemu specjaliście, wymaga powodu i trafia do audytu;

alert ma właściciela, priorytet, status, SLA operacyjne oraz historię komentarzy i zamknięcia;

punkty są naliczane dopiero po pozytywnej kwalifikacji zdarzenia wykonania.

Kontrola jakości reguł

testy tabelaryczne dla kombinacji ćwiczeń, parametrów i profili ograniczeń;

tryb symulacji przed publikacją nowej wersji reguł;

raport fałszywych blokad/alertów i możliwość bezpiecznego wycofania wersji.

8. Gamifikacja i społeczność opt-in

Gra Mateusza nie jest grą mobilną. Jest dobrowolnym mechanizmem motywacyjnym opartym o deklarowane wykonanie zestawów ćwiczeń, rankingi, drużyny i późniejsze wyzwania. Może działać pod pseudonimem i nie wpływa na decyzje terapeutyczne.

Ledger punktów

Każdy wpis zawiera źródłowe zdarzenie, regułę punktową, wersję, wartość, czas i przyczynę.

Wpisów nie edytuje się; korekty mają wartość przeciwną i wskazują wpis korygowany.

Rankingi, rekordy i osiągnięcia są projekcjami możliwymi do odbudowania.

Zabezpieczenia przeciw nabijaniu punktów

Mechanizm

Zasada

Idempotencja

jedno źródłowe wykonanie może wygenerować najwyżej jeden wpis danego typu

Limity

dobowe/tygodniowe limity punktów i malejący zwrot dla powtarzalnych aktywności

Cooldown

minimalny odstęp między kwalifikującymi się wykonaniami

Dopuszczalność

brak punktów za plan zablokowany lub wykonanie niezgodne z zasadami bezpieczeństwa

Anomalie

oznaczanie nietypowych wzorców do przeglądu; bez automatycznego oskarżania użytkownika

Prywatność

pseudonim, osobna zgoda na ranking i możliwość natychmiastowego wyłączenia widoczności

Rozwój funkcji

etap 1: punkty, prywatny postęp, ranking opt-in i podstawowe drużyny;

etap 2: zapraszanie, wyzwania, osiągnięcia i rekordy;

etap 3: wieloboje i rywalizacja drużynowa, nadal podporządkowane limitom bezpieczeństwa.

9. Frontend web i aplikacje mobilne

Angular web

Angular 22, TypeScript 6, Angular Material/CDK oraz wspólny design system;

standalone components, lazy routes, signals dla stanu lokalnego i RxJS dla strumieni asynchronicznych;

wygenerowany z OpenAPI klient TypeScript; brak ręcznego duplikowania typów backendowych;

route guards są wygodą UX, lecz ostateczna autoryzacja zawsze należy do backendu;

WCAG 2.2 AA jako cel, pełna obsługa klawiatury i responsywność panelu specjalisty.

Rekomendacja mobilna

Ionic Angular + Capacitor 8: pierwsza aplikacja mobilna uczestnika powinna współdzielić komponenty, kompetencje Angular, design tokeny i wygenerowany klient API. Capacitor zapewnia natywne opakowanie, dostęp do push, secure storage, deep linków i aparatu.

Opcja

Ocena dla projektu

Kiedy wybrać

Ionic Angular + Capacitor

REKOMENDOWANA — najmniejszy koszt wejścia i jeden język frontendowy

MVP i pierwsze etapy; formularze, kalendarz, plan, postęp, push

Kotlin Multiplatform + natywny UI

wariant rozwojowy — większy koszt, bardzo dobry dostęp do platform

gdy wearables, BLE, HealthKit/Health Connect i praca w tle staną się rdzeniem

Flutter

technicznie dobry, ale wprowadza Dart i drugi system UI

gdy powstanie osobny zespół mobilny lub UI wymaga ciężkich animacji

React Native

dojrzały, lecz wprowadza React obok Angulara

gdy organizacja ma silne kompetencje React Native

Dwie aplikacje natywne

najwyższa kontrola i najwyższy koszt

dopiero przy krytycznych integracjach platformowych i odpowiedniej skali zespołu

Offline i urządzenia

MVP: cache odczytu planu i bezpieczna kolejka deklaracji wykonania do późniejszej synchronizacji.

Brak pełnego offline-first dla notatek medycznych i kalendarza; konflikty rozstrzyga serwer.

Wearables, HealthKit, Health Connect i BLE pozostają poza MVP; wymagają osobnej oceny prawnej i technicznej.

10. API, integracje i zdarzenia

Standard API

REST/JSON pod /api/v1; kontrakt OpenAPI przechowywany i wersjonowany razem z kodem;

DTO oddzielone od encji domenowych; jednolity format błędu zgodny z Problem Details;

paginacja kursorowa dla długich list i filtrowanie po dozwolonych polach;

ETag/wersja rekordu dla operacji narażonych na konflikt;

Idempotency-Key dla wykonania sesji, rezerwacji i naliczania punktów;

SSE lub WebSocket dopiero dla uzasadnionych powiadomień czasu rzeczywistego.

Zdarzenia domenowe

Zdarzenie

Główni odbiorcy

ExerciseSetAssigned

powiadomienia, kalendarz, raport operacyjny

SessionDue / SessionOverdue

powiadomienia, dashboard specjalisty

SessionCompletedDeclared

postęp, bezpieczeństwo, kwalifikacja do punktów

PainReported / DifficultyReported

bezpieczeństwo, alerty, dashboard specjalisty

SafetyAlertRaised

powiadomienia specjalisty, audyt, SLA operacyjne

AppointmentBooked / Cancelled

kalendarz, powiadomienia

ConsentGranted / Revoked

autoryzacja zasobowa, audyt, czyszczenie cache

PointsAwarded / Reversed

ranking, profil gry, analityka gamifikacji

Niezawodność: zdarzenie wychodzące jest zapisywane w tej samej transakcji co zmiana domenowa (transactional outbox). Publikator ponawia dostarczenie, a odbiorcy są idempotentni.

Integracje w kolejnych etapach

e-mail/SMS/push, eksport kalendarza, płatności przez zewnętrznego PSP, systemy klubów/placówek;

broker RabbitMQ/Kafka dopiero przy niezależnych usługach, dużym wolumenie lub wielu konsumentach;

publiczne API wyłącznie z osobnym modelem uprawnień, limitami, audytem i umowami przetwarzania.

11. Tożsamość, autoryzacja i zgody

Uwierzytelnianie

Keycloak jako dostawca tożsamości; Authorization Code + PKCE dla SPA i mobile;

krótkie tokeny dostępu, rotowane refresh tokeny i bezpieczne przechowywanie po stronie mobilnej;

MFA wymagane dla specjalistów i administratorów; możliwość passkeys w dalszym etapie;

backend jest OAuth2 Resource Server i waliduje issuer, audience, podpis oraz ważność tokenu.

Model uprawnień

Warstwa

Przykład

RBAC

PARTICIPANT, SPECIALIST, ORG_ADMIN, SYSTEM_ADMIN

Relacja

specjalista ma aktywną relację z konkretnym uczestnikiem

Zakres zgody

uczestnik udostępnił cele i postęp, ale nie notatki/wywiad

Atrybut zasobu

notatka należy do organizacji i autora, alert ma przypisanego opiekuna

Cel operacji

odczyt terapeutyczny, obsługa rezerwacji, raport zagregowany

Ważne: role z tokenu nie wystarczą. Każdy przypadek użycia sprawdza relację, organizację, zakres zgody, właściciela zasobu i cel operacji. Cofnięcie zgody musi zacząć działać natychmiast.

Sesje i urządzenia

lista aktywnych urządzeń i możliwość zdalnego zakończenia sesji;

ochrona przed brute force, rate limiting i wykrywanie nietypowych logowań;

osobne klienty OIDC dla web, mobile i administracji; brak wspólnego sekretu w aplikacji klienckiej.

12. Prywatność, dane medyczne i audyt

Klasy danych

Klasa

Przykłady

Zasady

Tożsamościowe

imię, e-mail, identyfikatory konta

minimalizacja, szyfrowanie, kontrola administracyjna

Operacyjne/treningowe

plan, wykonanie, postęp, kalendarz

dostęp wg relacji i celu, retencja zgodna z usługą

Medyczne/szczególne

ból, wywiad, notatki fizjoterapeutyczne

najściślejsze uprawnienia, audyt odczytu, osobne zakresy zgód

Społecznościowe

pseudonim, drużyna, punkty, ranking

wyłącznie opt-in, brak danych medycznych i dokładnej lokalizacji

Zagregowane

metryki produktu i programu sponsorskiego

progi minimalnej grupy, brak możliwości identyfikacji osoby

Kontrole techniczne

TLS wszędzie, szyfrowanie dysków i kopii zapasowych; dla najbardziej wrażliwych pól możliwe szyfrowanie aplikacyjne;

niezmienny audyt logowania, udostępnienia, odczytu danych wrażliwych, zmian zgód, override i operacji administracyjnych;

eksport danych użytkownika, proces korekty, ograniczenia przetwarzania i usunięcia zgodnego z retencją;

sekrety w managerze sekretów, rotacja kluczy i zakaz logowania tokenów lub treści notatek;

DPIA, rejestr czynności, umowy powierzenia i formalna walidacja prawna przed produkcją.

Płatności

MVP przechowuje wyłącznie ręczny status należności: nieopłacona/opłacona/zwolniona/sporna.

Późniejsza integracja korzysta z PSP; system nie przechowuje numerów kart ani danych uwierzytelniających płatność.

13. Przechowywanie, wyszukiwanie i retencja

Technologia

Zastosowanie

Uwagi

PostgreSQL 18

dane domenowe, transakcje, outbox, audyt

źródło prawdy; migracje Flyway; indeksy mierzone zapytaniami

S3 / MinIO

wideo instruktażowe, zdjęcia, załączniki

prywatne bucket’y, signed URL, skanowanie plików

Redis — opcjonalnie

cache, rate limiting, krótkie blokady

nigdy źródło prawdy; system działa poprawnie po utracie cache

PostgreSQL FTS

wyszukiwanie ćwiczeń i specjalistów

OpenSearch dopiero po wykazaniu ograniczeń

Object backup

kopie mediów i dokumentów

wersjonowanie, retencja i testy odtworzenia

Zasady modelowania

relacyjne tabele dla kluczowych agregatów; JSONB tylko dla faktycznie zmiennych, wersjonowanych metadanych;

osobne schematy lub prefiksy tabel per moduł oraz zakaz FK do tabel innych modułów bez decyzji ADR;

soft delete nie jest domyślnym rozwiązaniem — wybór zależy od audytu, retencji i prawa do usunięcia;

backup produkcji szyfrowany, codzienny; regularne testy restore i udokumentowane RPO/RTO.

Cele ciągłości

początkowy cel: RPO ≤ 24 h i RTO ≤ 8 h; przed skalowaniem doprecyzować wg umów i ryzyka;

procedury utraty dostawcy push/e-mail nie mogą blokować zapisu planu ani wykonania;

storage mediów jest oddzielony od krytycznej transakcji przypisania planu.

14. Wdrożenie, obserwowalność i operacje

Topologia początkowa

statyczny Angular przez CDN/hosting obiektowy;

2–3 repliki aplikacji Spring Boot za load balancerem; aplikacja bezstanowa;

zarządzany PostgreSQL z backupem i możliwością point-in-time recovery;

Keycloak w konfiguracji wysokiej dostępności adekwatnej do środowiska;

storage S3, usługi APNs/FCM i dostawca e-mail;

Kubernetes dopiero, gdy organizacja ma kompetencje i realną potrzebę automatycznej orkiestracji.

Obserwowalność

Sygnał

Minimum

Metryki

latencja i błędy API, kolejka outbox, logowania, alerty bezpieczeństwa, push/e-mail, pula DB

Logi

strukturalne JSON, correlation/trace ID, brak danych medycznych i tokenów

Tracing

OpenTelemetry między API, bazą i integracjami zewnętrznymi

Frontend

błędy JavaScript, wersja aplikacji, urządzenie; bez treści formularzy wrażliwych

Alerty

SLO, wzrost błędów, brak publikacji outbox, opóźnione alerty bólu, problemy backupu

Wstępne SLO

dostępność API 99,5% miesięcznie w pilotażu;

p95 odczytu typowego widoku < 500 ms po stronie backendu;

95% krytycznych alertów widocznych w panelu do 60 sekund od zapisu;

100% produkcyjnych migracji bazy wykonanych automatycznie z kontrolą kompatybilności.

15. Jakość, testy i CI/CD

Poziom

Zakres

Testy domenowe

reguły planu, wersjonowanie ćwiczeń, limity bezpieczeństwa, ledger punktów

Testy modułów

granice Spring Modulith/ArchUnit, zdarzenia, transakcje i autoryzacja zasobowa

Integracyjne

PostgreSQL/Testcontainers, Flyway, Keycloak, storage, outbox i idempotencja

Kontraktowe

walidacja OpenAPI, kompatybilność klienta Angular i scenariusze błędów

Frontend

unit/component, dostępność, Playwright E2E dla ścieżek krytycznych

Mobile

smoke na iOS/Android, push, deep links, offline queue, aktualizacje aplikacji

Bezpieczeństwo

SAST, dependency/container scan, secret scan, DAST i testy uprawnień

Odporność

restore backupu, awaria push/e-mail, ponowienia outbox i konflikt synchronizacji

Pipeline

COMMIT → FORMAT/LINT → UNIT → MODUŁY → INTEGRACJA → BUILD → SKANY → DEPLOY DEV
TAG RELEASE → TESTY E2E/STAGING → AKCEPTACJA → MIGRACJA → PRODUKCJA → SMOKE

trunk-based development z krótkimi gałęziami i obowiązkowym przeglądem kodu;

SemVer dla API/aplikacji, wersja builda widoczna w panelu i logach;

feature flags dla gamifikacji, nowych reguł bezpieczeństwa i funkcji pilotażowych;

migracje forward-compatible; rollback aplikacji nie może wymagać destrukcyjnego cofania bazy.

Definition of Done dla funkcji wrażliwej

testy pozytywne i negatywne uprawnień, zgód oraz audytu;

ocena prywatności i model zagrożeń;

metryki, logowanie bez danych wrażliwych, instrukcja operacyjna i plan wycofania.

16. Roadmapa techniczna i granice MVP

Etap

Rezultat techniczny

Warunek przejścia

0. Fundament

repozytorium, CI/CD, Keycloak, moduły, baza, audyt i środowiska

zielony pipeline, model zagrożeń, ADR-y bazowe

1. Rdzeń

katalog, plan, sesja, wykonanie, podstawowe bezpieczeństwo

zamknięta ścieżka przypisanie → wykonanie → postęp

2. Aplikacje rolowe

mobile uczestnika, panel specjalisty, kalendarz, alerty, zgody, notatki

pilotaż z rzeczywistymi rolami i obsługą zgód

3. Gamifikacja

opt-in, ledger, ranking, drużyny i anty-farming

stabilny rdzeń bezpieczeństwa i moderacja operacyjna

4. Skalowanie

płatności PSP, integracje, zaawansowana analityka, ewentualne wydzielenia usług

dane o wolumenie, kosztach i ograniczeniach monolitu

5. RuszSię

dopasowanie 1:1 i ActivitySession w osobnym kontekście

udowodnione użycie rdzenia oraz osobna analiza bezpieczeństwa

Świadomie poza MVP

pobieranie płatności i przechowywanie instrumentów płatniczych;

rekomendacje ML, diagnozowanie, publiczne oceny specjalistów i marketplace;

wearables, HealthKit/Health Connect, BLE oraz pełna praca offline;

grupy aktywności RuszSię, trenerzy marketplace, czat czasu rzeczywistego;

mikroserwisy, Kafka, Kubernetes i OpenSearch bez potwierdzonego uzasadnienia.

Kryteria wydzielenia usługi

moduł wymaga niezależnego skalowania lub innego profilu zasobów;

ma stabilny kontrakt, własność jednego zespołu i może posiadać własne dane;

koszt operacyjny wydzielenia jest mniejszy niż zidentyfikowany koszt pozostania w monolicie;

migracja ma plan spójności, obserwowalności, awarii i wycofania.

17. Ryzyka techniczne i decyzje do zatwierdzenia

Ryzyko

Wpływ

Odpowiedź

Niejednoznaczne dane medyczne

wysoki

warsztaty prawne i domenowe, klasyfikacja danych, osobne zgody i audyt

Reguły bezpieczeństwa bez właściciela

wysoki

rada merytoryczna, wersjonowanie, tryb symulacji i odpowiedzialność za publikację

Nadużycia gamifikacji

średni/wysoki

ledger, idempotencja, limity, cooldown, anomalie i moderacja

Zbyt wczesne mikroserwisy

wysoki

modularny monolit i formalne kryteria wydzielenia

Dług mobilny

średni

Capacitor z adapterami natywnymi i okresowy przegląd progów migracji

Nadmierna liczba funkcji

wysoki

twarda granica MVP, feature flags i mierniki przejścia między etapami

Vendor lock-in

średni

standardy OIDC/OpenAPI/S3, adaptery i eksport danych

Brak jakości danych katalogu

wysoki

workflow publikacji, wersje, recenzja merytoryczna i testy reguł

Decyzje wymagające zatwierdzenia przed sprintem 1

model organizacji: indywidualny specjalista, gabinet/klub czy oba warianty;

właściciel merytoryczny katalogu oraz reguł bezpieczeństwa;

zakres notatek i wywiadu w MVP oraz wymagane okresy retencji;

model zapraszania uczestnika i ustanawiania relacji ze specjalistą;

czy rezerwacja terminu wymaga akceptacji specjalisty;

minimalny zakres offline w aplikacji mobilnej i wsparcie wersji iOS/Android;

SLO, RPO/RTO oraz docelowy dostawca infrastruktury.

18. Rejestr decyzji i źródła technologiczne

ADR — decyzje startowe

ADR

Decyzja

Status

ADR-001

Modularny monolit zamiast mikroserwisów na start

przyjęta

ADR-002

REST/OpenAPI zamiast GraphQL w MVP

przyjęta

ADR-003

Ionic Angular + Capacitor dla aplikacji uczestnika

przyjęta do walidacji prototypem

ADR-004

PostgreSQL jako źródło prawdy; JSONB używany selektywnie

przyjęta

ADR-005

Keycloak + OIDC/PKCE; uprawnienia zasobowe w backendzie

przyjęta

ADR-006

Outbox i zdarzenia wewnętrzne; broker dopiero później

przyjęta

ADR-007

Gamifikacja odseparowana od danych medycznych

przyjęta

ADR-008

RuszSię poza rdzeniem i na końcu roadmapy

przyjęta

Źródła oficjalne — baseline wersji

Spring Boot 4.1.0 — System Requirements — Java 17 minimum; wybór projektu: Java 25 LTS

Spring Framework 7 — Reference Documentation — główna dokumentacja frameworka

Spring Modulith — Reference Documentation — granice i testowanie modułów

Angular — Version compatibility — Angular 22 i zgodne wersje TypeScript/Node

Capacitor — Documentation — natywny runtime dla iOS i Android

Capacitor — Support policy — baseline Capacitor 8

Capacitor Push Notifications — APNs/FCM i wymagania platformowe

Keycloak Documentation — OIDC, administracja i wdrożenie

PostgreSQL 18 Documentation — relacyjna baza danych

Status materiałów wejściowych

Prospekt Ekosystemu Ruchu — źródło bieżącego zakresu biznesowego i kolejności rozwoju.

nowa_aplikacja.md — materiał rynkowy pomocniczy; nie jest specyfikacją systemu.

ARCHITECTURE.md opisujący „power-rpg” i architekturę gry — materiał historyczny, nieobowiązujący i niezgodny z bieżącą domeną; nie używać do implementacji.

Utrzymanie dokumentu: baseline bibliotek, systemów operacyjnych i wymagań sklepów mobilnych należy weryfikować co kwartał. Każda istotna zmiana architektury wymaga ADR, właściciela decyzji i planu migracji.