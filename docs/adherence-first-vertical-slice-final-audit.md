# Końcowy audyt vertical slice adherence-first

Status: P10 dostarczony. Ten raport opisuje wdrożone MVP i świadomie nie
rozszerza jego zakresu.

## Ślad przepływu

1. Plan jest aktywowany przez `PlanRevisionWorkflowService`; powstaje
   `PLAN_ACTIVATED` i przy pierwszej aktywacji uczestnik dostaje assignmenty.
2. `TodayAgenda` pokazuje przypisaną sesję, a uczestnik rozpoczyna próbę;
   zapis `SESSION_ATTEMPT_STARTED` zachowuje wybrany wariant.
3. Końcowa deklaracja wykonania domyka próbę i zapisuje `SESSION_COMPLETED`.
4. `MINIMUM` jest dostępny wyłącznie jako zatwierdzony wariant albo ścieżka
   recovery; nadal przechodzi przez aktualny effective safety envelope.
5. Zgłoszenie bariery zapisuje `BARRIER_REPORTED` z kodem reguły i wybraną
   akcją, bez wolnego tekstu w analityce.
6. Recovery oferuje dozwoloną ścieżkę, zapisuje `RECOVERY_CHOICE_SELECTED`, a
   po wykonaniu powrotu `RECOVERY_RETURN_COMPLETED`.
7. Zasada bariery/recovery może utworzyć użyteczny, zminimalizowany element
   worklisty specjalisty; jego odpowiedź zapisuje `WORKLIST_REPLIED`.
8. Powyższe fakty są deduplikowanymi zdarzeniami `analytics.adherence_metric_event`;
   obejmują techniczne referencje i kody wersji, nie notatki, diagnozy ani
   clinical rationale.

## Zakres MVP

Wdrożono neutralną instrumentację dla aktywacji planu, próby i ukończenia
sesji, bariery, wyboru i ukończenia recovery, zmiany dostępności oraz odpowiedzi
z worklisty. V032 tworzy osobny schemat `analytics`, ogranicza zbiór kodów
zdarzeń i indeksuje termin wygaśnięcia. Każde zdarzenie ma 180-dniową retencję;
automatyczny, codzienny wewnętrzny harmonogram wywołuje idempotentne
`purgeExpired()`.

Trzy eksperymenty w wersji 1 mają trwałe assignmenty, unikalne per uczestnik,
klucz i wersja: `REMINDER_PRESENTATION` (`CLASSIC`/`BARRIER_FIRST`),
`AGENDA_PRESENTATION` (`DASHBOARD`/`TODAY_ONLY`) oraz `AVAILABILITY_PLAN`
(`MINIMUM_PLAN`/`STANDARD_PLAN`). Wariant jest wyliczany deterministycznie z
UUID uczestnika, klucza i wersji przez SHA-256, a następnie utrwalany; retry i
współbieżne pierwsze przypisanie nie zmieniają przydziału.

## Granice świadomie zachowane

MVP nie wysyła danych do SaaS i nie wprowadza predykcji, automatycznej decyzji
klinicznej, globalnego score ryzyka/load ani analityki zawierającej wolny tekst.
Safety pozostaje niezmienne: brak lub nieaktualny assessment blokuje rozpoczęcie
i recovery, a eksperymenty oraz metryki nie omijają effective safety envelope.

Funkcje oznaczone **LATER/EXPERIMENT/DO_NOT_BUILD** pozostają poza wdrożeniem:
społeczności/grupy, rankingi, streaks, punkty i badge, marketplace oraz
wearables jako rdzeń, a także AI do rozpoznawania barier, upraszczania komunikacji
i podsumowań dla specjalisty. Trzy opisane powyżej eksperymenty są jedynie
deterministycznymi assignmentami i pomiarem — nie są dowodem skuteczności ani
mechanizmem automatycznego dostosowania opieki.

## Kontrakty legacy

Legacy endpointy i widoki pozostają kompatybilne. Można je oznaczyć
`deprecated` dopiero po zebraniu dowodu braku klientów dla każdego kontraktu;
P10 nie zmienia ani nie usuwa tych kontraktów.
