# Prompt 2 — focused onboarding UI

## Participant workspace (frontend prompt 2)

- Added lazy, specialist-guarded `/specialist/clients/:participantId` workspace using only the generated bounded workspace and cursor-timeline endpoints through `ApiFacade`.
- The page preserves `range`, `types`, `eventId`, and `view` in the URL; it provides responsive SVG overview, semantic chronological timeline/list alternative, event panel focus restoration, and cursor-based older-history loading.
- Presentation components are standalone OnPush components. Sensitive/technical identifiers are not rendered, and only backend-advertised known quick actions are exposed.
- `SCHEDULE_APPOINTMENT` is the sole currently contract-backed quick action: it opens an accessible in-place form, sends the generated appointment command with an idempotency key, then refreshes bounded workspace/timeline data. Other requested operations remain unavailable because the workspace contract does not advertise matching commands.

## Delivered

- Remediated backend assignment bucketing so deterministic high-bit SHA-256 values use a non-negative variant index; a focused regression test covers this path.
- `OnboardingPage` remains the smart container: the backend `State.stage` is the only stage authority and all API mutations remain in it.
- The focused onboarding route removes the product toolbar, navigation and footer at the shell level. It does not add a route-completion guard.
- Standalone OnPush presentation components render profile type, four-step progress, legal acknowledgements, basic profile, recurring availability, and READY completion.
- Profile type is only submitted by **Kontynuuj**. Specialist profile exposes the existing trainer/physiotherapist kinds and a required, visible time-zone selector. Availability supports every day of the week, repeatable add/remove rows, and rejects an end time not later than its start.
- The legal step requires both existing contract-backed acknowledgements before it calls the onboarding legal endpoint. It does not fabricate legal document content or links. READY sends the user to the existing catalog route.

## Accessibility and responsive behavior

- Status updates use a polite live region; loading/submission exposes `aria-busy`; error and legal states use alerts.
- Native Material controls retain labels, 44px minimum target sizing, visible focus, and reduced-motion handling.
- The focused dark layout has a four-stage current/complete/blocked treatment and collapses safely down to 320px.

## Deliberately unchanged

- No backend contracts, persistence, generated API client, dependencies, or legal acknowledgement semantics were changed.
- No completion guard was added: backend stage remains authoritative, while existing routes remain available as before.
