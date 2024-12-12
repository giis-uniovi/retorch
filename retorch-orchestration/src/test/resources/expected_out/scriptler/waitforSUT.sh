#!/bin/bash
if [ "$#" -ne 1 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "TJob-$1-set-up"  "Usage: $0 <TJobName>"
  exit 1
fi
DOCKER_HOST_IP=$(/sbin/ip route | awk '/default/ { print $3 }')
COUNTER=0
WAIT_LIMIT=40

while ! curl --insecure -s "https://full-teaching-$1:5000" | grep -q "<title>FullTeaching</title>"; do
  "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "TJob-$1-set-up" "Waiting $COUNTER seconds for $1 with URL https://full-teaching-$1:5000"
  sleep 5
  ((COUNTER++))

  if ((COUNTER > WAIT_LIMIT)); then
    "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "TJob-$1-set-up" "SUT is down, making a preventive tear-down and storing the logs"
    "$WORKSPACE/retorchfiles/scripts/storeContainerLogs.sh" "$1"
    # Tearing down the system.
    docker compose -f docker-compose.yml --env-file "$WORKSPACE/retorchfiles/envfiles/$1.env" --ansi never -p "$1" down --volumes
    exit 1
  fi
done
