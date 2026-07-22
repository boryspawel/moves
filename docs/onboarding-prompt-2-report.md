# Prompt 2 — focused onboarding UI

## Delivered

- Remediated backend assignment bucketing so deterministic high-bit SHA-256 values use a non-negative variant index; a focused regression test covers this path.
- `OnboardingPage` remains the smart container: the backend `State.stage` is the only stage authority and all API mutations remain in it.
- The focused onboarding route removes the product toolbar, navigation and footer at the shell level. It does not add a route-completion guard.
- Standalone OnPush presentation components render profile type, four-step progress, the safe legal blocker, basic profile, recurring availability, and READY completion.
- Profile type is only submitted by **Kontynuuj**. Specialist profile exposes the existing trainer/physiotherapist kinds. Availability supports every day of the week, repeatable add/remove rows, and rejects an end time not later than its start.
- The legal step has no fabricated document content, acknowledgement action, or link. READY sends the user to the existing catalog route.

## Accessibility and responsive behavior

- Status updates use a polite live region; loading/submission exposes `aria-busy`; error and legal states use alerts.
- Native Material controls retain labels, 44px minimum target sizing, visible focus, and reduced-motion handling.
- The focused dark layout has a four-stage current/complete/blocked treatment and collapses safely down to 320px.

## Deliberately unchanged

- No backend contracts, persistence, generated API client, dependencies, or legal acknowledgement semantics were changed.
- No completion guard was added: backend stage remains authoritative, while existing routes remain available as before.
