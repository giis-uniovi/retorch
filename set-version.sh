# Set a new version for all project components

# Ensures this script is running in his folder
SCRIPT_DIR=$(readlink -f $0 | xargs dirname)
echo "Run command at directory: $SCRIPT_DIR"
cd $SCRIPT_DIR
pwd

read -p "Enter new version: " NEW_VERSION

# Java multimodule project
mvn versions:set -DnewVersion="$NEW_VERSION" --no-transfer-progress
