#!/bin/bash
# Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
# All intellectual property rights on this file belong to GIIS.
# The waitforSUT.sh script waits until the SUT is up: the page at the given URL must contain the text configured in
# sut-wait-html. It polls every ${SUT-WAIT-INTERVAL} seconds, at most ${SUT-WAIT-LIMIT} times (sut-wait-interval and
# sut-wait-limit in retorchCI.properties). If the SUT is not up after that, it stores the container logs, tears the
# TJob containers down and exits with 1.

if [ "$#" -ne 2 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "TJob-$1-set-up"  "Usage: $0 <URL> <TJOB_NAME>"
  exit 1
fi
COUNTER=0
WAIT_LIMIT=${SUT-WAIT-LIMIT}
WAIT_INTERVAL=${SUT-WAIT-INTERVAL}

while ! curl --insecure -s "$1" | grep -q "${SUT-WAIT-HTML}"; do
  "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$2-set-up" "Waiting for $1 (attempt $COUNTER of $WAIT_LIMIT)"
  sleep "$WAIT_INTERVAL"
  ((COUNTER++))

  if ((COUNTER >= WAIT_LIMIT)); then
    "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$2 set-up" "SUT is down, making a preventive tear-down and storing the logs"
    "$WORKSPACE/.retorch/scripts/storeContainerLogs.sh" "$2"
    # Tearing down the system.
    docker compose -f docker-compose.yml --env-file "$WORKSPACE/.retorch/envfiles/$2.env" --ansi never -p "$2" down --volumes
    exit 1
  fi
done