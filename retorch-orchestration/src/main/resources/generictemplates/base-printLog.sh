#!/bin/bash
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <level> <component> <message>"
  exit 1
fi

echo "[$(date +"%Y-%m-%d %T")] [$1] [$2] - $3"
