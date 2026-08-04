# Appointment lifecycle history

`calendar.appointment` remains the authoritative current snapshot used by calendar, today, and header reads.
`calendar.appointment_event` is append-only lifecycle history written in the same transaction as successful calendar mutations.

V049 seeds one `BASELINE` row per existing appointment. A baseline records the migrated snapshot at its existing `starts_at`/`updated_at`; it is a migration fact, not a domain event. Specialist timelines translate it to the equivalent public scheduled, completed, cancelled, or no-show appointment type and never expose `BASELINE`.
