#!/usr/bin/env bash
# Throwaway local Postgres for TodoList API development and smoke testing.
# POSIX/bash 3.2 compatible counterpart to scripts/dev-db.ps1.
#
# Why this exists: `mvn -pl api exec:java` does NOT start an embedded Postgres.
# The embedded Postgres in this repo is a TEST-scope dependency used only by the
# api module's integration tests. With no DATABASE_URL the API starts but every
# data route answers 503, so any real local run needs a real database. Pointing
# a local API at production Neon is not an option either: startup runs Flyway,
# which would apply unmerged migrations to production.
#
# Usage:
#   bash scripts/dev-db.sh              start (or reuse), print DATABASE_URL
#   bash scripts/dev-db.sh --fixture    also load scripts/dev-db-fixture.sql
#   bash scripts/dev-db.sh --reset      delete the volume and start clean
#   bash scripts/dev-db.sh --stop       stop and remove the container
#   bash scripts/dev-db.sh --port 5434  use another host port
#
# --fixture requires the API to have started once already, because Flyway
# (inside the API) creates the schema.

set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

CONTAINER=todolist-dev-db
VOLUME=todolist-dev-db-data
IMAGE=postgres:16
DB_USER=postgres
DB_PASS=todo
DB_NAME=todo
PORT=5433
DO_STOP=0
DO_RESET=0
DO_FIXTURE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --stop) DO_STOP=1 ;;
    --reset) DO_RESET=1 ;;
    --fixture) DO_FIXTURE=1 ;;
    --port) shift; PORT="$1" ;;
    *) echo "Unknown argument: $1"; exit 2 ;;
  esac
  shift
done

DB_URL="postgres://${DB_USER}:${DB_PASS}@localhost:${PORT}/${DB_NAME}"

if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  echo ""
  echo "Docker is not running."
  echo "Start Docker Desktop, wait until the engine is running, then run this script again."
  exit 1
fi

if [ "$DO_STOP" -eq 1 ]; then
  echo "Stopping and removing container $CONTAINER ..."
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  echo "Done. The data volume is kept; pass --reset next start to wipe it."
  echo "Remember to unset DATABASE_URL in any shell that set it."
  exit 0
fi

if [ "$DO_RESET" -eq 1 ]; then
  echo "Reset requested: removing container and data volume ..."
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
fi

if [ "$(docker ps --filter "name=^/${CONTAINER}$" --format '{{.Names}}')" = "$CONTAINER" ]; then
  echo "Container $CONTAINER is already running."
elif [ "$(docker ps -a --filter "name=^/${CONTAINER}$" --format '{{.Names}}')" = "$CONTAINER" ]; then
  echo "Starting existing container $CONTAINER ..."
  docker start "$CONTAINER" >/dev/null
else
  echo "Creating container $CONTAINER on port $PORT ..."
  docker run -d --name "$CONTAINER" \
    -e "POSTGRES_PASSWORD=${DB_PASS}" \
    -e "POSTGRES_DB=${DB_NAME}" \
    -v "${VOLUME}:/var/lib/postgresql/data" \
    -p "${PORT}:5432" \
    "$IMAGE" >/dev/null
fi

echo "Waiting for Postgres to accept connections ..."
READY=0
i=1
while [ "$i" -le 30 ]; do
  if docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
  i=$((i + 1))
done
if [ "$READY" -ne 1 ]; then
  echo "Postgres did not become ready. Inspect it with: docker logs $CONTAINER"
  exit 1
fi
echo "Postgres is ready."

if [ "$DO_FIXTURE" -eq 1 ]; then
  if docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1 FROM users LIMIT 1" >/dev/null 2>&1; then
    echo "Loading scripts/dev-db-fixture.sql ..."
    docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < scripts/dev-db-fixture.sql
    echo "Fixture loaded."
  else
    echo ""
    echo "The schema does not exist yet, so the fixture cannot load."
    echo "Flyway runs inside the API, so start the API once first:"
    echo ""
    echo "  DATABASE_URL='$DB_URL' TODO_SESSION_SECRET='dev-secret' mvn -pl api exec:java"
    echo ""
    echo "Stop it once it logs that it is listening, then re-run this script with --fixture."
  fi
fi

echo ""
echo "Local database ready. Run the API with:"
echo ""
echo "  DATABASE_URL='$DB_URL' TODO_SESSION_SECRET='dev-secret' mvn -pl api exec:java"
echo ""
echo "Then the desktop client, in another terminal:"
echo ""
echo "  mvn -pl client javafx:run"
echo ""
echo "Create a login account against this database with: bash scripts/seed-user.sh"
echo "(enter $DB_URL at its prompt)"
echo ""
echo "Tear down when finished: bash scripts/dev-db.sh --stop"
