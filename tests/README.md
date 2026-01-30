# Test Suite for gradle-completion

This directory contains tests for the gradle-completion script to ensure it works correctly and remains secure against command injection attacks.

## Test Files

### integration_test.sh
Main integration test suite that validates:
- Cache generation and file format
- Task name parsing (including spaces)
- Partial task matching via grep
- Security: no shell expansion or command injection
- Description preservation

**Run locally:**
```bash
bash tests/integration_test.sh
```

### test_completion.sh
Bash completion function tests (requires full bash completion environment).

**Run locally:**
```bash
bash tests/test_completion.sh
```

### test_completion.zsh & zsh_test_runner.sh
Zsh completion function tests for validating the `_gradle` completion script.

**test_completion.zsh** validates:
- Cache generation and task caching
- Security: no command injection from malicious task descriptions
- Proper task description formatting

**zsh_test_runner.sh** is the runner script that executes the zsh tests (automatically skips if zsh is not available).

**Run locally:**
```bash
bash tests/zsh_test_runner.sh
```

All 9 tests pass successfully, validating that the zsh completion script properly:
- Generates and caches gradle tasks
- Preserves malicious code in descriptions without executing it
- Formats task output with proper separators

### Audit.md
Comprehensive security audit documenting vulnerabilities found during the security review of issue #91. Includes severity levels, remediation priorities, and descriptions of potential attack vectors.

## Test Project

### `tests/projects/gradle-completion-security-test/`

A Gradle project with malicious task names and descriptions for security testing:

**Attack vectors in descriptions:**
- Backtick command substitution: `` `echo PWNED` ``
- Dollar-paren substitution: `$(echo PWNED)`
- Variable expansion: `$USER`, `$HOME`, `${var}`
- Process substitution: `<(cmd)`, `>(cmd)`
- Tilde expansion: `~/path`
- Parameter expansion: `${!var}`, `${var^^}`, `${var/p/r}`
- Command chaining: `$(cmd1 && cmd2)`
- Pipes and redirects: `|`, `>`, `<`, `>>`
- Here strings: `<<<`
- Arithmetic: `$((1+1))`
- Control characters: ANSI escapes, null bytes, newlines

**Attack vectors in task names:**
- Semicolon separator: `name; echo PWNED`
- Background process: `name & echo PWNED`
- Brace/history expansion: `{a,b}`, `!!`
- Newline injection

## CI Testing

The test suite runs automatically on:
- **Pull requests** to `master`
- When changes affect: `gradle-completion.bash`, `gradle-completion.bash.template`, or `tests/**`

**Platforms tested:**
- Ubuntu (latest)
- macOS (latest)

## Security Tests

The test suite verifies that the completion script:
1. Does not execute backticks (`` ` ``)
2. Does not execute command substitution (`$()`)
3. Does not expand variables (`$USER` remains literal)
4. Does not evaluate arithmetic (`$((1+1))` remains literal)
5. Does not expand tildes (`~` remains literal)
6. Does not execute process substitution (`<()`, `>()`)
7. Does not create files via injection attempts
8. Preserves all shell metacharacters as literal text

## Test Coverage

- Cache generation and format validation
- Partial task matching (e.g., "boo" → "boom")
- Tasks with spaces in names
- Tasks without descriptions
- Subproject task handling
- Shell operator preservation in descriptions
- No environment variable leakage
- No file system side effects
