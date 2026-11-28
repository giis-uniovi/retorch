#!/bin/bash
# Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
# All intellectual property rights on this file belong to GIIS.
# The generateusageprofile.sh script builds the usage profile of the execution (one self-contained HTML file with the
# resources used and what they would cost in different clouds) from the single usage file written by
# resourcemonitor.sh (artifacts/usageprofile-<branch>-<build>.csv).
#
# The profiling tool is the jar $HOME/.retorch/retorch-profiling-cli.jar. If it is not there it is downloaded from the
# URL configured in retorchCI.properties (profiling-jar-url). A project can prepare the jar by other means (e.g. build
# it from source while no release publishes it) in the optional custom-usageprofile snippet, inserted just below.
# The prices of the cloud providers are downloaded and cached in $HOME/.retorch/prices.json. The regions (comma
# separated lists) come from retorchCI.properties (azure-region, aws-region) and can be overridden with the
# AZURE_REGIONS / AWS_REGIONS environment variables (e.g. Jenkins job parameters); an empty value disables the provider.

PROFILING_JAR_URL="${PROFILING_JAR_URL_PLACEHOLDER}"
PROFILING_JAR="$HOME/.retorch/retorch-profiling-cli.jar"

fail() {
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "UsageProfile" "$1"
    exit 1
}

mkdir -p "$HOME/.retorch"

# START Custom usage profile commands (they can leave the profiling jar in $PROFILING_JAR)
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "UsageProfile" "Start executing custom commands"
${CUSTOM_USAGEPROFILE_COMMANDS}
"$SCRIPTS_FOLDER/printLog.sh" "DEBUG" "UsageProfile" "End executing custom commands"
# END Custom usage profile commands

if [ ! -f "$PROFILING_JAR" ]; then
    "$SCRIPTS_FOLDER/printLog.sh" "INFO" "UsageProfile" "Downloading the profiling tool from $PROFILING_JAR_URL"
    curl -fsSL "$PROFILING_JAR_URL" -o "$PROFILING_JAR.tmp" || { rm -f "$PROFILING_JAR.tmp"; fail "Could not download $PROFILING_JAR_URL"; }
    mv "$PROFILING_JAR.tmp" "$PROFILING_JAR"
fi

USAGE_FILE="$WORKSPACE/artifacts/usageprofile-${BRANCH_NAME///}-${BUILD_NUMBER}.csv"
OUTPUT_FILE="$WORKSPACE/artifacts/usageprofile/usageprofile.html"
[ -f "$USAGE_FILE" ] || fail "Missing $USAGE_FILE"

# The execution graph (TJobs, stages and tests) written by the orchestrator, if the project has it
PLAN_FILE="$WORKSPACE/.retorch/executionplan.json"
PLAN_ARG=()
[ -f "$PLAN_FILE" ] && PLAN_ARG=("$PLAN_FILE")

${OPTIONAL_PRICE_PROVIDERS_COMMANDS}
java -jar "$PROFILING_JAR" "${JOB_NAME}-${BUILD_NUMBER}" "$USAGE_FILE" "$OUTPUT_FILE" \
    "${AZURE_REGIONS:-${AZURE_REGION_PLACEHOLDER}}" "${AWS_REGIONS:-${AWS_REGION_PLACEHOLDER}}" \
    "$HOME/.retorch/prices.json" "${PLAN_ARG[@]}"
