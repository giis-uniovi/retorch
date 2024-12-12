#!/bin/bash
set -e

# Execute the script to write timestamp
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Starting the TJob tear-down"
# Store docker logs
"$WORKSPACE/retorchfiles/scripts/storeContainerLogs.sh" "$1"

# Change to SUT location
cd "$SUT_LOCATION"

# Tear down Docker containers and volumes
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Tearing down Docker containers and volumes for TJOB $1"
docker compose -f docker-compose.yml --env-file "$WORKSPACE/retorchfiles/envfiles/$1.env" --ansi never -p "$1" down --volumes

# Return to the original working directory
cd "$WORKSPACE"

"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-tear-down" "Tear-down ended"
# Execute the script to write timestamp again
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
