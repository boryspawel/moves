# ADR-021: neutral specialist authorization boundary

Consumers of specialist relationship and professional-capability checks depend on
`identityaccess.api.SpecialistAuthorizationPort`, not on the `specialist` module.
The specialist module remains the provider: `SpecialistAuthorizationService` performs
the same verified-scope, active-relationship, consent, audit and capability checks.

The neutral port exposes the existing capability decision and a guard-only
`requireActiveRelationship` operation. Its participant argument is named
`participantId`; the actor remains `actorAccountId`. This is an internal Java
boundary change and does not rename HTTP/OpenAPI fields or alter permissions.

This keeps specialist dashboard reads one-way while planning, goals, documentation,
load analysis and execution consume authorization without creating module cycles.
