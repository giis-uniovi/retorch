#!/bin/bash
# The tjob-teardown.sh script provides all the necessary commands to tear-down each TJob's Resources after the
# test execution has ended. It stores the container logs of the current TJob, tear down the containers
# and execute the custom commands provided in the custom-tjob-teardown file.

set -e

if [ "$#" -ne 2 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "TJob-tear-down" "Usage: $0 <TJobName> <Stage>"
    exit 1
fi

# Execute the script to write timestamp
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Starting the TJob tear-down"
# Store docker logs
"$SCRIPTS_FOLDER/storeContainerLogs.sh" "$1"

# Change to SUT location
cd "$SUT_LOCATION"

# Tear down Docker containers and volumes
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Tearing down Docker containers and volumes for TJOB $1"
docker compose -f docker-compose.yml --env-file "$WORKSPACE/.retorch/envfiles/$1.env" --ansi never -p "$1" down --volumes

# Return to the original working directory
cd "$WORKSPACE"

# START Custom Set-up commands
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Start executing custom commands"
# The custom-tjob-teardown file specifies the custom commands-scripting code that need to be executed into the TJob teardown, personalize
# it in the /.retorch/customsscriptscode/custom-tjob-teardown.
echo "This TJOB dont have any kind of specific commands"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "End executing custom commands"
# END Custom Set-up commands

"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Tear-down ended"
# Execute the script to write timestamp again
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
