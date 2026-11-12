# M1 — minimalny fundament Android

**Status:** lokalny build, testy jednostkowe i oba debug APK zweryfikowane; workflow CI przeszedł walidację statyczną, ale nie został uruchomiony zdalnie. Dokument nie obejmuje ekranów biznesowych, OIDC ani testów na urządzeniu.

## Klient API i MOB-004

Pełne wygenerowanie Kotlin przez OpenAPI Generator 7.24.0 i 7.26.0 pozostaje zablokowane przez niepoprawne dziedziczenie `Dose` (`oneOf` razem z `allOf`). Standardowa próba ograniczenia generatora do czterech operacji nadal wymagała dużej, pośrednio zależnej części specyfikacji; ręczne utrzymywanie list modeli lub szablonów generatora byłoby mniej stabilne niż mała granica HTTP.

M1 używa więc ręcznie typowanego adaptera Retrofit tylko dla odczytów: `GET /api/v1/identity/me`, `GET /api/v1/specialist/today`, `GET /api/v1/specialist/participants` i `GET /api/v1/specialist/worklist`. Modele zachowują UUID, `date`, `date-time`, enumy i opcjonalność ze snapshotu OpenAPI. Adapter rozróżnia 401, 403, 409 i inne odpowiedzi HTTP z opcjonalnym `Problem`.

`Idempotency-Key` nie jest wysyłany przez odczyty. Transport udostępnia wyłącznie funkcję zakładającą ten nagłówek na pojedyncze żądanie przyszłej, istniejącej operacji mutującej; nie dodaje własnego endpointu ani mutacji.

## Struktura

`android/` jest jednym modułem `app`: Compose UI → `ReadinessViewModel` → `SpecialistRepository` → Retrofit API. Trzy górne sekcje to Dzień, Uczestnicy i Sprawy. Bez tokenu aplikacja nie wykonuje żądań; M1 pokazuje stan pustej sesji. Nie ma lokalnej bazy danych, synchronizacji, dokumentów, załączników, danych biomedycznych ani logiki zgód.

Warianty `dev` i `prod` mają `BASE_URL` w `BuildConfig`. Nie zawierają sekretów. W `dev` domyślny adres emulatora to `http://10.0.2.2:8080/`; urządzenie fizyczne wymaga adresu hosta LAN przekazanego właściwością Gradle. `prod` domyślnie używa celowo nieprawidłowego `https://moves-api.example.invalid/` i wymaga rzeczywistego publicznego URL przed integracją. Cleartext jest dopuszczony wyłącznie dla `dev`.

## Budowanie

Wymagane są JDK 17 oraz Android SDK z `platform-tools`, `platforms;android-37.0` i `build-tools;36.0.0` (domyślny wybór AGP 9.3). Ustaw SDK przez `ANDROID_HOME` albo `android/local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

```bash
cd android
JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk ./gradlew --no-daemon :app:testDevDebugUnitTest :app:assembleDebug
JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk ./gradlew --no-daemon :app:assembleProdDebug -Pmoves.prodBaseUrl=https://api.example.org/
JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk ./gradlew --no-daemon :app:assembleDevDebug -Pmoves.devBaseUrl=http://192.168.1.20:8080/
```

Debug APK `dev` jest w `android/app/build/outputs/apk/dev/debug/app-dev-debug.apk`. Workflow `.github/workflows/android.yml` wykonuje ten sam test i build oraz publikuje APK jako artefakt `moves-mobile-dev-debug-apk`. Nie uruchamia emulatora ani nie instaluje aplikacji.

Po ręcznym podłączeniu urządzenia można sprawdzić ADB i zainstalować lokalny artefakt:

```bash
adb devices
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Na emulatorze adres `10.0.2.2` wskazuje hosta z Compose backendem na porcie 8080. Na urządzeniu fizycznym należy użyć osiągalnego adresu LAN i świadomie zapewnić dostęp do środowiska deweloperskiego. Testy urządzenia i emulatora pozostają poza M1.

## Zweryfikowany wynik lokalny

Na JDK 17 i Android SDK z `platforms;android-37.0` wykonano `:app:compileDevDebugKotlin`, `:app:testDevDebugUnitTest` oraz `:app:assembleDebug`. Testy zakończyły się wynikiem 5/5: cztery testy transportu sprawdzają UUID, `LocalDate`, `OffsetDateTime` z zachowaniem `+02:00`, enumy i opcjonalność, nagłówek bearer oraz brak `Idempotency-Key` w odczytach, odpowiedzi 401/403/409 i nie-JSON 502 oraz klucz idempotencji na pojedynczym żądaniu. Piąty test potwierdza, że bez tokenu ViewModel pozostaje w stanie pustym bez wywołania repository.

Powstały oba APK debug:

| Wariant | Ścieżka | Rozmiar | SHA-256 |
| --- | --- | ---: | --- |
| dev | `android/app/build/outputs/apk/dev/debug/app-dev-debug.apk` | 12 464 844 B | `2601fcbe58c092e6cae5ff5351fc79cd8a612ffcea05825389cdb18c1c0e7e15` |
| prod | `android/app/build/outputs/apk/prod/debug/app-prod-debug.apk` | 12 464 836 B | `04b7b62d8960773e7fcae902ab4f5b010bf9c3ace8009641adbc26b153f2809f` |

Zweryfikowano manifest (`minSdk` 26, `targetSdk` 37) i podpis wariantu `devDebug`. `actionlint` 1.7.12, po weryfikacji sumy kontrolnej, zaakceptował `.github/workflows/android.yml`; sprawdzono również składnię YAML i semantykę workflow. Nie uruchomiono workflow w GitHub Actions, ADB, emulatora ani urządzenia. Nieznaczący stderr OkHttp podczas testów nie wpływał na ich wynik.
