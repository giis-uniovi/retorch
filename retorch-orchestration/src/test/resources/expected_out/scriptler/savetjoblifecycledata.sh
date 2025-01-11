#!/bin/bash
# The savetjoblifecycledata.sh script generates a CSV file containing the durations of different TJob lifecycles.
# It collects the previously generated files using the base-writetime.sh script and combines them into a single file
# with all the durations, which can later be processed.

set -e
# Check if the correct number of arguments is provided
if [ "$#" -ne 0 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "CreationTimeFile" "This script doesn't take any parameter"
    exit 1
fi

PATH_FILES="$WORKSPACE/retorchcostestimationdata/exec${BUILD_NUMBER}/tjob*"
OUTPUT_FILE="$WORKSPACE/artifacts/lifecycletimingsexec${BRANCH_NAME///}-${BUILD_NUMBER}.csv"
OUTPUT_DIR_COI="$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER/COI.data"

echo "tjobname;stage;COI-setup-start;COI-setup-end;tjob-setup-start;tjob-setup-end;tjob-testexec-start;tjob-testexec-end;tjob-teardown-start;tjob-teardown-end;coi-teardown-start;coi-teardown-end" >"$OUTPUT_FILE"

for csvfile in $PATH_FILES; do
  if [ -e "$csvfile" ] && [ -e "$OUTPUT_DIR_COI" ]; then
    {
      tail -n +2 "$csvfile"
      echo -n ";"
      tail -n +2 "$OUTPUT_DIR_COI" | rev | cut -d ';' -f 2 | rev | tr -d '\n'
      echo -n ";"
      tail -n +2 "$OUTPUT_DIR_COI" | rev | cut -d ';' -f 1 | rev
    } >>"$OUTPUT_FILE"
  else
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "CreationTimeFile" "Error: One or more files do not exist."
    exit 1
  fi
done
