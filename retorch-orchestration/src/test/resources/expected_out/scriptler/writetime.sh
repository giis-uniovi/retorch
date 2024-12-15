#!/bin/bash
# The writetime.sh script appends different timestamps for each TJob to a separate file. These files are then
# processed and combined into a single file containing all COI and TJob lifecycle durations.

# Check if the correct number of parameters is provided
if [ "$#" -ne 2 ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "writeTime-$1" "Usage: $0 <STAGE> <TJOBNAME>"
    exit 1
fi
STAGE=$2
# Constants
WORKSPACE_DIR="$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER"
OUTPUT_FILE="$WORKSPACE_DIR/$STAGE.data"
OUTPUT_DIR_COI="$WORKSPACE_DIR/COI.data"

# Function to append timestamp
append_timestamp() {
  if [ -f "$1" ]; then
    echo -n ";$(date +%s%3N)" >>"$1"
  else
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "writeTime-$1" "Error: File $1 does not exist."
    exit 1
  fi
}

# Check if the output file exists
if [ -f "$OUTPUT_FILE" ]; then
  append_timestamp "$OUTPUT_FILE"
else
  echo "tjobname;stage;COI-setup-start;COI-setup-end;setup-start;setup-end;testexec-start;testexec-end;teardown-start;teardown-end" >"$OUTPUT_FILE"
  {
    echo -n "$STAGE;$BUILD_NUMBER;"
    tail -n +2 "$OUTPUT_DIR_COI"
    echo -n ";$(date +%s%3N)"
  } >>"$OUTPUT_FILE"
fi
