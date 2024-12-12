#!/bin/bash
echo "Starting to store container logs!"
 # Store docker logs
    DIRECTORY_PATH="$WORKSPACE/target/containerlogs/$1"

    if [ ! -d "$DIRECTORY_PATH" ]; then
      echo "Directory for storing logs doesnt exist creating..."
      mkdir -p $DIRECTORY_PATH
    fi

    for CONTAINER_NAME in $(docker ps -a --format "{{.Names}}" --filter Name=$1); do
      echo "Storing log container $CONTAINER_NAME in TJob $1"
      docker logs $CONTAINER_NAME &>"$DIRECTORY_PATH/$CONTAINER_NAME.log"
    done
echo "Storing of logs finished"
