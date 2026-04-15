#!/bin/bash
# shellcheck disable=SC2016
#^-- We want to preserve the literal string in the test assertions, because that is what we want to test for.

# Integration tests for gradle-completion
# Tests the cache layer: generation, file format, task name filtering,
# and that shell metacharacters in task names/descriptions are never evaluated.
# Does NOT call _gradle directly — see test_completion.sh for that.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-test-build"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

test_assert() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"

    if [[ "$actual" == *"$expected"* ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected: $expected"
        echo "  Got: $actual"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_not_contains() {
    local test_name="$1"
    local unexpected="$2"
    local actual="$3"

    if [[ "$actual" != *"$unexpected"* ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Should not contain: $unexpected"
        echo "  But got: $actual"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

main() {
    echo "Integration Tests for gradle-completion"
    echo "========================================"
    echo ""

    # Create isolated test environment to avoid conflicts with parallel test runs
    TEST_TMPDIR=$(mktemp -d)
    export GRADLE_USER_HOME="$TEST_TMPDIR/gradle-home"
    mkdir -p "$GRADLE_USER_HOME"
    WORK_DIR="$TEST_TMPDIR/project"
    cp -r "$TEST_PROJECT" "$WORK_DIR"
    trap 'rm -rf "$TEST_TMPDIR"' EXIT

    GRADLE_COMPLETION_CACHE_DIR="$GRADLE_USER_HOME/completion"
    rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true

    echo "Test Group: Cache Generation"
    echo "---"

    # Generate tasks list
    tasks_output=$("$PROJECT_ROOT/gradlew" -p "$TEST_PROJECT" -q tasks --all 2>/dev/null)

    test_assert "desc_backtick_cmd task is listed" "desc_backtick_cmd - " "$tasks_output"
    test_assert "task with spaces is listed" "fmt_task_with_spaces" "$tasks_output"

    # Task descriptions are not evaluated
    test_assert "backtick description preserved" 'echo PWNED' "$tasks_output"
    test_assert "command substitution preserved" '$(echo PWNED)' "$tasks_output"

    echo ""
    echo "Test Group: Cache File Format"
    echo "---"

    # Create a symlink so the completion script finds gradlew
    ln -sf "$PROJECT_ROOT/gradlew" "$WORK_DIR/gradlew"

    # Source completion functions and generate cache
    bash -e <<CACHE_GEN
export GRADLE_USER_HOME="$GRADLE_USER_HOME"
source "$PROJECT_ROOT/gradle-completion.bash" 2>/dev/null

# Must run in test project directory for completion functions to work
cd "$WORK_DIR"

# Initialize and generate cache
__gradle-init-cache-dir
__gradle-set-project-root-dir
__gradle-set-build-file
__gradle-set-cache-name
__gradle-generate-script-cache
__gradle-set-files-checksum
__gradle-generate-tasks-cache 2>/dev/null
CACHE_GEN

    # Get the actual cache file (stored under checksum name)
    # First find the .md5 file to get the checksum
    md5_file=$(find "$GRADLE_COMPLETION_CACHE_DIR" -maxdepth 1 -name '*.md5' -print -quit 2>/dev/null)
    if [[ -f "$md5_file" ]]; then
        cache_checksum=$(cat "$md5_file")
        cache_file="$GRADLE_COMPLETION_CACHE_DIR/$cache_checksum"
    else
        # Fallback: look for any file that has content (not the .md5 file)
        cache_file=$(find "$GRADLE_COMPLETION_CACHE_DIR" -maxdepth 1 -type f ! -name '*.md5' ! -name '.DS_Store' -print -quit 2>/dev/null)
    fi

    if [[ -f "$cache_file" ]]; then
        cache_content=$(cat "$cache_file")

        test_assert "desc_backtick_cmd task in cache" "desc_backtick_cmd " "$cache_content"
        test_assert "task with spaces in cache" "fmt_task_with_spaces" "$cache_content"
        test_assert "description with backticks preserved" 'echo PWNED' "$cache_content"

        echo ""
        echo "Test Group: Task Name Filtering"
        echo "---"

        # Partial matching with grep
        partial_match=$(grep "^desc_b" "$cache_file" | head -1)
        test_assert "grep finds tasks starting with 'desc_b'" "desc_backtick_cmd" "$partial_match"

        # Colon-prefixed subproject tasks
        subproject_line=$(grep "^:" "$cache_file" | head -1)
        test_assert "subproject tasks have colon prefix" ":sub:" "$subproject_line"

        echo ""
        echo "Test Group: Security - No Shell Expansion"
        echo "---"

        # Test: Variable expansion did NOT occur (values should be literal $USER not expanded)
        # We check that literal dollar signs are preserved in the cache
        test_assert "USER variable preserved as literal" '$USER' "$cache_content"
        test_assert "HOME variable preserved as literal" '$HOME' "$cache_content"
        # Ensure actual username/home path didn't leak into cache
        test_not_contains "USER not expanded to actual value" "$USER" "$cache_content"
        test_not_contains "HOME not expanded to actual value" "$HOME" "$cache_content"

        # Test: Arithmetic expansion did NOT occur
        test_assert "arithmetic preserved as literal" '1337 + 2023' "$cache_content"
        test_not_contains "arithmetic not evaluated to 3360" "3360" "$cache_content"

        # Test: Command substitution preserved as literal
        test_assert "backtick command preserved" 'echo PWNED' "$cache_content"
        test_assert "dollar-paren command preserved" '$(echo PWNED)' "$cache_content"

        # Test: Process substitution preserved as literal
        test_assert "process sub input preserved" '<(echo pwned)' "$cache_content"
        test_assert "process sub output preserved" '>(cat' "$cache_content"

        # Test: Tilde expansion did NOT occur
        # shellcheck disable=SC2088
        test_assert "tilde preserved as literal" '~/secret' "$cache_content"
        test_not_contains "tilde not expanded to home" "$HOME/secret" "$cache_content"

        # Test: Advanced parameter expansion preserved (check for recognizable patterns)
        test_assert "indirect expansion preserved" '${!PATH}' "$cache_content"
        test_assert "substring expansion preserved" '${PATH:0:10}' "$cache_content"
        test_assert "case modification uppercase preserved" '${var^^}' "$cache_content"
        test_assert "case modification lowercase preserved" '${var,,}' "$cache_content"
        test_assert "pattern substitution preserved" '${var/foo/bar}' "$cache_content"
        test_assert "pattern deletion preserved" '${var#prefix}' "$cache_content"
        test_assert "default value expansion preserved" '${var:-$(echo pwned)}' "$cache_content"

        # Test: Here string/doc markers preserved
        test_assert "here string preserved" "<<<" "$cache_content"
        test_assert "heredoc marker preserved" "<<EOF" "$cache_content"

        # Test: Nested and complex command substitution preserved
        test_assert "nested command sub preserved" '$(echo $(whoami))' "$cache_content"
        test_assert "conditional execution preserved" '$(test -f /etc/passwd' "$cache_content"
        test_assert "piped command sub preserved" "sed 's/test/pwned/'" "$cache_content"
        test_assert "subshell preserved" '$( (echo a; echo b) )' "$cache_content"
        test_assert "array subscript preserved" '${array[$(echo 0)]}' "$cache_content"

        # Test: Combination attacks preserved as literals
        test_assert "combo attack preserved" '$(echo $USER)' "$cache_content"
        test_assert "nested proc sub preserved" '$(cat <(echo secret))' "$cache_content"

        # Test: Tilde with username preserved
        test_assert "user tilde preserved" '~root/.bashrc' "$cache_content"
        test_not_contains "user tilde not expanded" "/var/root/.bashrc" "$cache_content"

        echo ""
        echo "Test Group: Security - No File System Side Effects"
        echo "---"

        # Test: No files created by malicious descriptions
        # Check if actual file exists (not the error message)
        if [[ -f /tmp/pwned ]] || [[ -f /tmp/pwned.txt ]] || ls /tmp/pwned* >/dev/null 2>&1; then
            echo -e "${RED}✗${NC} no /tmp/pwned file created"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        else
            echo -e "${GREEN}✓${NC} no /tmp/pwned file created"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        fi

        # Test: No evidence of command execution in unexpected places
        test_not_contains "no PWNED marker in output" "PWNED" "$(echo "$tasks_output" | grep -v "echo PWNED" | grep -v "description" || echo '')"

        echo ""
        echo "Test Group: Shell Operators in Descriptions"
        echo "---"

        # Test: Descriptions with shell operators preserved (not executed)
        # Note: Gradle forbids |, <, >, etc. in task NAMES, but allows them in descriptions
        test_assert "pipe in description preserved" "echo secret | cat" "$cache_content"
        test_assert "redirect in description preserved" "> /tmp/file" "$cache_content"
        test_assert "input redirect preserved" "< /etc/passwd" "$cache_content"
        test_assert "append redirect preserved" ">> /tmp/file" "$cache_content"
        test_assert "stderr redirect preserved" "2>&1" "$cache_content"

        echo ""
        echo "Test Group: Control Characters in Descriptions"
        echo "---"

        # Test: ANSI escape sequences preserved and not interpreted
        test_assert "ANSI color code preserved" "31m" "$cache_content"
        test_assert "ANSI clear screen preserved" "2J" "$cache_content"

        echo ""
        echo "Test Group: Quote Handling in Descriptions"
        echo "---"

        test_assert "escaped double quote preserved" 'escaped quote' "$cache_content"
        test_assert "mixed quotes preserved" "double" "$cache_content"

        echo ""
        echo "Test Group: Attack Vectors in Task Names"
        echo "---"

        # Test: Task names with shell metacharacters are preserved as literals in cache.
        # The dangerous part (after ; or &) must appear literally, not be executed.
        test_assert "semicolon preserved literally in name" 'name_semicolon; echo PWNED' "$cache_content"
        test_assert "ampersand preserved literally in name" 'name_background & echo PWNED' "$cache_content"
        test_assert "backtick preserved literally in name" 'name_backtick `echo PWNED`' "$cache_content"
        test_assert "dollar-paren preserved literally in name" 'name_dollar_cmd $(echo PWNED)' "$cache_content"
        test_assert "curly brace preserved literally in name" 'name_curly_brace ${echo PWNED}' "$cache_content"
        test_assert "brace expansion preserved literally in name" 'name_brace_expansion {a,b,c}' "$cache_content"
        test_assert "history expansion preserved literally in name" 'name_history_expansion !!' "$cache_content"
        test_assert "single quotes preserved literally in name" "name_single_quotes 'single' quotes" "$cache_content"
        test_assert "nested cmd sub preserved literally in name" 'name_nested_cmd $(echo $(whoami))' "$cache_content"
        test_assert "cmd chaining preserved literally in name" 'name_cmd_chaining $(echo step1 && echo step2)' "$cache_content"
        test_assert "subshell preserved literally in name" 'name_subshell $( (echo a; echo b) )' "$cache_content"
        test_assert "var expansion preserved literally in name" 'name_var_expansion $USER $HOME' "$cache_content"
        test_assert "arithmetic preserved literally in name" 'name_arithmetic $((1337 + 2023))' "$cache_content"

        # Newline injection: the \n splits into two cache lines.
        # Verify the second line ("echo PWNED") is NOT a valid task that would execute.
        # It ends up as a literal cache entry, which is safe since we never eval cache content.
        test_assert "newline-injected name truncated at newline" "name_newline" "$cache_content"

    else
        echo -e "${RED}✗${NC} Cache file not generated"
        echo "  Expected: $cache_file"
        echo "  Available files:"
        find "$GRADLE_COMPLETION_CACHE_DIR" -maxdepth 1 -type f -print 2>/dev/null | tail -5
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi

    echo ""
    echo "========================================"
    echo "Summary:"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"

    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
}

main "$@"
