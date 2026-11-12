# MOB-004 — spike Kotlin OpenAPI Generator

**Data:** 2026-11-12. **Decyzja:** **NO-GO** dla bezpośredniego, pełnego klienta Kotlin wygenerowanego z aktualnego snapshotu. M1 korzysta z ręcznie typowanego adaptera czterech odczytów, opisanego w [07 — M1](07-m1-android-build.md), bez zmiany kontraktu.

## Zakres

Eksperyment czytał niezmieniony, już zmodyfikowany lokalnie snapshot `web/openapi/openapi.json` (SHA-256 `ba8a2ae6fe9aa3117dcdeda7aa2ab6106d6e89b57106447c2b5642caf9e9ab4f`); nie zapisywał go ani nie zmieniał backendu, migracji, Keycloak, zgód lub funkcji medycznych. Każde uruchomienie generuje klienta ponownie z bieżącego snapshotu i wypisuje jego SHA-256; celowo nie zamrażamy ani nie kopiujemy dużego pliku specyfikacji. Pierwsze uruchomienie użyło lokalnego JAR-a OpenAPI Generator `7.24.0`, a niezależny kontrolny spike mobilny użył `7.26.0`. Współdzielona konfiguracja web pozostaje na 7.24.0 do odrębnej migracji wygenerowanego klienta.

Wygenerowane źródła pozostają poza repozytorium w katalogu tymczasowym. Kontrolowany build użył JDK `17.0.21`, Gradle `8.14.3` i Kotlin Gradle Plugin `2.2.21`. JDK 17 jest konieczny dla tego eksperymentu: Gradle 8.14 nie uruchamia się na hoście z Java 25 (major version 69), a Java 25 wymaga Gradle 9.1 lub nowszego.

Kontrolny compile gate używa Kotlin Gradle Plugin i plugin serializacji `2.4.21`, Retrofit wraz z converterami `3.0.0`, kotlinx-serialization `1.13.0`, OkHttp `5.5.1`, Gson `2.15.0` oraz Gradle `8.14.3-bin`. Gradle 8.14.3 wykonał build; pobrany wrapper 8.14.5 był uszkodzony, więc nie jest uznany za przetestowany. Wersja Kotlin `2.5.0-RC` została wykluczona. To potwierdza, że użyty compile gate nie wynika z nieobsługiwanej pary Kotlin/Gradle. Źródła: [Kotlin Gradle plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) i [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html).

## Wynik

Wszystkie trzy warianty pełnego klienta Retrofit2 wygenerowane przez 7.24.0 zakończyły `compileKotlin` kodem wyjścia `1`:

| Serializacja / opcja `oneOf` | Wynik | Reprezentatywny błąd |
| --- | --- | --- |
| `kotlinx_serialization`, `generateOneOfAnyOfWrappers=true` | FAIL | `Dose` jest `sealed interface`, a `AerobicDose.kt:124` dziedziczy jako `: Dose()`; kompilator: `This type does not have a constructor` |
| `kotlinx_serialization`, `generateOneOfAnyOfWrappers=false` | FAIL | `AerobicDose.kt:83` nie oznacza `rpe` jako `override`, a `AerobicDose.kt:89` odwołuje się do nieistniejącego `AerobicDose.Side` |
| `gson`, `generateOneOfAnyOfWrappers=true` | FAIL | `AerobicDose.kt:69` ma `type overrides nothing`; model `Dose` jest finalnym data class i nie może być klasą bazową |

Źródłem jest kompozycja kontraktu `Dose`: sześć wariantów `AerobicDose`, `BreathingDose`, `IsometricDose`, `MobilityDose`, `StrengthDose` i `StretchDose` łączy `oneOf` z `allOf`. Nie jest to błąd konfiguracji Androida. Domyślny build wygenerowany przez Generator 7.24.0 również nie został przyjęty jako konfiguracja produktu: pinował Kotlin 2.4.0 i Retrofit 3.0.0 poza celowo kontrolowanym eksperymentem.

## Powtórzenie na aktualnym generatorze

Kontrolne uruchomienie OpenAPI Generator `7.26.0` z aktualnymi stabilnymi zależnościami opisanymi wyżej ponownie dotarło do tych samych błędów dziedziczenia `Dose`, zarówno dla `kotlinx_serialization`, jak i Gson. Rozwiązanie zależności oraz kompilacja infrastruktury Gradle zakończyły się przed tym punktem poprawnie. Aktualizacja generatora nie usuwa blokady pełnego klienta. Dla obecnego M1 decyzją jest mały, ręcznie typowany adapter czterech odczytów; nie zmienia on kontraktu ani nie wymaga obejścia modelu `Dose`.

## Co zostało sprawdzone statycznie

- Każdy z sześciu discriminatorów `Dose` jest obecny w wygenerowanym kodzie; jego JSON round-trip nie był wykonany, ponieważ klient się nie kompiluje.
- UUID są mapowane jako `java.util.UUID`, daty jako `java.time.LocalDate`, a date-time jako `java.time.OffsetDateTime`.
- Pola nie wymagane w specyfikacji są generowane jako nullable z domyślną wartością `null`; należy traktować to jako obserwację generatora, nie potwierdzenie semantyki runtime.
- Wymagany nagłówek `Idempotency-Key` jest generowany jako nienullowalny parametr Retrofit `@Header("Idempotency-Key")`.
- Wygenerowane endpointy zwracają `Call<T>`; klient nie zawiera produktuowego mapowania HTTP 400/401/409 ani Problem Details. Tę granicę powinien w przyszłości obsłużyć repository/error mapper, ale runtime nie był testowany.

Nie uruchomiono MockWebServer ani żądania do backendu: nie można wiarygodnie testować transportu, odpowiedzi 400/401/409 ani serializacji `Dose`, dopóki klient nie przejdzie kompilacji.

## Odtworzenie

Skrypt [run.sh](experiments/mob-004/run.sh) generuje pierwszy wariant do katalogu tymczasowego i uruchamia kontrolowaną kompilację na generatorze 7.26.0. Wymaga dostępu do zależności Gradle/Maven, JDK 17 oraz obecnego lokalnego JAR-a generatora. Domyślnie tworzy odrębny `GRADLE_USER_HOME` we własnym katalogu tymczasowym; pierwsze uruchomienie wymaga sieci do pobrania Gradle 8.14.3-bin i artefaktów Maven. Do działania offline należy wcześniej wypełnić ten cache albo jawnie dostosować go do istniejącego cache Gradle.

```bash
JAVA_HOME=/path/to/jdk-17 bash docs/mobile/experiments/mob-004/run.sh
```

Generator i opcje są zgodne z [dokumentacją generatora Kotlin](https://openapi-generator.tech/docs/generators/kotlin/) oraz [opcją `generateOneOfAnyOfWrappers`](https://openapi-generator.tech/docs/generators/kotlin/#config-options). Skrypt nie zapisuje OpenAPI i nie dodaje wygenerowanych źródeł do repozytorium.

## Dalsze warianty decyzji

1. Wykonać nowy, osobno przypięty spike nowszego OpenAPI Generator, po uprzednim potwierdzeniu, że naprawia dokładnie `oneOf`/`allOf` dla `Dose`; nie zakładać tego na podstawie samej wersji.
2. Utrzymywać lokalny override szablonu generatora dla tego modelu wraz z testami wszystkich sześciu wariantów JSON, enumów, UUID/dat, opcjonalności, nagłówka idempotencji i 400/401/409. Ma to stały koszt utrzymania przy aktualizacji generatora.
3. Wygenerować tylko bezpieczny podzbiór API lub napisać małą, typowaną granicę HTTP ręcznie. Taki wariant nadal musi niezależnie udowodnić mapowanie `Dose`; nie należy zmieniać backendu ani kontraktu wyłącznie dla generatora.

M1 zamyka MOB-004 dla obecnego, małego zakresu: ocenione standardowe próby selekcji po operationId nadal obejmowały duży graf pośrednich modeli, a jego ręczne pielęgnowanie po każdej regeneracji nie jest uzasadnione. Nie jest to twierdzenie, że każda możliwa konfiguracja selektywnego generatora jest niemożliwa. Nie wdrożono generatora częściowego, własnych szablonów ani ręcznych poprawek wygenerowanego kodu. Pełny klient wymaga osobnej decyzji i ponownego, niezależnego spike'a.
