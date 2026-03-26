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
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-security-test"

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

    rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true
    run_completion "desc_backtick" "gradle"

    if [[ ! -f /tmp/pwned ]] && [[ ! -f /tmp/pwned.txt ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name (injection detected!)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        rm -f /tmp/pwned /tmp/pwned.txt 2>/dev/null || true
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
    echo "Test Group: Security (No Command Injection)"
    echo "---"
    assert_no_injection "completion does not execute injected commands"

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
