#!/usr/bin/env bash
# Run Netnotifyagent from project root (Git Bash / WSL / bash.exe on Windows)
# Make sure you built the project: mvn clean package -DskipTests

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA=java

# prefer dependencies copied to target/dependency (created by mvn dependency:copy-dependencies)
DEPS_DIR="$SCRIPT_DIR/target/dependency"
JAR_DIR="$SCRIPT_DIR/target/classes"

# If dependencies are missing, try to copy them via Maven
if [ ! -d "$DEPS_DIR" ] || [ -z "$(ls -A "$DEPS_DIR" 2>/dev/null)" ]; then
	echo "Dependencies not found in $DEPS_DIR. Running 'mvn dependency:copy-dependencies package -DskipTests' to populate jars..."
	(cd "$SCRIPT_DIR" && mvn dependency:copy-dependencies package -DskipTests) || {
		echo "Failed to copy dependencies via Maven. Please run: mvn dependency:copy-dependencies package -DskipTests" >&2
		exit 1
	}
fi

MODULE_PATH="$DEPS_DIR"
CP="$JAR_DIR:$DEPS_DIR/*"

exec "$JAVA" --module-path "$MODULE_PATH" --add-modules javafx.controls,javafx.web -cp "$CP" br.gov.pb.der.netnotifyagent.Netnotifyagent
