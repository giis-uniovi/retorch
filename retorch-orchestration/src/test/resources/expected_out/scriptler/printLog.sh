#!/bin/bash
# The printlog.sh script provides a template for outputting formatted messages during the setup, execution
# and teardown of TJobs and COI.
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <level> <component> <message>"
  exit 1
fi
echo "[$(date +"%Y-%m-%d %T")] [$1] [$2] - $3"
