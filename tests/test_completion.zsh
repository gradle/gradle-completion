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
# Zsh completion tests for gradle-completion.
# Tests the _gradle zsh completion script: cache generation, security
# (no command injection from malicious task descriptions), and task
# description formatting.

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
NC='\033[0m'

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Cleanup function
cleanup_test_env() {
    # Clean test gradle home
    rm -rf "$GRADLE_USER_HOME_TEST" 2>/dev/null || true
    # Remove temporary gradlew symlink
    rm -f "$TEST_PROJECT/gradlew" 2>/dev/null || true
    # Ensure any leftover /tmp/pwned* files from previous runs are removed
    setopt local_options no_nomatch
    rm -f /tmp/pwned /tmp/pwned.txt /tmp/pwned.* 2>/dev/null || true
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

    # Create a temporary symlink so the completion script finds gradlew
    # in the test project root (it checks $project_root_dir/gradlew)
    ln -sf "$PROJECT_ROOT/gradlew" "$TEST_PROJECT/gradlew"

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
    md5_file=$(find "$cache_dir" -maxdepth 1 -name '*.md5' -print -quit 2>/dev/null)

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
    else
        echo -e "${RED}✗${NC} tasks cache file not readable, cannot verify contents"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        cache_content=""
    fi

    test_assert_contains "cache contains 'desc_backtick_cmd' task" "desc_backtick_cmd:" "cache_content"
    test_assert_contains "cache contains 'fmt_task_with_spaces' task" "fmt_task_with_spaces" "cache_content"

    echo ""
    echo "Test Group: Security - No File System Side Effects"
    echo "---"

    test_assert_file_not_exists "no /tmp/pwned file from backticks" "/tmp/pwned"
    test_assert_file_not_exists "no /tmp/pwned.txt file from variable expansion" "/tmp/pwned.txt"

    echo ""
    echo "Test Group: Security - Description Literals Preserved"
    echo "---"

    # Command substitution variants
    test_assert_contains "backtick description preserved" 'echo PWNED' "cache_content"
    test_assert_contains "dollar-paren syntax preserved" '$(echo PWNED)' "cache_content"
    test_assert_contains "curly brace expansion preserved" '${echo PWNED}' "cache_content"

    # Variable expansion
    test_assert_contains "USER variable preserved as literal" '$USER' "cache_content"
    test_assert_contains "HOME variable preserved as literal" '$HOME' "cache_content"
    test_assert_not_contains "USER not expanded to actual value" "$USER" "cache_content"
    test_assert_not_contains "HOME not expanded to actual value" "$HOME" "cache_content"

    # Arithmetic expansion
    test_assert_contains "arithmetic preserved as literal" '1337 + 2023' "cache_content"
    test_assert_not_contains "arithmetic not evaluated to 3360" "3360" "cache_content"

    # Nested and complex command substitution
    test_assert_contains "nested command sub preserved" '$(echo $(whoami))' "cache_content"
    test_assert_contains "conditional execution preserved" '$(test -f /etc/passwd' "cache_content"
    test_assert_contains "piped command sub preserved" "sed 's/test/pwned/'" "cache_content"
    test_assert_contains "subshell preserved" '$( (echo a; echo b) )' "cache_content"
    test_assert_contains "array subscript preserved" '${array[$(echo 0)]}' "cache_content"

    # Process substitution
    test_assert_contains "process sub input preserved" '<(echo pwned)' "cache_content"
    test_assert_contains "process sub output preserved" '>(cat' "cache_content"

    # Tilde expansion
    test_assert_contains "tilde preserved as literal" '~/secret' "cache_content"
    test_assert_contains "user tilde preserved" '~root/.bashrc' "cache_content"

    # Advanced parameter expansion
    test_assert_contains "indirect expansion preserved" '${!PATH}' "cache_content"
    test_assert_contains "substring expansion preserved" '${PATH:0:10}' "cache_content"
    test_assert_contains "case modification uppercase preserved" '${var^^}' "cache_content"
    test_assert_contains "case modification lowercase preserved" '${var,,}' "cache_content"
    test_assert_contains "pattern substitution preserved" '${var/foo/bar}' "cache_content"
    test_assert_contains "pattern deletion preserved" '${var#prefix}' "cache_content"
    test_assert_contains "default value expansion preserved" '${var:-$(echo pwned)}' "cache_content"

    # Here string/doc
    test_assert_contains "here string preserved" "<<<" "cache_content"
    test_assert_contains "heredoc marker preserved" "<<EOF" "cache_content"

    # Shell operators in descriptions
    test_assert_contains "pipe in description preserved" "echo secret | cat" "cache_content"
    test_assert_contains "redirect in description preserved" "> /tmp/file" "cache_content"
    test_assert_contains "stderr redirect preserved" "2>&1" "cache_content"

    # Combination attacks
    test_assert_contains "combo attack preserved" '$(echo $USER)' "cache_content"
    test_assert_contains "nested proc sub preserved" '$(cat <(echo secret))' "cache_content"

    echo ""
    echo "Test Group: Security - Name Injection (Stripped by Regex)"
    echo "---"

    # The zsh _gradle regex strips shell metacharacters from task names,
    # so only the prefix before the first forbidden character survives.
    # This is safe: the dangerous payload is never stored in the cache.
    test_assert_contains "semicolon name prefix in cache" 'name_semicolon' "cache_content"
    test_assert_contains "backtick name prefix in cache" 'name_backtick' "cache_content"
    test_assert_contains "dollar-paren name prefix in cache" 'name_dollar_cmd' "cache_content"
    test_assert_contains "curly brace name prefix in cache" 'name_curly_brace' "cache_content"
    test_assert_contains "var expansion name prefix in cache" 'name_var_expansion' "cache_content"
    test_assert_contains "arithmetic name prefix in cache" 'name_arithmetic' "cache_content"
    test_assert_contains "nested cmd sub name prefix in cache" 'name_nested_cmd' "cache_content"
    test_assert_contains "subshell name prefix in cache" 'name_subshell' "cache_content"
    test_assert_contains "brace expansion name prefix in cache" 'name_brace_expansion' "cache_content"
    test_assert_contains "history expansion name prefix in cache" 'name_history_expansion' "cache_content"

    echo ""
    echo "Test Group: Task Description Formatting"
    echo "---"

    test_assert_contains "descriptions use ':' separator" ":" "cache_content"

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
