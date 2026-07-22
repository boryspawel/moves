# Prompt 1 — kontrakt i stan onboardingu

## Decyzje

- Backendowy `State.stage` pozostaje jedynym źródłem prawdy; frontend mapuje go centralnie na stan prezentacji: wybór typu profilu, dokumenty, dane profilu, dostępność albo ukończenie.
- `participantProfile` wysyła `timeZoneId` tylko, gdy przeglądarka poda niepustą wartość z `Intl.DateTimeFormat().resolvedOptions().timeZone`. Backendowy kontrakt pozostaje kompatybilny: pole nie jest wymagane.
- Nie dodano komunikatu o nieodwracalności typu profilu. Obecny model tylko ignoruje kolejną próbę zmiany wybranego pola; nie jest to zatwierdzony, szerszy kontrakt produktu.

## BLOCKED_LEGAL_DOCUMENT_CONTENT

Repozytorium zawiera modele potwierdzeń, lecz nie zawiera zatwierdzonych publicznych treści ani URL regulaminu i informacji o prywatności. Krok prawny pokazuje bezpieczny stan z blokadą i nie wysyła potwierdzenia bez dokumentów. Do odblokowania potrzebna jest zatwierdzona publiczna projekcja metadanych dokumentów (`type`, `version`, `title`, `publicUrl`).

## Stan dla redesignu

Kontener oddziela ładowanie, błąd początkowego odczytu, załadowany etap, wysyłanie kroku i błąd kroku. Przy błędzie initial load nie renderuje formularzy; błąd wysłania zachowuje formularz oraz ostatni poprawnie odczytany `State`.
