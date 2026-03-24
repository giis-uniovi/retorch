#!/bin/bash
# The testexecution.sh script executes the test cases, providing them with the URL and port where the SUT is available.
# It also measures the execution time and marks the pipeline as failed if any test case fails.

set -e

# Check if the required parameters are provided
if [ "$#" -lt 4 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-test-execution" "Usage: $0 <TJOB_NAME> <STAGE> <SUT_URL> <TESTS_TO_EXECUTE>"
  exit 1
fi

TJOB_NAME="$1"
STAGE="$2"
SUT_URL="$3"
TEST_NAME="$4"

"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "The URL IS $SUT_URL"

# Execute the script to write timestamp
"$SCRIPTS_FOLDER/writetime.sh" "$STAGE" "$TJOB_NAME"

"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "Staring execution of TJOB $TJOB_NAME"

# Run Maven test
mvn test -Dtest="$TEST_NAME" -DTJOB_NAME="$TJOB_NAME" -DSUT_URL="$SUT_URL"
MVN_EXIT_CODE=$?

# Execute the script to write timestamp again
"$SCRIPTS_FOLDER/writetime.sh" "$STAGE" "$TJOB_NAME"

if [ $MVN_EXIT_CODE -ne 0 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-test-execution" "Maven test FAILED with exit code $MVN_EXIT_CODE."
  exit 1
fi

"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "Maven test succeeded."
exit 0
