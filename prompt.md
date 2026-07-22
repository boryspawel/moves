Prompt 1 — kontrakt i maszyna stanów
ROLA

Działaj jako senior full-stack engineer projektu moves. Twoim zadaniem jest
ustabilizowanie kontraktu i modelu stanu onboardingu przed przebudową wizualną.

Nie wykonuj jeszcze finalnego redesignu.

ZASADY PRACY

1. Przeczytaj AGENTS.md, instrukcje repozytorium i właściwe skillsy.
2. Sprawdź branch, HEAD oraz początkowy git status.
3. Zachowaj wszystkie zastane zmiany użytkownika.
4. Korzystaj przede wszystkim z MCP IntelliJ i dostępnych skillsów.
5. Przed każdym Mavenem lub Javą uruchamianą na hoście aktywuj Java 25 przez
   SDKMAN w tej samej powłoce.
6. Nie wykonuj commita ani push.
7. Nie edytuj ręcznie plików wygenerowanego klienta OpenAPI.
8. Odświeżaj OpenAPI i klienta tylko istniejącym workflow repozytorium.

KONTEKST AS-IS

Obecny web/src/app/features/onboarding.page.ts:

- renderuje kilka niezależnych kart równocześnie;
- używa jednego ogólnego signal failed/message dla ładowania i wszystkich akcji;
- przy błędzie początkowego GET state pozostawia state=null, ale nadal renderuje
  formularze;
- pokazuje techniczne wartości stage i missingSteps;
- participantProfile wysyła displayName bez timeZoneId;
- dostępność pozwala wybrać tylko część dni tygodnia;
- błędy są redukowane do jednego ogólnego komunikatu.

Backendowy OnboardingService wyznacza kolejność:

1. PROFILE_TYPE_REQUIRED
2. LEGAL_REQUIRED
3. PROFILE_REQUIRED
4. AVAILABILITY_REQUIRED
5. READY

Nie twórz drugiej, niezależnej maszyny prawdy po stronie Angulara. State zwracany
przez backend ma pozostać źródłem prawdy.

CEL

Przygotuj stabilny kontener onboardingu, na którym można bezpiecznie zbudować
docelowy interfejs krokowy.

ZAKRES

1. Audyt kontraktu

Sprawdź:

- OnboardingController;
- OnboardingService;
- API State i wszystkie request DTO;
- OnboardingControllerApi;
- AuthService i routing;
- istniejące testy;
- rzeczywiste zachowanie runtime.

Ustal, czy profile type może zostać później zmieniony. Nie dodawaj komunikatu
„wybór jest nieodwracalny”, jeśli kod domenowy tego nie gwarantuje.

2. Jawny model stanu UI

Rozdziel co najmniej:

- initial loading;
- initial load error;
- loaded;
- stage submission;
- stage submission error;
- ready.

Wymagania:

- podczas initial loading nie renderuj formularzy;
- podczas initial load error pokaż tylko kontrolowany komunikat i „Spróbuj ponownie”;
- nie pokazuj wtedy „Brakujące kroki: brak”;
- akcja jednego kroku nie może blokować lub zerować pozostałego stanu;
- zachowuj wpisane wartości po błędzie;
- po sukcesie wykorzystuj State zwrócony przez API;
- zabezpiecz się przed wielokrotnym wysłaniem formularza;
- nie pokazuj surowego body błędu ani wyjątku backendu.

Nie używaj MatStepper jako maszyny stanu. Backendowy stage jest źródłem prawdy.

3. Mapowanie etapu

Dodaj jedno centralne, typowane mapowanie:

- PROFILE_TYPE_REQUIRED -> profile type;
- LEGAL_REQUIRED -> legal;
- PROFILE_REQUIRED -> basic profile;
- AVAILABILITY_REQUIRED -> availability;
- READY -> complete.

Usuń prezentowanie surowych enumów backendowych użytkownikowi.

4. Strefa czasowa

Napraw kontrakt uczestnika:

- participantProfile ma wysyłać timeZoneId;
- użyj Intl.DateTimeFormat().resolvedOptions().timeZone jako bezpiecznej wartości
  początkowej;
- nie hardcoduj Europe/Warsaw jako wartości dla wszystkich użytkowników;
- przygotuj typowany helper lub service możliwy do ponownego użycia;
- dodaj test dla braku i poprawnego wykrycia strefy;
- nie edytuj klienta OpenAPI ręcznie.

Zweryfikuj również, czy backend powinien wymagać niepustego timeZoneId w request
validation. Nie zmieniaj kontraktu bez testów kompatybilności.

5. Dokumenty prawne

Sprawdź, czy repozytorium posiada rzeczywiste treści albo URL regulaminu i
informacji o prywatności.

Nie twórz:

- fikcyjnych treści;
- pustych stron;
- tymczasowych URL wyglądających jak produkcyjne;
- linków do nieistniejących zasobów.

Jeśli źródło dokumentów nie istnieje, opisz to jako
BLOCKED_LEGAL_DOCUMENT_CONTENT. Nie blokuj z tego powodu pozostałych prac.

Jeżeli istnieje prawdziwe źródło, zaprojektuj minimalną publiczną projekcję
metadanych dokumentów: type, version, title, publicUrl. Nie wystawiaj ścieżek
serwera.

6. Testy

Dodaj testy Angulara dla:

- initial loading;
- initial load error;
- retry;
- poprawnego mapowania każdego stage;
- niepokazywania formularzy przy state=null;
- niezależnego błędu akcji;
- blokowania podwójnego submitu;
- wysłania participant timeZoneId;
- obsługi READY.

Dodaj lub popraw testy backendu, jeśli zmienisz walidację albo kontrakt.

7. Walidacja

Wykonaj:

- testy backendu związane z onboardingiem;
- testy Angulara;
- build Angulara;
- weryfikację OpenAPI, jeżeli kontrakt został zmieniony.

RAPORT

Podaj:

1. branch, HEAD i początkowy working tree;
2. diagnozę przyczyny błędnego ekranu;
3. rozpoznany kontrakt kolejności kroków;
4. decyzję dotyczącą timeZoneId;
5. wynik audytu dokumentów prawnych;
6. listę zmienionych plików;
7. wykonane testy;
8. końcowy git status --short;
9. ocenę READY FOR VISUAL REDESIGN albo NOT READY.

Nie wykonuj commita ani push.
Prompt 2 — docelowy layout i flow
ROLA

Działaj jako senior Angular engineer i implementator zatwierdzonego projektu UX
onboardingu w moves.

Zakładamy, że zadanie stabilizacji kontraktu i stanu zostało wcześniej wykonane.
Najpierw sprawdź stan as-is i potwierdź to założenie. Jeśli nie jest spełnione,
uzupełnij brakujące elementy zgodnie z istniejącą architekturą, bez tworzenia
obejść.

ZASADY REPOZYTORIUM

- przeczytaj AGENTS.md i właściwe skillsy;
- sprawdź branch, HEAD i working tree;
- zachowaj zastane zmiany;
- używaj MCP IntelliJ i skillsów;
- przed Mavenem/Javą aktywuj Java 25 przez SDKMAN;
- nie wykonuj commita ani push;
- nie edytuj ręcznie plików generowanych;
- nie zmieniaj backendu bez rzeczywistej potrzeby kontraktowej.

CEL

Zastąp obecny dashboard kart jednym responsywnym onboardingiem prowadzonym przez
backendowy State.

KOLEJNOŚĆ

1. Sposób korzystania
2. Dokumenty
3. Dane profilu
4. Dostępność
5. Gotowe

Backendowy stage pozostaje źródłem prawdy.

ARCHITEKTURA KOMPONENTÓW

Preferowany podział:

- OnboardingPage jako smart container;
- OnboardingProgressComponent;
- ProfileTypeStepComponent;
- LegalStepComponent;
- BasicProfileStepComponent;
- AvailabilityStepComponent;
- OnboardingCompleteComponent.

Komponenty kroków:

- standalone;
- ChangeDetectionStrategy.OnPush;
- otrzymują dane przez typed inputs;
- emitują typed outputs;
- nie wywołują API samodzielnie.

Nie twórz nadmiernie ogólnego frameworka wizardów.

LAYOUT DESKTOP

Główna zawartość:

- max-width około 1040px;
- wyśrodkowana;
- nagłówek „Skonfiguruj konto”;
- opis „Jeszcze kilka informacji i możesz zacząć”;
- dwie kolumny:
   - progress rail około 260px;
   - karta aktywnego kroku, maksymalnie około 640px.

Progress rail pokazuje:

- „Krok X z 4”;
- cztery etykiety kroków;
- status ukończony / bieżący / przyszły;
- aria-current="step" dla bieżącego;
- pasek lub tekstowy procent postępu;
- informację „Zapisujemy postęp po każdym kroku”.

Nie pozwalaj przechodzić bezpośrednio do przyszłych kroków. Ukończone elementy
nie muszą być linkami.

LAYOUT MOBILE

Dla szerokości poniżej około 720px:

- jedna kolumna;
- bez pionowego raila;
- „Krok X z 4” i poziomy pasek postępu nad kartą;
- pola i przycisk na całą szerokość;
- brak poziomego scrolla od 320px;
- minimum 44px dla interaktywnych celów.

SHELL

Na trasie nieukończonego onboardingu zastosuj tryb skupiony:

- logo;
- nazwa użytkownika, jeśli mieści się;
- wylogowanie;
- bez Katalogu, Importu, Sesji, Planu, Alertów i Punktów.

Po READY i przejściu do aplikacji przywróć normalną nawigację.

Nie opieraj tego wyłącznie na CSS ukrywającym dostępne linki. Zastosuj jawny stan
lub dane routingu.

KROK 1 — SPOSÓB KORZYSTANIA

Pokaż dwie wybieralne karty:

Uczestnik
„Chcę realizować treningi i korzystać z przygotowanych planów.”

Specjalista
„Chcę przygotowywać plany i pracować z uczestnikami.”

Wymagania:

- wybór dostępny klawiaturą;
- semantyka radio group albo równoważna;
- zaznaczenie nie wysyła od razu requestu;
- zapis następuje po „Kontynuuj”;
- wyraźny stan selected, hover, focus i disabled;
- nie używaj ikon wymagających nowych zewnętrznych assetów.

KROK 2 — DOKUMENTY

Docelowo pokaż osobno:

- „Akceptuję regulamin”;
- „Zapoznałem się z informacją o prywatności”.

Każda pozycja ma zawierać rzeczywisty link i wersję dokumentu, jeśli kontrakt je
udostępnia.

Nie wymyślaj treści ani URL. Jeśli nadal istnieje
BLOCKED_LEGAL_DOCUMENT_CONTENT:

- nie twórz fałszywych linków;
- wyraźnie opisz blocker w raporcie;
- nie oznaczaj tego kroku jako produkcyjnie akceptowalnego.

Główny przycisk:
„Akceptuję i kontynuuję”.

KROK 3 — DANE PROFILU

Uczestnik:

- Nazwa wyświetlana;
- Strefa czasowa.

Specjalista:

- Nazwa wyświetlana;
- Specjalizacja;
- dodatkowa strefa czasowa tylko wtedy, gdy aktualny kontrakt jej wymaga.

Dla strefy:

- wykryj wartość przez Intl;
- pokaż etykietę „Strefa czasowa”;
- pokaż helper „Wykryto automatycznie”;
- nie używaj etykiety „Strefa IANA”;
- nie pokazuj AM/PM w polskim interfejsie.

Główny przycisk:
„Zapisz i kontynuuj”.

Dodaj błędy pod właściwymi polami. Nie używaj wyłącznie ogólnego komunikatu.

KROK 4 — DOSTĘPNOŚĆ

Nagłówek:
„Ustaw typową dostępność”.

Opis ma być neutralny, dopóki dokumentacja domenowa nie potwierdzi odrębnego
znaczenia dla uczestnika i specjalisty.

Zaprojektuj repeatable list:

- dzień tygodnia;
- godzina od;
- godzina do;
- usuń przedział;
- „Dodaj kolejny przedział”.

Obsłuż wszystkie siedem dni tygodnia.

Wymagania:

- format 24-godzinny;
- co najmniej jeden przedział;
- endTime musi być późniejsze niż startTime;
- nie wysyłaj formularza z lokalnymi błędami;
- strefa czasowa wykryta automatycznie;
- na mobile każdy przedział może być pionową kartą;
- przycisk usuwania ma dostępną nazwę zawierającą dzień i godziny.

Nie dodawaj skomplikowanego wykrywania konfliktów, jeśli backend nie ma takiego
kontraktu. Podstawową walidację nakładania dodaj tylko wtedy, gdy można ją
zaimplementować deterministycznie i pokryć testami.

Główny przycisk:
„Zapisz dostępność”.

READY

Po uzyskaniu State.stage === READY pokaż osobny ekran:

- nagłówek „Konto jest gotowe”;
- krótkie podsumowanie profilu;
- wszystkie kroki oznaczone jako ukończone;
- przycisk „Przejdź do katalogu”.

Nie wykonuj natychmiastowego redirectu bez pokazania użytkownikowi sukcesu.

WIZUALNE WYMAGANIA

- zachowaj istniejący dark theme;
- używaj wyłącznie typografii bezszeryfowej;
- mat-form-field appearance="outline";
- jedna główna karta aktywnego kroku;
- border-radius około 16px;
- subtelne obramowanie;
- bez dużych dekoracyjnych cieni;
- bez przypadkowych wysokości kart;
- logiczne odstępy oparte o skalę 8px;
- główny przycisk wizualnie dominujący;
- nie wykonuj globalnego redesignu katalogu ani pozostałych ekranów.

STANY

Obsłuż wizualnie:

- initial loading — skeleton albo spokojny loader;
- initial load error — karta błędu i retry;
- stage loading;
- stage validation error;
- stage API error;
- success;
- READY;
- unauthorized;
- forbidden.

Podczas błędu nie ukrywaj wprowadzonych danych.

DOSTĘPNOŚĆ

- lang="pl";
- dokładnie jedno h1;
- hierarchia h1/h2;
- aria-live dla wyniku operacji;
- aria-busy podczas zapisu;
- aria-current dla bieżącego kroku;
- focus na nagłówku nowego kroku po sukcesie;
- pełna obsługa klawiaturą;
- widoczny focus;
- WCAG AA;
- tytuł dokumentu „Konfiguracja konta | moves”;
- bez automatycznego przesuwania fokusu podczas zwykłego pisania.

TESTY

Dodaj testy dla:

- renderowania tylko bieżącego kroku;
- wszystkich mapowań stage;
- wyboru typu profilu bez natychmiastowego requestu;
- przejścia po kliknięciu Kontynuuj;
- participant timeZoneId;
- specjalizacji specjalisty;
- wszystkich dni tygodnia;
- dodania i usunięcia przedziału;
- walidacji godzin;
- widoku READY;
- focusu po zmianie kroku;
- mobilnego progress indicatora;
- ukrycia nawigacji produktowej w trybie onboardingu.

WALIDACJA

Uruchom:

- testy Angulara;
- build produkcyjny;
- właściwe testy backendu, jeśli backend został zmieniony;
- kontrolę budżetu stylów komponentu.

RAPORT

Podaj:

1. branch, HEAD i początkowy stan;
2. decyzje architektoniczne;
3. listę zmienionych plików;
4. opis każdego kroku;
5. testy i wyniki;
6. nierozwiązane blockery;
7. końcowy git status;
8. ocenę READY FOR BROWSER QA albo NOT READY.

Nie wykonuj commita ani push.
Prompt 3 — kontrola runtime i poprawki
ROLA

Działaj jako senior frontend QA engineer z prawem do poprawiania kodu.
Przeprowadź praktyczny audyt wdrożonego onboardingu moves i usuwaj znalezione
problemy na bieżąco.

Nie ograniczaj się do raportu.

ZASADY

- przeczytaj instrukcje repozytorium i skillsy;
- sprawdź branch, HEAD i working tree;
- zachowaj zastane zmiany;
- korzystaj z MCP IntelliJ oraz narzędzi przeglądarkowych;
- przed Mavenem/Javą aktywuj Java 25 przez SDKMAN;
- nie wykonuj commita ani push;
- używaj istniejącego Compose;
- testuj prawdziwy lokalny OIDC, bez atrap logowania.

SCENARIUSZE

Sprawdź co najmniej:

1. Pierwsze wejście nowego użytkownika.
2. Odświeżenie na każdym kroku.
3. Wylogowanie i ponowne zalogowanie.
4. Przerwanie i wznowienie onboardingu.
5. Profil uczestnika.
6. Profil specjalisty.
7. Niepoprawne pole profilu.
8. Niepoprawny przedział godzin.
9. Błąd początkowego GET state.
10. Błąd zapisu każdego kroku.
11. Podwójne szybkie kliknięcie głównego przycisku.
12. Stan READY i przejście do katalogu.
13. Bezpośrednie wejście na inne trasy przed ukończeniem onboardingu.
14. Sesję po wygaśnięciu albo odświeżeniu tokenu.

PRZEGLĄDARKA

Sprawdź:

- 1440x900;
- 1024x768;
- 768x1024;
- 390x844;
- 320x700.

Dla każdej szerokości zweryfikuj:

- brak poziomego scrolla;
- brak uciętych przycisków;
- czytelne pola;
- prawidłową kolejność treści;
- minimalne cele dotykowe;
- brak nachodzenia toolbara;
- poprawny scroll do błędnego pola.

DOSTĘPNOŚĆ

Wykonaj:

- przejście całego flow samą klawiaturą;
- kontrolę kolejności tabulatora;
- sprawdzenie focusu po zmianie kroku;
- axe;
- kontrolę nazw przycisków;
- kontrolę aria-live;
- kontrolę aria-current;
- kontrolę kontrastu;
- kontrolę reduced motion, jeśli wprowadzono animacje.

RUNTIME

Sprawdź:

- konsolę przeglądarki;
- requesty i statusy API;
- brak podwójnych requestów;
- payload participantProfile zawierający timeZoneId;
- payload availability;
- format godzin;
- brak surowych wyjątków w interfejsie;
- zachowanie po 401, 403, 404 i 5xx;
- zachowanie Keycloak.

E2E

Dodaj albo popraw Playwright E2E:

- prawdziwe logowanie OIDC;
- pełny flow uczestnika;
- pełny flow specjalisty, jeśli dostępne jest niezależne konto testowe;
- wznowienie po refreshu;
- retry po kontrolowanym błędzie API;
- mobile viewport;
- axe dla każdego kroku.

ZDJĘCIA

Wykonaj zrzuty:

- każdy krok desktop;
- każdy krok mobile;
- loading;
- initial load error;
- validation error;
- API error;
- READY;
- focus klawiaturowy;
- rozwinięty formularz kilku przedziałów dostępności.

POPRAWKI

Napraw znalezione problemy w tej samej turze. Po każdej istotnej poprawce uruchom
najwęższy właściwy zestaw testów, a na końcu pełną walidację frontendu.

RAPORT

Podaj:

1. środowisko i uruchomione usługi;
2. testowane konta i role bez ujawniania sekretów;
3. listę scenariuszy z wynikiem;
4. znalezione problemy i wykonane poprawki;
5. wyniki testów i axe;
6. ścieżki do zrzutów;
7. nierozwiązane blockery;
8. końcowy git status;
9. ocenę ACCEPTABLE, ACCEPTABLE WITH ISSUES albo NOT ACCEPTABLE.
