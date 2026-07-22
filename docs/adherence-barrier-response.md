# Zgłoszenie bariery — reguła `BARRIER_RESPONSE_V1`

`adherence` zapisuje pojedynczy, idempotentny `BarrierReport` dla sesji przed
startem albo jej aktywnej próby. Rekord zawiera kategorię, pełny zestaw
proponowanych akcji, wybór uczestnika, wynik oraz kod wersji reguły. Słownik
kategorii jest trwały i wersjonowany w `adherence.barrier_category_dictionary`.

Reguły są deterministyczne: `NO_TIME` i `FATIGUE` proponują wyłącznie wcześniej
zatwierdzone `SHORT`/`MINIMUM` oraz przeplanowanie; `TOO_DIFFICULT` używa tylko
`MINIMUM`; `PAIN_OR_SYMPTOMS` nie formułuje porady medycznej; a
`UNSURE_TECHNIQUE` może zatrzymać ćwiczenie i poprosić o kontakt. Jeżeli safety
nie pozwala na wykonanie, jedyną opcją jest kontakt ze specjalistą.

Sygnał kontaktu jest neutralnym rekordem modułu `specialist`. Powstaje tylko po
wyborze `CONTACT_SPECIALIST`; dla bólu i niepewnej techniki ma priorytet
`PROMPT`, a brak czasu nigdy nie tworzy sygnału automatycznie ani pilnego.
Żadna akcja nie zmienia ograniczeń safety, planu ani finalnego wykonania.
