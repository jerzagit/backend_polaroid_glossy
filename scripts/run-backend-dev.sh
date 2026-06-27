#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5433/polaroid}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-password}"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
else
  echo "Java 17 is required. Set JAVA_HOME to a Java 17 JDK before running this script." >&2
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

if command -v docker >/dev/null 2>&1; then
  if ! docker ps --format '{{.Names}}' | grep -qx 'polaroid-postgres-dev'; then
    if docker ps -a --format '{{.Names}}' | grep -qx 'polaroid-postgres-dev'; then
      docker start polaroid-postgres-dev >/dev/null
    else
      docker run -d \
        --name polaroid-postgres-dev \
        -p 5433:5432 \
        -e POSTGRES_PASSWORD="$DB_PASSWORD" \
        -e POSTGRES_DB=polaroid \
        postgres:15 >/dev/null
    fi
  fi
fi

cd "$ROOT_DIR"
exec mvn spring-boot:run -Dspring-boot.run.profiles=dev
