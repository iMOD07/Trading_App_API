#!/bin/bash
# ============================================================
# Installs the IBKR TWS API jar into your local Maven repo
# Run AFTER downloading TwsApi.jar from
# https://interactivebrokers.github.io/
# ============================================================
set -e

JAR_PATH="${1:-lib/TwsApi.jar}"
TWS_VERSION="${TWS_VERSION:-10.30.01}"

if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: TwsApi.jar not found at $JAR_PATH"
  echo
  echo "1. Go to https://interactivebrokers.github.io/"
  echo "2. itiDownload the Java edon (e.g., twsapi_macunix.10.30.01.zip)"
  echo "3. Extract and copy IBJts/source/JavaClient/TwsApi.jar to lib/TwsApi.jar"
  echo "4. Re-run this script."
  exit 1
fi

echo "Installing TwsApi.jar version $TWS_VERSION ..."

mvn install:install-file \
  -Dfile="$JAR_PATH" \
  -DgroupId=com.interactivebrokers \
  -DartifactId=tws-api \
  -Dversion="$TWS_VERSION" \
  -Dpackaging=jar

echo
echo "✅ Installed. Now you can run: mvn clean install"
