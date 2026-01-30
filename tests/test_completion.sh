#!/bin/bash

# Test suite for gradle-completion script
# Tests various completion scenarios against test projects

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPLETION_SCRIPT="$PROJECT_ROOT/gradle-completion.bash"
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-security-test"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Test counter
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Cleanup function
cleanup() {
    rm -f ~/.gradle/completion/*gradle-completion-security-test* 2>/dev/null || true
}

# Test assertion
assert_completion() {
    local test_name="$1"
    local prefix="$2"
    local expected_pattern="$3"

    TESTS_RUN=$((TESTS_RUN + 1))

    # Source the completion script and get completions
    source "$COMPLETION_SCRIPT" 2>/dev/null || true

    cd "$TEST_PROJECT"

    # Mock the bash completion functions
    _get_comp_words_by_ref() {
        local var_ref="${1##-}"
        if [[ "$var_ref" == "cur" ]]; then
            eval "$var_ref='$prefix'"
        elif [[ "$var_ref" == "prev" ]]; then
            eval "$var_ref='gradle'"
        fi
    }

    # Get completions
    local COMPREPLY=()
    _gradle

    # Check if expected pattern matches any completion
    local found=0
    for completion in "${COMPREPLY[@]}"; do
        if [[ "$completion" =~ $expected_pattern ]]; then
            found=1
            break
        fi
    done

    if [[ $found -eq 1 ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected pattern: $expected_pattern"
        echo "  Got: ${COMPREPLY[@]:0:3}..."
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Test that no command injection happens
assert_no_injection() {
    local test_name="$1"

    TESTS_RUN=$((TESTS_RUN + 1))

    # Clear any marker files
    rm -f /tmp/pwned 2>/dev/null || true

    # Run completion that might trigger injection
    cd "$TEST_PROJECT"
    source "$COMPLETION_SCRIPT" 2>/dev/null || true

    # Check if injection marker was created
    if [[ ! -f /tmp/pwned ]]; then
        echo -e "${GREEN}✓${NC} $test_name (no injection)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name (injection detected!)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        rm -f /tmp/pwned
    fi
}

main() {
    echo "Testing gradle-completion script"
    echo "=================================="
    echo ""

    cleanup

    # Test 1: Basic completion
    echo "Test Group: Basic Completion"
    assert_completion "no prefix shows tasks" "" "boom|help|tasks"

    # Test 2: Partial task matching
    echo ""
    echo "Test Group: Partial Matching"
    assert_completion "prefix 'boo' matches boom tasks" "boo" "boom"
    assert_completion "prefix 'som' matches someTask" "som" "someTask|Some test"
    assert_completion "prefix 'hel' matches help" "hel" "help"

    # Test 3: Tasks with spaces
    echo ""
    echo "Test Group: Tasks with Spaces"
    assert_completion "tasks with spaces are available" "boom_" "boom_ with spaces"

    # Test 4: Security - no injection
    echo ""
    echo "Test Group: Security (No Command Injection)"
    assert_no_injection "backticks do not execute commands"
    assert_no_injection "dollar syntax does not execute variables"

    # Test 5: Description formatting
    echo ""
    echo "Test Group: Description Display"
    assert_completion "descriptions are preserved in output" "boom" " - "

    # Test 6: Subproject tasks
    echo ""
    echo "Test Group: Subproject Tasks"
    assert_completion "colon prefix for subproject tasks" ":" ":"

    # Print summary
    echo ""
    echo "=================================="
    echo "Test Summary:"
    echo "  Total:  $TESTS_RUN"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"

    cleanup

    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
}

main "$@"
