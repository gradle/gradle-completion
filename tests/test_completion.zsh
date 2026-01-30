#!/usr/bin/env zsh
# shellcheck disable=SC2154,SC2296,SC2181,SC2317,SC1112,SC2316,SC1091,SC2034,SC2086
# Note: This is a zsh-specific test file. Shellcheck has limited zsh support.
# SC2154: Variables from sourced _gradle script
# SC2296: Zsh parameter expansion (P) flag
# SC2181: Zsh condition syntax
# SC2317: Functions referenced indirectly
# SC1112: Zsh-specific syntax
# SC2316: Function parameter expansion
# SC1091: Can't follow sourced file
# SC2034: Variables appear unused (used indirectly)
# SC2086: Quote expansion in zsh
# Zsh completion tests for gradle-completion
# Tests various completion scenarios against test projects

# Setup test environment
emulate -L zsh
setopt local_options no_null_glob

SCRIPT_DIR=${0:a:h}
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_PROJECT="$SCRIPT_DIR/projects/gradle-completion-security-test"
GRADLE_USER_HOME_TEST="$SCRIPT_DIR/.gradle-test"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'  # shellcheck disable=SC2034  # Reserved for future use
NC='\033[0m'

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Cleanup function
cleanup_test_env() {
    # Clean test gradle home
    rm -rf "$GRADLE_USER_HOME_TEST" 2>/dev/null || true
}

# Test assertion function - check if item is in completion results
test_assert_contains() {
    local test_name="$1"
    local expected="$2"
    local actual_var="$3"

    local actual
    actual="${(P)actual_var}" # Dereference variable name

    if [[ "$actual" == *"$expected"* ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected to find: $expected"
        echo "  In: $actual"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Test assertion function - check if item is NOT in completion results
test_assert_not_contains() {
    local test_name="$1"
    local unexpected="$2"
    local actual_var="$3"

    local actual
    actual="${(P)actual_var}" # Dereference variable name

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

# Test assertion function - check file exists
test_assert_file_exists() {
    local test_name="$1"
    local filepath="$2"

    if [[ -f "$filepath" ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected file to exist: $filepath"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Test assertion function - check file does NOT exist
test_assert_file_not_exists() {
    local test_name="$1"
    local filepath="$2"

    if [[ ! -f "$filepath" ]]; then
        echo -e "${GREEN}✓${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected file NOT to exist: $filepath"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

main() {
    echo "Zsh Completion Tests for gradle-completion"
    echo "=========================================="
    echo ""

    # Clean test environment before starting
    cleanup_test_env
    mkdir -p "$GRADLE_USER_HOME_TEST"

    # Navigate to the test project
    cd "$TEST_PROJECT" || { echo "Failed to navigate to test project"; exit 1; }

    # --- Test Group: Cache Generation ---
    echo "Test Group: Cache Generation"
    echo "---"

    # Source completion script with test gradle home
    export GRADLE_USER_HOME="$GRADLE_USER_HOME_TEST"
    source "$PROJECT_ROOT/_gradle" 2>/dev/null

    # Initialize and generate cache
    __gradle-init-cache-dir
    __gradle-set-project-root-dir
    __gradle-set-build-file
    __gradle-set-cache-name
    __gradle-generate-script-cache
    __gradle-set-files-checksum

    # Verify script cache file was created (which implies cache directory exists)
    test_assert_file_exists "script cache file created" "$cache_dir/$cache_name"

    # Generate tasks cache
    __gradle-generate-tasks-cache 2>/dev/null

    # Find the actual tasks cache file by reading the .md5 file
    # The .md5 file contains the checksum which is the name of the tasks cache file
    local md5_file=""
    md5_file=$(ls "$cache_dir"/*.md5 2>/dev/null | head -1)

    local tasks_cache_file=""
    local cache_content=""

    if [[ -f "$md5_file" ]]; then
        local cache_checksum
        cache_checksum=$(<"$md5_file")
        tasks_cache_file="$cache_dir/$cache_checksum"
    fi

    test_assert_file_exists "tasks cache file created" "$tasks_cache_file"

    # Verify cache contains expected tasks
    if [[ -f "$tasks_cache_file" ]]; then
        cache_content=$(<"$tasks_cache_file")

        test_assert_contains "cache contains 'boom' task" "boom:" "cache_content"
        test_assert_contains "cache contains 'boom_ with spaces' task" "boom_" "cache_content"
    fi

    echo ""
    echo "Test Group: Security (No Command Injection)"
    echo "---"

    # Verify no injection from backticks
    test_assert_file_not_exists "no /tmp/pwned file from backticks" "/tmp/pwned"
    test_assert_file_not_exists "no /tmp/pwned.txt file from variable expansion" "/tmp/pwned.txt"

    # Verify malicious descriptions are preserved (not executed)
    if [[ -n "$cache_content" ]]; then
        test_assert_contains "backtick description preserved" 'echo PWNED' "cache_content"
        test_assert_contains "dollar syntax preserved" '$(echo PWNED)' "cache_content"
    fi

    echo ""
    echo "Test Group: Task Description Formatting"
    echo "---"

    if [[ -n "$cache_content" ]]; then
        test_assert_contains "descriptions use ':' separator" ":" "cache_content"
    fi

    # Cleanup
    echo ""
    echo "=========================================="
    echo "Summary:"
    echo "  Total:  $((TESTS_PASSED + TESTS_FAILED))"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"

    cleanup_test_env

    # Return exit code
    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
    exit 0
}

main "$@"
