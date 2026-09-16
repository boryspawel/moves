# ADR-023: Obiektywna publikacja katalogu ćwiczeń

- Status: przyjęta
- Data: 2026-09-16

## Decyzja

V062 zastępuje wąską część ADR-010, która czyniła ręczną recenzję warunkiem publikacji.
Normalny przepływ katalogu to `DRAFT` → edycja → automatyczna walidacja obiektywnej
gotowości → `PUBLISHED`. Opublikowana wersja pozostaje niezmienna; zmiana wymaga
następnej wersji albo wycofania zgodnie z istniejącymi regułami.

## Konsekwencje

Historia i endpointy recenzji pozostają kompatybilnym, uśpionym zapisem, ale nie są
bramką normalnego UI ani publikacji. Import nadal zachowuje staging, mapowania,
licencję i artefakt z ADR-010; przechodzi do tego samego szkicu i edytora.
