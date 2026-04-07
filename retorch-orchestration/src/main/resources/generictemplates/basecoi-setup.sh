#!/bin/bash
# The coi-setup.sh script provides all the necessary commands to set up the infrastructure before executing
# the TJobs. It performs debugging actions, such as outputting the kernel, library, and Docker versions.
# Additionally, it prunes old videos and execution data, and verifies that the required Selenium containers are running.

if [ "$#" -ne 0 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-set-up" "Usage: $0 - This script does not take any parameters"
    exit 1
fi

SEL_VIDEO_DIR="/opt/selenoid/video/"
SEL_LOG_DIR="/opt/selenoid/logs/"

# Function to remove older files
remove_old_files() {
    dir=$1
    days=$2
    "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-set-up" "Removing older files ($days days) in $dir! The number of files prior to remove:"
    find "$dir" | wc -l
    find "$dir" -mindepth 1 -maxdepth 1 -mtime +$days -exec rm -rf {} \;
    "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-set-up" "Removing older files ($days days) in $dir! The number of files after remove:"
    find "$dir" | wc -l
}

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
# Remove older videos (older than 15 days)
remove_old_files "$SEL_VIDEO_DIR" 15

# Remove older logs (older than 7 days)
remove_old_files "$SEL_LOG_DIR" 7

# Create necessary directories
mkdir -p "$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER"
mkdir -p "$WORKSPACE/artifacts"
mkdir -p "$SUT_LOCATION/tmp"

# START Custom COI commands
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Start executing custom commands"
${CUSTOM_SETUP_COI_COMMANDS}
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "End executing custom commands"
# END Custom COI commands


# Check if the provided string appears in any running container's name
check_container_running() {
    local search_term="$1"

    # We capture the ID of any running container that matches the name string
    local container_id=$(docker ps --filter "name=$search_term" --filter "status=running" -q)

    if [ -n "$container_id" ]; then
        "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-set-up" "Found running container containing '$search_term' in the name."
    else
        "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-set-up" "No running container name matches '$search_term'. Aborting."
        exit 1
    fi
}

check_container_running "selenium-hub"
check_container_running "chrome-video"
check_container_running "chrome-node"

echo "Building images of SUT"

cd "$SUT_LOCATION"
# Use double quotes for Docker Compose files TEMPORAL FIX
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