#!/usr/bin/env bash

SELF_UPDATE_URL="https://raw.githubusercontent.com/meonlol/t-bash/master/runTests.sh"

usage() {
cat << EOF
T-Bash   v0.4
A tiny bash testing framework.

Loads all files in the cwd that are prefixed with 'test_', and then executes
all functions that are prefixed with 'test_' in those files. Large tests are
prefixed with 'testLarge_' and are only executed with the -a flag.
Currently only supports 'assertEquals' test, all other cases can be tested
using simple if statements and the 'fail' method.

Usage:
./runTests.sh [-hvatm] [test_files...]

-h                Print this help
-v                verbose
-a                Run all tests, including those prefixed with testLarge_
-t                Prints how long each test took
-m [testmatcher]  Runs only the tests that match the string (bash matches supported)
-u                Execute a self-update
EOF
exit
}

main() {

  # adding : behind the command will require arguments
  while getopts "vhatm:u" opt; do
    case $opt in
      h)
        usage
        ;;
      v)
        export VERBOSE=true
        ;;
      a)
        export RUN_LARGE_TESTS=true
        ;;
      t)
        export TIMED=true
        ;;
      m)
        export MATCH="$OPTARG"
        ;;
      u)
        runSelfUpdate
        exit
        ;;
      *)
        usage
        ;;
    esac
  done

  shift "$((OPTIND - 1))"
  if [[ "$@" != "" ]]; then
    TEST_FILES=($@)
  else
    TEST_FILES=($(echo ./test_*))
  fi

  TEST_FILE_COUNT=${#TEST_FILES[@]}
  FAILING_TESTS=0

  for test_file in ${TEST_FILES[@]}; do
    logv "running $test_file"

    # Load the test files in a sub-shell, to prevent overwriting functions
    # between files (mainly setup & teardown)
    (callTestsInFile $test_file)
    ((FAILING_TESTS+=$?)) # Will be 0 if no tests failed.
  done

  if [[ $FAILING_TESTS > 0 ]]; then
    echo $FAILING_TESTS failing tests in $TEST_FILE_COUNT files
    echo TEST SUITE FAILED
    exit 1
  else
    echo suite successfull
  fi
}

callTestsInFile() {
  source $1

  finishedTestCount=0
  [[ ! $VERBOSE ]] && initDotLine

  for currFunc in $(compgen -A function); do
    local output
    ((finishedTestCount+=1))
    if [[ $currFunc == "test_"* || $RUN_LARGE_TESTS && $currFunc == "testLarge_"* ]]; then
      [[ ! $VERBOSE ]] && updateDotLine

      output=$(callTest $currFunc 2>&1)
    fi

    if [[ -n $output ]]; then
      (( _PRINTED_LINE_COUNT+=$(echo -e "$output" | wc -l) ))
      echo -e "$output"
    fi
  done

  # since we want to be able to use echo in the tests, but are also in a
  # sub-shell so we can't set variables, we use the exit-code to return the
  # number of failing tests.
  exit $FAILING_TEST_COUNT
}

initDotLine() {
  echo "" # start with a blank line to print the dots on
  _PRINTED_LINE_COUNT=1 # Tracks how many lines have been printed since the dot-line, so we know where it is.
}

updateDotLine() {
  tput cuu $_PRINTED_LINE_COUNT # move the cursor up to the dot-line
  echo -ne "\r" # go to the start of the line
  printf "%0.s." $(seq 0 $finishedTestCount) # print as may dots as tests that have run
  tput cud $_PRINTED_LINE_COUNT # move the cursor back down.
  echo -ne "\r" # go to the start of the line again
}

# Helper functions

callTest() {
  testFunc=$1
  if [[ -z "$MATCH" || $testFunc == $MATCH ]]; then
    logv "  $testFunc"

    callIfExists setup

    if [[ "$TIMED" == "true" ]]; then
      [[ "$VERBOSE" != "true" ]] && echo "$testFunc"
      eval "time -p $testFunc"
      echo
    else
      eval $testFunc
    fi

    callIfExists teardown
  fi
}

callIfExists() {
  declare -F -f $1 > /dev/null
  if [ $? == 0 ]; then
    $1
  fi
}

failUnexpected() {
    maxSizeForMultiline=30
    if [[ "${#1}" -gt $maxSizeForMultiline || ${#2} -gt $maxSizeForMultiline ]]; then
      failFromStackDepth 3 "expected: '$1'\n    got:      '$2'"
    else
      failFromStackDepth 3 "expected: '$1', got: '$2'"
    fi
}

# allows specifyng the call-stack depth at which the error was thrown
failFromStackDepth() {
  printf "FAIL: $test_file(${BASH_LINENO[$1-1]}) > ${FUNCNAME[$1]}\n"
  printf "    $2\n"
  callIfExists teardown

  ((FAILING_TEST_COUNT++))
}

logv() {
  if [ $VERBOSE ]; then
    echo "$1"
  fi
}

runSelfUpdate() {
  # Tnx: https://stackoverflow.com/q/8595751/3968618
  echo "Performing self-update..."

  echo "Downloading latest version..."
  curl $SELF_UPDATE_URL -o $0.tmp
  if [[ $? != 0 ]]; then
    >&2 echo "Update failed: Error downloading."
    exit 1
  fi

  # Copy over modes from old version
  filePermissions=$(stat -c '%a' $0 2> /dev/null)
  if [[ $? != 0 ]]; then
    filePermissions=$(stat -f '%A' $0)
  fi
  if ! chmod $filePermissions "$0.tmp" ; then
    >&2 echo "Update failed: Error setting access-rights on $0.tmp"
    exit 1
  fi

  cat > selfUpdateScript.sh << EOF
#!/usr/bin/env bash
# Overwrite script with updated version
if mv "$0.tmp" "$0"; then
  echo "Done."
  rm \$0
  echo "Update complete."
else
  echo "Failed to overwrite script with updated version!"
fi
EOF

  echo -n "Overwriting old version..."
  exec /bin/bash selfUpdateScript.sh
}

# Asserts:
#--------------------------------------------------------------------------------

assertEquals() {
  if [[ "$2" != "$1" ]]; then
    failUnexpected "$1" "$2"
  fi
}

assertMatches() {
  if [[ "$2" != $1 ]]; then
    failUnexpected "$1" "$2"
  fi
}

fail() {
  failFromStackDepth 2 "$1"
}


# Main entry point (excluded from tests)
if [[ "$0" == "$BASH_SOURCE" ]]; then
  main $@
fi
