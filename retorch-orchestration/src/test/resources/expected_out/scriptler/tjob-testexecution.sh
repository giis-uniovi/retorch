#!/bin/bash
# The testexecution.sh script executes the test cases, providing them with the URL and port where the SUT is available.
# It also measures the execution time and marks the pipeline as failed if any test case fails.

set -e
# Function to execute timestamp script
EXECUTE_TIMESTAMP_SCRIPT() {
  local STAGE="$1"
  local TJOB_NAME="$2"
  $SCRIPTS_FOLDER/writetime.sh "$STAGE" "$TJOB_NAME"
}

# Check if the required parameters are provided
if [ "$#" -lt 5 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-test-execution" "Usage: $0 <TJOB_NAME> <STAGE> <SUT_URL> <PORT> <TESTS_TO_EXECUTE>"
  exit 1
fi
# Constants
DOCKER_HOST_IP=$(/sbin/ip route | awk '/default/ { print $3 }')
# Define variables
TJOB_NAME="$1"
STAGE="$2"
PORT="$4"
TEST_NAME="$5"
if [[ $3 == "https://" || $3 == "http://" ]]; then
  "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "Only http:// || https:// protocol provided, using HOST DOCKER INTERNAL IP "
  SUT_URL="$3$DOCKER_HOST_IP"
else
  "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "Using the provided URL"
  SUT_URL="$3"
fi
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "The URL IS $SUT_URL"

# Execute the script to write timestamp
EXECUTE_TIMESTAMP_SCRIPT "$STAGE" "$TJOB_NAME"

# Display HOST_IP and PORT information
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "The HOST_IP is: $DOCKER_HOST_IP and PORT $PORT for the TJOB $TJOB_NAME"

LOCALHOST="$DOCKER_HOST_IP:$PORT"
# Run Maven test
mvn test -Dtest="$TEST_NAME" -Dtjob_name="$TJOB_NAME" -DSUT_URL="$SUT_URL" -DSUT_PORT="$PORT"

# Store Test result
MVN_EXIT_CODE=$?

# Execute the script to write timestamp again
EXECUTE_TIMESTAMP_SCRIPT "$STAGE" "$TJOB_NAME"

# Check if Maven test failed
if [ $MVN_EXIT_CODE -ne 0 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-test-execution" "Maven test FAILED with exit code $MVN_EXIT_CODE."
  exit 1 # Return 1 if the Maven test fails
fi

# If the script reaches here, the Maven test succeeded
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-test-execution" "Maven test succeeded."

exit 0
