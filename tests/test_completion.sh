#!/bin/bash

# End-to-end tests for the _gradle bash completion function.
# Pre-generates the cache, then calls _gradle with mocked cur/prev values
# and inspects COMPREPLY for correct results: task matching, option completion,
# and command injection safety.
# See integration_test.sh for cache-level tests.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPLETION_SCRIPT="$PROJECT_ROOT/gradle-completion.bash"
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-test-build"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Create isolated test environment to avoid conflicts with parallel test runs
TEST_TMPDIR=$(mktemp -d)
export GRADLE_USER_HOME="$TEST_TMPDIR/gradle-home"
mkdir -p "$GRADLE_USER_HOME"
WORK_DIR="$TEST_TMPDIR/project"
cp -r "$TEST_PROJECT" "$WORK_DIR"
trap 'rm -rf "$TEST_TMPDIR"' EXIT

# Cleanup function
cleanup() {
    rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true
}

# Pre-generate the completion cache so _gradle can read from it
# without spawning a background Gradle process
generate_cache() {
    ln -sf "$PROJECT_ROOT/gradlew" "$WORK_DIR/gradlew"

    bash -e <<CACHE_GEN
export GRADLE_USER_HOME="$GRADLE_USER_HOME"
source "$COMPLETION_SCRIPT" 2>/dev/null
cd "$WORK_DIR"
__gradle-init-cache-dir
__gradle-set-project-root-dir
__gradle-set-build-file
__gradle-set-cache-name
__gradle-generate-script-cache
__gradle-set-files-checksum
__gradle-generate-tasks-cache 2>/dev/null
CACHE_GEN
}

# Mock _get_comp_words_by_ref to simulate bash-completion framework
# Call 1: _get_comp_words_by_ref -n : cur     → sets cur=$_mock_cur
# Call 2: _get_comp_words_by_ref -n : -p prev → sets prev=$_mock_prev
_get_comp_words_by_ref() {
    local _is_prev=0
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n) shift ;; # skip delimiter arg (e.g. ':')
            -p) _is_prev=1 ;;
            *)
                if [[ $_is_prev -eq 1 ]]; then
                    printf -v "$1" '%s' "$_mock_prev"
                    _is_prev=0
                else
                    printf -v "$1" '%s' "$_mock_cur"
                fi
                ;;
        esac
        shift
    done
}

# Mock __ltrim_colon_completions (no-op for tests)
__ltrim_colon_completions() { :; }

# Source the completion script (once, after mocks are defined)
source "$COMPLETION_SCRIPT" 2>/dev/null

# Run _gradle and capture COMPREPLY
# Usage: run_completion "cur" "prev"
run_completion() {
    _mock_cur="$1"
    _mock_prev="${2:-gradle}"
    COMPREPLY=()
    cd "$WORK_DIR"
    _gradle
}

assert_completion_contains() {
    local test_name="$1"
    local expected="$2"

    local found=0
    for entry in "${COMPREPLY[@]}"; do
        if [[ "$entry" == *"$expected"* ]]; then
            found=1
            break
        fi
    done

    if [[ $found -eq 1 ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected to find: $expected"
        echo "  COMPREPLY (first 5): ${COMPREPLY[*]:0:5}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

assert_completion_not_empty() {
    local test_name="$1"

    if [[ ${#COMPREPLY[@]} -gt 0 ]]; then
        echo -e "${GREEN}✓${NC} $test_name (${#COMPREPLY[@]} results)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name (empty COMPREPLY)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

assert_no_injection() {
    local test_name="$1"
    local prefix="$2"

    rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true
    run_completion "$prefix" "gradle"

    if [[ ! -f /tmp/pwned ]] && [[ ! -f /tmp/pwned.txt ]] && ! ls /tmp/pwned* >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name (injection detected!)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true
    fi
}



main() {
    echo "Bash Completion Function Tests"
    echo "=================================="
    echo ""

    cleanup
    echo "Generating cache..."
    generate_cache
    echo ""

    echo "Test Group: Basic Completion"
    echo "---"
    run_completion "" "gradle"
    assert_completion_not_empty "empty prefix returns tasks"
    assert_completion_contains "desc_backtick_cmd task in results" "desc_backtick_cmd"

    echo ""
    echo "Test Group: Partial Matching"
    echo "---"
    run_completion "desc_b" "gradle"
    assert_completion_contains "prefix 'desc_b' matches desc_backtick_cmd" "desc_backtick_cmd"
    assert_completion_not_empty "partial match returns results"

    run_completion "hel" "gradle"
    assert_completion_contains "prefix 'hel' matches help" "help"

    echo ""
    echo "Test Group: Tasks with Descriptions"
    echo "---"
    run_completion "desc_c" "gradle"
    assert_completion_contains "descriptions included with separator" " - "

    echo ""
    echo "Test Group: Long Options"
    echo "---"
    run_completion "--" "gradle"
    assert_completion_not_empty "double-dash returns long options"
    assert_completion_contains "help option available" "--help"
    assert_completion_contains "stacktrace option available" "--stacktrace"

    echo ""
    echo "Test Group: Short Options"
    echo "---"
    run_completion "-" "gradle"
    assert_completion_not_empty "single-dash returns short options"

    echo ""
    echo "Test Group: Properties"
    echo "---"
    run_completion "-D" "gradle"
    assert_completion_not_empty "-D prefix returns properties"

    echo ""
    echo "Test Group: Security - Description Injection (No Side Effects)"
    echo "---"
    assert_no_injection "backtick command substitution" "desc_backtick"
    assert_no_injection "dollar-paren command substitution" "desc_dollar"
    assert_no_injection "curly brace expansion" "desc_curly"
    assert_no_injection "variable expansion" "desc_var"
    assert_no_injection "command with redirect" "desc_cmd_with"
    assert_no_injection "command chaining" "desc_cmd_chaining"
    assert_no_injection "command loop" "desc_cmd_loop"
    assert_no_injection "data exfiltration" "desc_data"
    assert_no_injection "arithmetic expansion" "desc_arith"
    assert_no_injection "nested command substitution" "desc_nested"
    assert_no_injection "parameter default with cmd sub" "desc_param_default"
    assert_no_injection "conditional execution" "desc_conditional"
    assert_no_injection "piped command substitution" "desc_piped"
    assert_no_injection "array subscript with cmd sub" "desc_array"
    assert_no_injection "subshell execution" "desc_subshell"
    assert_no_injection "process substitution input" "desc_proc_sub_i"
    assert_no_injection "process substitution output" "desc_proc_sub_o"
    assert_no_injection "tilde expansion home" "desc_tilde"
    assert_no_injection "indirect variable expansion" "desc_param_indirect"
    assert_no_injection "substring expansion" "desc_param_substring"
    assert_no_injection "case modification" "desc_case"
    assert_no_injection "pattern substitution" "desc_pattern"
    assert_no_injection "here string/doc" "desc_here"
    assert_no_injection "pipe operator" "desc_pipe"
    assert_no_injection "redirect operator" "desc_redirect"
    assert_no_injection "control characters" "desc_ctrl"
    assert_no_injection "ANSI escape codes" "desc_ansi"
    assert_no_injection "combination attacks" "desc_combo"
    assert_no_injection "quote escape" "desc_quote"

    echo ""
    echo "Test Group: Security - Name Injection (No Side Effects)"
    echo "---"
    assert_no_injection "backtick in task name" "name_backtick"
    assert_no_injection "dollar-paren in task name" "name_dollar_cmd"
    assert_no_injection "curly brace in task name" "name_curly"
    assert_no_injection "variable expansion in task name" "name_var"
    assert_no_injection "command chaining in task name" "name_cmd_chaining"
    assert_no_injection "command loop in task name" "name_cmd_loop"
    assert_no_injection "arithmetic in task name" "name_arith"
    assert_no_injection "nested cmd sub in task name" "name_nested"
    assert_no_injection "array subscript in task name" "name_array"
    assert_no_injection "subshell in task name" "name_subshell"
    assert_no_injection "semicolon in task name" "name_semicolon"
    assert_no_injection "background process in task name" "name_background"
    assert_no_injection "brace expansion in task name" "name_brace"
    assert_no_injection "history expansion in task name" "name_history"
    assert_no_injection "newline injection in task name" "name_newline"
    assert_no_injection "dollar sign in task name" "name_dollar\$"

    # Print summary
    echo ""
    echo "=================================="
    echo "Test Summary:"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"

    cleanup

    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
}

main "$@"
