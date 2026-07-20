# Prompt 6 — gamifikacja opt-in

## Pochodzenie

- repo źródłowe: `../gra-mateusza`, branch `main`, lokalny HEAD `0b994cb6e35319837a2c81de4fb9a556b1943b90`;
- zweryfikowano `UserXpService`, `TrainingLogService`, `UserXp`, `UserLevelLog`, testy, migracje V006/V007 oraz aktywne ekrany profilu i rankingu;
- źródło przyznaje 50 XP za trening z mniej niż trzema ćwiczeniami i 100 XP od trzech ćwiczeń;
- źródłowy anti-farming od poziomu 3 redukuje nagrodę do 50%, gdy podobny trening wystąpił więcej niż trzy razy w ostatnich pięciu dniach;
- nie znaleziono backendowych cooldownów, limitów dobowych/tygodniowych, streaków ani questów. Ranking aktywnego UI zawiera stałe placeholdery, nie realny endpoint.

## Decyzje migracyjne

| Źródło | Cel | Decyzja |
|---|---|---|
| mutowalny `UserXp` | append-only `PointLedgerEntry` | REWRITE; suma ledgeru jest źródłem wyniku |
| `UserLevelLog` i wzór poziomu | brak w pierwszym etapie | REJECT/DEFER; zakres wymaga punktów i rankingu, a poziom nie może być drugim źródłem prawdy |
| `calculateXp` 50/100 | wersjonowalna bazowa wartość reguły | REFERENCE; brak domyślnego seeda, administrator publikuje jawne parametry |
| anti-farming: okno 5 dni, pełna nagroda dla pierwszych czterech podobnych aktywności, następnie 50% | `repeatWindowDays`, `fullRewardOccurrences`, `reducedRewardPercent` | PORT jako konfigurowalne i wersjonowane pola reguły |
| bezpośrednie wywołanie XP z `TrainingLogService` | neutralny `ExecutionQualificationPort` | REWRITE; gamifikacja otrzymuje tylko ID, konto, czas i klucz aktywności |
| power stats, fizyczne parametry profilu | brak | REJECT; dane te nie należą do rankingu |
| placeholder leaderboard | projekcja z ledgeru | REJECT/REWRITE |
| streaki, questy, drużyny, wyzwania | brak w tym etapie | DEFER; brak dojrzałej implementacji lub zakres późniejszy |

## Model i reguły

- `GamificationProfile` jest domyślnie wyłączony. Włączenie zapisuje czas opt-in; wcześniejszych wykonań nie można punktować wstecz.
- Widoczność rankingu wymaga włączonego profilu i pseudonimu. Wyłączenie widoczności natychmiast usuwa wiersz projekcji.
- Administrator publikuje niezmienną, nazwaną wersję `PointRuleVersion`. Poprzednia wersja staje się nieaktywna; stary ledger nadal wskazuje używaną wersję.
- Reguła zawiera bazę, limit dobowy i tygodniowy, cooldown, okno powtórzeń, liczbę pełnych nagród i procent malejącego zwrotu. Repo nie seeduje niepotwierdzonych wartości.
- Jedno źródłowe wykonanie tworzy najwyżej jeden wpis `AWARD`; ponowienie zwraca ten sam wpis niezależnie od nowego klucza HTTP.
- Cooldown i wyczerpane limity zapisują wynik zerowy w ledgerze. Dzięki temu decyzja jest idempotentna i nie można ponawiać jej po upływie czasu.
- Reversal jest ujemnym wpisem wskazującym oryginał i przechowującym powód; oryginał nie jest zmieniany.
- Limity liczą dodatnie nagrody, więc reversal nie odnawia puli i nie umożliwia farmienia.
- `RankingProjection` zawiera tylko konto, pseudonim i sumę; może zostać w całości odbudowana z ledgeru i aktualnego opt-in.
- Brak sprzedaży punktów, endpointów zakupowych i wpływu gamifikacji na plan.

## Granica prywatności i bezpieczeństwa

Gamifikacja nie importuje `ParticipantSafetyService`, raportu bólu, trudności ani notatek. Neutralny port zwraca wyłącznie istniejące, jawnie deklarowane wykonanie, konto, czas i klucz aktywności z wersji ćwiczeń. Hard block w execution nie tworzy wykonania, więc nie istnieje zdarzenie możliwe do kwalifikacji. DTO postępu i rankingu nie zawierają pól safety.

## API

- `PUT /api/v1/gamification/me/profile`;
- `GET /api/v1/gamification/me`;
- `POST /api/v1/gamification/executions/{executionId}/qualifications`;
- `GET /api/v1/gamification/ranking`;
- administracyjne publikowanie reguły, reversal i rebuild rankingu wymagają `GAMIFICATION_ADMIN`.

## Walidacja

- `PointPolicyTest`: 4 testy domenowe — cooldown, diminishing returns, limit dobowy i tygodniowy;
- `GamificationIntegrationTest`: 3 testy na PostgreSQL 18.4/Testcontainers — opt-in, brak retroaktywności, idempotencja, ledger, reversal z zachowaniem historii, brak danych wrażliwych, ranking/rebuild, ukrycie użytkownika, hard block i OpenAPI;
- celowany zestaw: 7 testów, exit 0 na Java 25.

## Dług techniczny

- polityka punktowa nie ma seeda produkcyjnego: wartości muszą zostać zatwierdzone i opublikowane przez administratora;
- okna dobowe/tygodniowe są kroczące 24 h/7 dni w UTC; ewentualne okna kalendarzowe zależne od strefy wymagają decyzji produktu;
- kwalifikacja jest synchronicznym endpointem. Docelowy outbox/consumer może użyć tego samego neutralnego portu i ograniczeń unikalności;
- brak detekcji anomalii do przeglądu i procesu moderacji;
- pseudonim jest unikalny case-insensitive, lecz zasady rezerwacji i moderacji nazw wymagają doprecyzowania;
- drużyny, streaki, questy, wyzwania, rekordy i osiągnięcia pozostają poza pierwszym etapem.
