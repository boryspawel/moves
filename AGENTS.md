# Agent execution policy

## Continuous delivery

- Execute sequential prompts continuously. Do not end a turn, send a handover, or report partial completion while an in-scope implementation step remains.
- Stop only for a real blocker: missing authority, a user decision that materially changes scope, or an external state that cannot be changed safely.
- A failed command, malformed patch, or a failing test is work to diagnose and fix, not a reason to pause.

## IntelliJ MCP first

- Use the configured IntelliJ MCP server for code navigation, symbol and call analysis, dependency inspection, refactoring, inspections, builds, and test run configurations whenever it exposes a suitable operation.
- Pass this project path to every IntelliJ MCP call: `/home/pb/Documents/projects/moves`.
- Use shell tools only when IntelliJ MCP does not expose the operation or when a direct command is required by the repository workflow. State the fallback only in the final report.

## Token and delegation discipline

- Prefer targeted IntelliJ queries and narrow file reads over broad repository dumps.
- For independent, bounded reconnaissance, test diagnosis, or review tasks, delegate to a lower-cost subagent (`gpt-5.6-terra`, low reasoning effort). Keep cross-module design, edits, and final integration with the primary agent.
- Do not delegate when coordination would cost more context than the task saves.
- Subagents must return concise, evidence-backed findings; the primary agent remains responsible for integration and validation.

## Validation

- Before declaring an implementation complete, run the relevant tests, `git diff --check`, and the project-prescribed validation suite.
