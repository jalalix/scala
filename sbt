#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/sbt-launch.jar"
PROPS="$DIR/project/build.properties"

if [[ ! -f "$JAR" ]]; then
  if [[ ! -f "$PROPS" ]]; then
    echo "missing $PROPS" >&2
    exit 1
  fi
  VERSION="$(sed -n 's/^sbt.version=//p' "$PROPS" | tr -d '[:space:]')"
  if [[ -z "$VERSION" ]]; then
    echo "could not read sbt.version from $PROPS" >&2
    exit 1
  fi
  URL="https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/${VERSION}/sbt-launch-${VERSION}.jar"
  echo "Downloading sbt-launch ${VERSION}..."
  TMP="${JAR}.tmp"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$TMP"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$TMP" "$URL"
  else
    echo "need curl or wget to download sbt-launch.jar" >&2
    exit 1
  fi
  mv "$TMP" "$JAR"
fi

exec java -jar "$JAR" "$@"
