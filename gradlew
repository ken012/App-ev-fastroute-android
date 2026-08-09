#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(command -v java 2>/dev/null || true)
fi

if [ -z "$JAVACMD" ] || [ ! -x "$JAVACMD" ]; then
    echo "ERROR: No working Java installation was found. Set JAVA_HOME to JDK 17 or newer." >&2
    exit 1
fi

exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
    -Dorg.gradle.appname=gradlew \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
