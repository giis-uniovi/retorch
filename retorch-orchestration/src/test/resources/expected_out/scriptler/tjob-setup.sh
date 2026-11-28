#!/bin/bash
# The tjob-setup.sh script provides all the necessary commands to set up each TJob's resources before executing
# the TJobs. It includes a placeholder {CUSTOM_SETUP_COMMANDS} where the commands from the custom-tjob-setup file
# are inserted. The script deploys the required test Resources using Docker Compose and waits for the SUT to be ready
# by invoking the waitforSUT.sh script.

if [ "$#" -ne 3 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "TJob-set-up" "Usage: $0 <TJobName> <Stage> <SUTUrl>"
    exit 1
fi

# The lifecycle row of a TJob has six timestamps: set-up start/end, test execution start/end and tear-down start/end.
# When the set-up fails the test execution never runs, so this writes the three that would be missing (set-up end and
# the two of the test execution); the tear-down script writes the last two and every column stays in its place.
write_missing_times() {
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1" # set-up end
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1" # test execution start
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1" # test execution end
}

# Execute the script to write timestamp
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"

# START Custom Set-up commands
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Start executing custom commands"
# The custom-tjob-setup file specifies the custom commands-scripting code that need to be executed into the TJob setup, personalize
# it in the /.retorch/customsscriptscode/custom-tjob-setup.
echo "This TJOB dont have any kind of specific commands"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "End executing custom commands"
# END Custom Set-up commands

# Deploy containers
cd "$SUT_LOCATION"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Deploying containers for TJOB $1"
if ! docker compose -f docker-compose.yml --env-file "$WORKSPACE/.retorch/envfiles/$1.env" --ansi never -p "$1" up -d; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Docker compose failed, the test execution will not run"
    write_missing_times "$1" "$2"
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Exiting"
    exit 1
fi
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Docker compose successful!"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Waiting for the system to be up..."
if ! "$SCRIPTS_FOLDER/waitforSUT.sh" "$3" "$1"; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "The system did not come up, the test execution will not run"
    write_missing_times "$1" "$2"
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Exiting"
    exit 1
fi
cd "$WORKSPACE"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "System READY!! Test execution can start!"
# Execute the script to write timestamp again
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
exit 0
