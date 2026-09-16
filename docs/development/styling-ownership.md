# Styling ownership

- `web/src/styles/` owns tokens, Material configuration, resets, and explicit reusable primitives (`.panel`, `.card`, `.app-summary`, `.app-native-control`). Use a primitive only when its layout is intended; do not add broad element selectors.
- `web/src/app/shell.scss` owns the application toolbar, primary navigation, and footer. Navigation uses `.app-primary-nav` and `.app-nav-active`; component-local `.active` states are unrelated.
- Feature styles stay component-scoped. The specialist workspace page owns only root layout/state/attention rules; its header, summary, timeline, dialog, goals, and documentation components own their rendered styles through their own `styleUrl` files. Do not restore `ViewEncapsulation.None` or `@scope` as a workaround for component-style budgets.
- Native text, number, date, select, and textarea controls may opt in to `.app-native-control`. Checkboxes, radio controls, and Material-managed inputs must not use it.
- Dialog sizing and control targets use `--app-dialog-max-width` and `--app-control-min-height`. Calendar slots retain their denser geometry; changing their hit target needs a separate interaction/layout decision.

## Manual QA checklist

- Verify participant, specialist, and combined-role primary navigation at 320px, 768px, and desktop: links remain keyboard reachable, horizontally scrollable, and no focus outline is clipped.
- Verify client, Today, and workspace dialogs: close/action controls, focus visibility, invalid native-control state, disabled state, textarea resizing, and a long dialog on a short viewport.
- Verify workspace header, summary strip, filters, timeline cards, selected-event panel, goals, and documentation panel independently after their component-style ownership split.
- Verify onboarding availability duration and catalog-admin text/textarea controls at 320px, 768px, and desktop.
- Verify the specialist workspace, BodyMap warning state, exercise-set native fields, plan forms, and login card after route changes. No visual QA is implied by source or automated tests.
