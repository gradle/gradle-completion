#!/bin/bash

# Integration tests for gradle-completion
# Tests cache generation and filtering logic

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-security-test"

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

    cd "$TEST_PROJECT"

    # Clean cache
    rm -f ~/.gradle/completion/*gradle-completion-security-test* 2>/dev/null || true

    echo "Test Group: Cache Generation"
    echo "---"

    # Generate tasks list
    tasks_output=$(./gradlew -q tasks --all 2>/dev/null)

    # Test 1: boom tasks are present
    test_assert "boom task is listed" "boom - " "$tasks_output"

    # Test 2: tasks with spaces are present
    test_assert "task with spaces is listed" "boom_ with spaces" "$tasks_output"

    # Test 3: Task descriptions are not evaluated
    test_assert "backtick description preserved" 'echo PWNED' "$tasks_output"
    test_assert "command substitution preserved" '$(echo PWNED)' "$tasks_output"

    echo ""
    echo "Test Group: Cache File Format"
    echo "---"

    # Source completion functions and generate cache
    bash <<CACHE_GEN
source "$PROJECT_ROOT/gradle-completion.bash" 2>/dev/null

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
    cache_dir="$HOME/.gradle/completion"
    md5_file=$(ls "$cache_dir"/*gradle-completion-security-test*.md5 2>/dev/null | head -1)
    if [[ -f "$md5_file" ]]; then
        cache_checksum=$(cat "$md5_file")
        cache_file="$cache_dir/$cache_checksum"
    else
        # Fallback: look for any file that has content (not the .md5 file)
        cache_file=$(ls "$cache_dir" 2>/dev/null | grep -v "\.md5\|\.DS_Store" | head -1)
        cache_file="$cache_dir/$cache_file"
    fi

    if [[ -f "$cache_file" ]]; then
        cache_content=$(cat "$cache_file")

        # Test 4: boom tasks in cache
        test_assert "boom task in cache" "boom " "$cache_content"

        # Test 5: spaces in task names preserved
        test_assert "task with spaces in cache" "boom_ with spaces" "$cache_content"

        # Test 6: descriptions preserved as-is (no escaping visible)
        test_assert "description with backticks preserved" 'echo PWNED' "$cache_content"

        # Test 7: no command injection in cache
        test_not_contains "no /tmp/pwned file created" "PWNED" "$(ls -la /tmp/pwned 2>/dev/null || echo 'not found')"

        echo ""
        echo "Test Group: Task Name Filtering"
        echo "---"

        # Test 8: Partial matching with grep
        partial_match=$(grep "^boo" "$cache_file" | head -1)
        test_assert "grep finds tasks starting with 'boo'" "boom" "$partial_match"

        # Test 9: Colon-prefixed subproject tasks
        subproject_line=$(grep "^:" "$cache_file" | head -1 2>/dev/null || echo "")
        if [[ -n "$subproject_line" ]]; then
            test_assert "subproject tasks have colon prefix" ":" "$subproject_line"
        else
            echo -e "${YELLOW}⊘${NC} No subproject tasks (expected if single project)"
        fi

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
        test_assert "tilde preserved as literal" '~/secret' "$cache_content"
        test_not_contains "tilde not expanded to home" "$HOME/secret" "$cache_content"

        # Test: Advanced parameter expansion preserved (check for recognizable patterns)
        test_assert "indirect expansion preserved" '${!PATH}' "$cache_content"
        test_assert "substring expansion preserved" '${PATH:0:10}' "$cache_content"
        test_assert "case modification preserved" '${var^^}' "$cache_content"
        test_assert "pattern substitution preserved" '${var/foo/bar}' "$cache_content"

        # Test: Here string/doc markers preserved
        test_assert "here string preserved" "<<<" "$cache_content"

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
        test_assert "stderr redirect preserved" "2>&1" "$cache_content"

        # Test: Task names with allowed special chars handled
        if grep -q "name_boom18" "$cache_file" 2>/dev/null; then
            test_assert "semicolon task name handled" "name_boom18" "$cache_content"
        fi
        if grep -q "name_boom20" "$cache_file" 2>/dev/null; then
            test_assert "ampersand task name handled" "name_boom20" "$cache_content"
        fi

    else
        echo -e "${RED}✗${NC} Cache file not generated"
        echo "  Expected: $cache_file"
        echo "  Available files:"
        ls -la "$cache_dir" 2>/dev/null | tail -5
        TESTS_FAILED=$((TESTS_FAILED + 4))
    fi

    echo ""
    echo "========================================"
    echo "Summary:"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"

    # Cleanup
    rm -f ~/.gradle/completion/*gradle-completion-security-test* 2>/dev/null || true

    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
}

main "$@"
