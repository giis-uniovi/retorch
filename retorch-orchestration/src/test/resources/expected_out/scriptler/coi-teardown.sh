#!/bin/bash
# The coi-teardown.sh script provides all the necessary commands to tear-down the infrastructure after executing
# the TJobs. It performs cleaning actions, like remove all the containers and docker volumes, but also ends, collects
# and creates file with the different TJob and COI lifecycles using the savetjoblifecycledata.sh script.

COITEARDOWNSTART="$(date +%s%3N)"
# Log the start of the container teardown process
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-tear-down" "Switching off all containers that start with *tjob*..."
# Get the list of container IDs to stop and remove
container_ids=$(docker ps -a -q --filter name=tjob)
if [ -n "$container_ids" ]; then
    # Stop the containers
    if docker stop $container_ids; then
        "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-tear-down" "Successfully stopped containers: $container_ids"
    else
        "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-tear-down" "Failed to stop containers: $container_ids"
        exit 1
    fi
    # Collect volumes used by the containers
    volumes_to_prune=$(docker ps -a --filter id="$container_ids" --format '{{.Mounts}}' | \
                        grep -oP '(?<=type=volume,destination=\/)[^,]+')
    # Remove the containers
    if docker rm $container_ids; then
        "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-tear-down" "Successfully removed containers: $container_ids"
    else
        "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "COI-tear-down" "Failed to remove containers: $container_ids"
        exit 1
    fi
    # Prune the collected volumes
       if [ -n "$volumes_to_prune" ]; then
           for volume in $volumes_to_prune; do
               if docker volume rm "$volume"; then
                   log "DEBUG" "COI-tear-down" "Successfully removed volume: $volume"
               else
                   log "ERROR" "COI-tear-down" "Failed to remove volume: $volume"
               fi
           done
    else
        "$SCRIPTS_FOLDER/printLog.sh" "INFO" "COI-tear-down" "No volumes associated with containers to prune."
    fi
else
    "$SCRIPTS_FOLDER/printLog.sh" "INFO" "COI-tear-down" "No containers with names starting with 'tjob' found to stop and remove."
fi
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-tear-down" "Teardown process completed successfully."

# Run saveTJobLifecycledata.sh script
echo "Running saveTJobLifecycledata.sh script..."
sh "$SCRIPTS_FOLDER/savetjoblifecycledata.sh"
COITEARDOWNEND="$(date +%s%3N)"
OUTPUTDIRCOI="$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER/COI.data"
echo -n ";$COITEARDOWNSTART;$COITEARDOWNEND" >> "$OUTPUTDIRCOI"
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "COI-tear-down" "Script completed successfully."
