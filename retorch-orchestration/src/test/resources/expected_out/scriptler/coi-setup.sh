#!/bin/bash
# The coi-setup.sh script provides all the necessary commands to set up the infrastructure before executing
# the TJobs. It performs debugging actions, such as outputting the kernel, library, and Docker versions.
# Anything specific to the SUT or to its test infrastructure (for example checking that the Selenium containers are
# running, or removing old videos) belongs to the optional custom-coi-setup hook. Finally it builds the images of the SUT.

if [ "$#" -ne 0 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-set-up" "Usage: $0 - This script does not take any parameters"
    exit 1
fi

# Set output directory for COI
OUTPUTDIRCOI="$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER/COI.data"

# Record start time for COI setup
COISETUPSTART=$(date +%s%3N)

# Prompt some system information
docker_version=$(docker version)
docker_compose_version=$(docker compose version | awk '{print $4}')
curl_version=$(curl --version | head -n 1 | awk '{print $2}')
kernel_version=$(uname -r)
os_version=$(cat /etc/os-release | grep PRETTY_NAME | cut -d '"' -f 2)

"$SCRIPTS_FOLDER/printLog.sh" "INFO" "COI-set-up" "Os version : $os_version, Kernel version: $kernel_version,  Docker-compose version: $docker_compose_version, curl-version: $curl_version"
"$SCRIPTS_FOLDER/printLog.sh" "INFO" "COI-set-up" "Full Docker versions:\n $docker_version"

# Create necessary directories
mkdir -p "$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER"
mkdir -p "$WORKSPACE/artifacts"
mkdir -p "$SUT_LOCATION/tmp"

# START Custom COI commands
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-set-up" "Start executing custom commands"
# The custom-coi-setup file specifies the custom commands-scripting code that need to be executed into the COI setup, personalize
# it in the /.retorch/customsscriptscode/custom-coi-setup.
echo "This COI setup dont have any kind of specific commands"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-set-up" "End executing custom commands"
# END Custom COI commands


echo "Building images of SUT"

cd "$SUT_LOCATION"
# The images are the same for every TJob: build them with the environment file of the first TJob of the plan
if docker compose -f "docker-compose.yml" --env-file "$WORKSPACE/.retorch/envfiles/tjoba.env" --ansi never build; then
    "$SCRIPTS_FOLDER/printLog.sh" "INFO" "COI-set-up" "Images for the SUT created successfully"
else
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-set-up" "Failed to build images of SUT"
    exit 1
fi

# Record end time for COI setup
COISETUPEND=$(date +%s%3N)

# Write setup timestamps to output file
echo "COI-SETUP-START;COI-SETUP-END;COI-TEARDOWN-START;COI-TEARDOWN-END" > "$OUTPUTDIRCOI"
echo -n "$COISETUPSTART;$COISETUPEND" >> "$OUTPUTDIRCOI"
