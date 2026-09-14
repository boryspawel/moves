# ADR-020: participant invitation claim and self-service read access

Participant records remain canonical and are linked 1:1 to an account only after a verified-email claim. An invitation stores a hashed credential, expiry and single-use/reissue lifecycle; the browser exchanges the emailed fragment for a distinct, HttpOnly, one-hour claim-context cookie before OIDC starts. The raw token is never persisted or routed.

Claim is deliberate after login. Backend record locks and database uniqueness turn races into explicit conflict responses; unfinished onboarding continues through its existing workflow and never creates a second record. A claim neither grants consent nor converts a specialist profile. SMTP delivery is opt-in and fails closed; local Compose uses Mailpit. Delivery happens before commit, so a sent email can exceptionally point at an uncommitted invitation.

The participant navigation exposes only bounded, backend-owned read views of goals, execution history and the exact saved plan revision. Dates are rendered in the browser timezone; no latest planning source is substituted for a snapshot.
