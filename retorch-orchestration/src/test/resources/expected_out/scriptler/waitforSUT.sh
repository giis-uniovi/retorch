#!/bin/bash
# The waitforSUT.sh script waits until a given container reaches the 'healthy' state. It polls every 5 seconds
# for up to 200 seconds. If the container is not healthy after this period, all containers are torn down.

if [ "$#" -ne 2 ]; then
  "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "TJob-$1-set-up"  "Usage: $0 <URL> <TJOB_NAME>"
  exit 1
fi
DOCKER_HOST_IP=$(/sbin/ip route | awk '/default/ { print $3 }')
COUNTER=0
WAIT_LIMIT=40

while ! curl --insecure -s "$1" | grep -q "<div class=\"esh-catalog-item col-md-4\">"; do
  "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$2-set-up" "Waiting $COUNTER seconds for $1"
  sleep 5
  ((COUNTER++))

  if ((COUNTER > WAIT_LIMIT)); then
    "$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "$2 set-up" "SUT is down, making a preventive tear-down and storing the logs"
    "$WORKSPACE/.retorch/scripts/storeContainerLogs.sh" "$2"
    # Tearing down the system.
    docker compose -f docker-compose.yml --env-file "$WORKSPACE/.retorch/envfiles/$2.env" --ansi never -p "$2" down --volumes
    exit 1
  fi
done
