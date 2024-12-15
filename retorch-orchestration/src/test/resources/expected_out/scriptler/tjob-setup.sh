#!/bin/bash
# The tjob-setup.sh script provides all the necessary commands to set up each TJob's resources before executing
# the TJobs. It includes a placeholder {CUSTOM_SETUP_COMMANDS} where the commands from the custom-tjob-setup file
# are inserted. The script deploys the required test Resources using Docker Compose and waits for the SUT to be ready
# by invoking the waitforSUT.sh script.

# Execute the script to write timestamp
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
# Export Docker Host IP
DOCKER_HOST_IP=$(/sbin/ip route | awk '/default/ { print $3 }')
export DOCKER_HOST_IP
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Exporting the HOST_IP: $DOCKER_HOST_IP"

# START Custom Set-up commands
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Start executing custom commands"
# The custom-tjob-setup file specifies the custom commands-scripting code that need to be executed into the TJob setup, personalize
# it in the /retorchfiles/customsscriptscode/custom-tjob-setup.
copy_and_replace_envoy_configs() {
    local tjobname="$1"
    cp -p "$SUT_LOCATION/src/ApiGateways/Envoy/config/mobileshopping/envoy.yaml" "$SUT_LOCATION/src/tmp/$tjobname/mobileshopping/"
    cp -p "$SUT_LOCATION/src/ApiGateways/Envoy/config/webshopping/envoy.yaml" "$SUT_LOCATION/src/tmp/$tjobname/webshopping/"

    sed -i "s/\${tjobname}/$tjobname/g" "$SUT_LOCATION/src/tmp/$tjobname/mobileshopping/envoy.yaml"
    sed -i "s/\${tjobname}/$tjobname/g" "$SUT_LOCATION/src/tmp/$tjobname/webshopping/envoy.yaml"
}
# COI setup
mkdir -p "$SUT_LOCATION/src/tmp/$1/mobileshopping"
mkdir -p "$SUT_LOCATION/src/tmp/$1/webshopping"

copy_and_replace_envoy_configs "$1"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "End executing custom commands"
# END Custom Set-up commands

# Deploy containers
cd "$SUT_LOCATION"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Deploying containers for TJOB $1"
docker compose -f docker-compose.yml --env-file "$WORKSPACE/retorchfiles/envfiles/$1.env" --ansi never -p "$1" up -d

if [ $? -ne 0 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Docker compose failed,writing end time of the set-up"
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Docker compose failed,writing end time of the test execution"
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
    "$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "$1-set-up" "Exiting"
    exit 1
else
   "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Docker compose successful!"
fi
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "Waiting for the system to be up..."
"$WORKSPACE/retorchfiles/scripts/waitforSUT.sh" "$1"
cd "$WORKSPACE"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$1-set-up" "System READY!! Test execution can start!"
# Execute the script to write timestamp again
"$SCRIPTS_FOLDER/writetime.sh" "$2" "$1"
exit 0
