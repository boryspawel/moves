# Web

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.0.7.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## End-to-end tests

The Playwright suite exercises the deployed frontend, Keycloak Authorization Code + PKCE login and the real backend. It never intercepts API calls or supplies test identities in source control. Screenshots, traces and videos are written under `test-results/` and are ignored by Git.

Set a base URL and existing participant credentials before running it:

```bash
export E2E_BASE_URL=http://localhost:4200
export E2E_PARTICIPANT_USERNAME='existing-participant'
export E2E_PARTICIPANT_PASSWORD='existing-participant-password'
npx playwright install chromium
npm run test:e2e
```

`E2E_BASE_URL` defaults to `http://localhost:4200`. The participant must already be at onboarding stage `READY`; the suite verifies that state, refreshes the authenticated browser session, checks navigation to Sessions, keyboard operation, axe accessibility, 390 px and 320 px layouts, and a real backend error/retry path using an invalid catalog version identifier.

Optional independent accounts are supported and are the only scenarios that intentionally skip when absent:

```bash
# Existing specialist account for the worklist navigation check.
export E2E_SPECIALIST_USERNAME='existing-specialist'
export E2E_SPECIALIST_PASSWORD='existing-specialist-password'

# Separate participant that has a planned session that can be started normally.
export E2E_RESUME_PARTICIPANT_USERNAME='participant-with-startable-session'
export E2E_RESUME_PARTICIPANT_PASSWORD='participant-with-startable-session-password'
```

The resume account must be separate because the test starts, pauses and refreshes an attempt through the normal UI and must not depend on mutable shared participant data. No plan, session or legal state is created by the suite.

### Running against Compose

Start the stack from the repository root, then run the suite from this directory. Use credentials from your local `.env`/Keycloak setup; do not copy them into this README or CI logs.

```bash
docker compose up --build
cd web
export E2E_BASE_URL=http://localhost:4200
export E2E_PARTICIPANT_USERNAME='your-local-participant'
export E2E_PARTICIPANT_PASSWORD='your-local-password'
npx playwright install chromium
npm run test:e2e
```

For a different Compose frontend port, set `E2E_BASE_URL` to its public origin and ensure that origin is configured in the Keycloak client's redirect URIs before the realm is imported.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
