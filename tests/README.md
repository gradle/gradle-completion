# Tests

- **`integration_test.sh`** — tests the cache layer: generation, file format, task filtering, and security (shell metacharacters in task names/descriptions are never evaluated).
- **`test_completion.sh`** — tests the bash `_gradle` completion function end-to-end: calls `_gradle` with mocked input and checks `COMPREPLY`.
- **`test_completion.zsh`** — same as above but for the zsh `_gradle` script. Run via `zsh_test_runner.sh` (skips if zsh is unavailable).
- **`projects/gradle-completion-security-test/`** — Gradle project with 50+ malicious task names and descriptions (command substitution, variable expansion, process substitution, etc.) used by all test suites.

Run locally:
```bash
bash tests/integration_test.sh
bash tests/test_completion.sh
bash tests/zsh_test_runner.sh
```
