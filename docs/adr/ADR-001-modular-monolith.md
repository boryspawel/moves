# ADR-001: Modularny monolit

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Rdzeń obejmuje spójne transakcyjnie katalog, plan, wykonanie, bezpieczeństwo, zgody i audyt. Pierwszy zespół i wolumen nie uzasadniają kosztu systemu rozproszonego.

## Decyzja

Budujemy jeden deployowalny backend Spring Boot. Bounded contexts są głównymi pakietami z kontrolowanymi API, osobnymi tabelami/schematami i testem cykli ArchUnit. Wydzielenie usługi wymaga mierzalnego uzasadnienia i osobnego ADR.

## Konsekwencje

Prostsze transakcje, testy i operacje. Granice muszą być pilnowane w kodzie; wspólna baza nie uprawnia do bezpośrednich zapytań do tabel innego modułu.
