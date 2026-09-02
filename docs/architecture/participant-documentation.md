# Participant documentation

Participant documentation is owned by the specialist account and uses the canonical `participant_id`; it is stored in `participant_documentation` and always references `participant.participant_record`, which remains the canonical participant record and optional account-link boundary. Documentation does not create or replace that record and does not require a participant account or access link. Both trainer and physiotherapist may work in their explicit active relationship context.

Interviews use the static `INITIAL_SPECIALIST_INTERVIEW` v1 template. Question snapshots are persisted with each answer, so later template changes cannot reinterpret completed records. Draft interviews may be saved; completed interviews are immutable and completing a later interview supersedes the earlier completed interview for that specialist and participant.

Notes are specialist-owned drafts. A final note is immutable and may only be archived. Audit records include action and record identity only: note content and interview answers are never put in general audit payloads.

All documentation reads and writes require the active specialist-participant relationship over the canonical participant record. Until a retention/consent policy is designed, terminated relationships lose access to historical documentation. TODO: define historical access, corrections to final notes, template version management, self-completion, sharing and attachments.

The specialist workspace presents compact interview and note summaries before the existing history. Documentation panels are deep-linkable, keyboard-dismissible, and use only the generated participant-documentation client. Editing and lifecycle controls are shown only when the documentation response advertises the corresponding `availableActions`; completed interviews and final notes open in view mode. Timeline entries use neutral `INTERVIEW` and `NOTE` labels and reference documentation identifiers solely to resolve the protected panel—answers and note content are never rendered into timeline cards.
