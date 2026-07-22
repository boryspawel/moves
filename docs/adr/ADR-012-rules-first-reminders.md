# ADR-012: reminders rules-first

`notification.reminders` jest lokalnym modułem właścicielem preferencji uczestnika i
neutralnego audytu decyzji/dostarczeń. Reguły `REMINDER_RULES_V1` są deterministyczne:
respektują timezone i quiet hours, limit częstotliwości, opt-out, complete/reschedule,
zgłoszenie bólu oraz zgodę na łagodny powrót. Audit przechowuje wyłącznie wersjonowany
reason code, kanał i wynik decyzji — bez tekstu wolnego lub danych klinicznych.

Pojedyncze pominięcie nie tworzy sygnału dla specjalisty. Moduł nie zmienia planu,
nie przewiduje czasu, nie korzysta z SaaS i nie dostarcza predykcyjnej personalizacji.
