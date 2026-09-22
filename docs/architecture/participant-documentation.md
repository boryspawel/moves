# Participant documentation

Participant documentation is owned by the specialist account and uses the canonical `participant_id`; it is stored in `participant_documentation` and always references `participant.participant_record`, which remains the canonical participant record and optional account-link boundary. Documentation does not create or replace that record and does not require a participant account or access link. Both trainer and physiotherapist may work in their explicit active relationship context.

Interviews use the static `INITIAL_SPECIALIST_INTERVIEW` v1 template. Each answer stores a snapshot of its question title, type and required flag, preserving the meaning of a completed answer if the template changes. Draft interviews may be saved; completed interviews are immutable and completing a later interview supersedes the earlier completed interview for that specialist and participant.

Notes are specialist-owned drafts. A final note is immutable and may only be archived. Audit records include action and record identity only: note content and interview answers are never put in general audit payloads.

## Granice dostępu

Każdy odczyt i zapis wymaga aktywnej relacji specjalista–uczestnik nad kanoniczną
kartoteką. Po zakończeniu relacji dokumentacja nie jest dostępna. System nie udostępnia
korekt finalnych notatek, self-completion, sharingu, attachments ani zarządzania wersjami
template. Zmiana zasad retencji, dostępu lub zgody wymaga jawnej decyzji
produktowo-prawnej.

The specialist workspace presents compact interview and note summaries before the existing history. Documentation panels are deep-linkable, keyboard-dismissible, and use only the generated participant-documentation client. Editing and lifecycle controls are shown only when the documentation response advertises the corresponding `availableActions`; completed interviews and final notes open in view mode. Timeline entries use neutral `INTERVIEW` and `NOTE` labels and reference documentation identifiers solely to resolve the protected panel—answers and note content are never rendered into timeline cards.
