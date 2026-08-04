# Appointment lifecycle history

`calendar.appointment` remains the authoritative current snapshot used by calendar, today, and header reads.
`calendar.appointment_event` is append-only lifecycle history written in the same transaction as successful calendar mutations. V050 widens the event record with five classification columns while preserving the existing history.

V049 seeds one `BASELINE` row per existing appointment. A baseline records the migrated snapshot at its existing `starts_at`/`updated_at`; it is a migration fact, not a domain event. It marks the boundary between the migrated baseline and later lifecycle history. Specialist timelines translate it to the equivalent public scheduled, completed, cancelled, or no-show appointment type and never expose `BASELINE`.

Public appointment-event identifiers are UUIDs distinct from the appointment identifier. A context-authorized detail endpoint resolves an event only within the participant context: the caller must have the active relationship and the capability required for that context. The UUID can therefore be used in a deep link without making it a capability or a cross-context lookup key.
