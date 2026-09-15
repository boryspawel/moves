# P0–P6 manual QA handoff

This is a final-validation handoff, not a PASS claim. No automated browser/OIDC flow was executed.
Authenticate manually with a fresh subject after fix 3384; record the environment, role and observed
response before calling any authorization result green.

- **Specialist login and workspace:** verify trainer and physiotherapist entry, active relationship
  selection and deep links. Edge: stale/missing relation or wrong acting context must not reveal a client.
- **Participant record:** create/open an account-free and a linked record. Edge: identifiers remain
  distinct and no account is inferred for an account-free record.
- **Goals, lifecycle and observations:** create a supported goal, add observations, update metadata,
  then achieve/cancel through allowed actions. Edge: terminal goal rejects measurement; own paged
  history exposes no recorder, note or evidence source.
- **Plans:** select an eligible goal, create/reopen a revision and activate through server flow. Edge:
  source changes do not rewrite an activated snapshot.
- **Set authoring, sharing and revoke:** create/edit/publish, grant one exact version, use it in a plan,
  then revoke. Edge: revoke blocks future draft validation/activation but not history.
- **Self-directed plans:** create/read only the participant’s plan. Edge: participant cannot acknowledge
  or override a hard safety block.
- **Safety:** inspect effective restrictions in both professional contexts. Edge: trainer does not see
  physiotherapist-only clinical rationale.
- **Invitation claim:** claim through fragment/cookie flow. Edge: account, canonical participant and
  invitation IDs remain distinct; fragment is removed before OIDC.
- **Agenda:** inspect today, slots and overdue appointment action. Edge: terminal appointment states
  are not candidates for the next appointment.
- **Execution:** perform partial, skipped and stopped outcomes, then resume where allowed. Edge: only
  fully completed selected prescriptions qualify as `COMPLETED`.
- **Adherence:** inspect completed/planned-session summary data and inclusive 7/30-day scheduled windows.
  Edge: zero/null denominator or no data must not appear as `MISSED` or reconstructed history.
- **Timeline:** start in timeline, filter and deep-link to a goal. Edge: unavailable protected detail
  remains unavailable without content disclosure.
- **Progress/chart:** inspect each outcome by `goalId`. Edge: mixed units are excluded, no-data/baseline-
  only states are explicit, percent is unclipped only for meaningful baselines, and no combined score exists.
- **Responsive and accessibility:** repeat workspace, goals, history and chart interaction at narrow width,
  200% zoom, keyboard-only and reduced-motion settings. Edge: panels return focus and controls retain names.

No other concrete blocking debt is recorded here. The required manual browser/accessibility/auth review
remains outstanding. Existing budget warnings are data signals, not a UI defect without manual review.
