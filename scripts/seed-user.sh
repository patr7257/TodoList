#!/usr/bin/env bash
# Seeds (or updates) a TodoList login account in the shared Neon Postgres, using
# the API's own scrypt hashing so the account can sign in on web and desktop.
# Run: cd /path/to/TodoList && bash scripts/seed-user.sh
# Prompts for the Neon DATABASE_URL (unpooled) if not already in the env, then
# for email / name / password. Re-running with the same email resets it.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"

JAR="api/target/todolist-api.jar"
if [ ! -f "$JAR" ]; then
  echo "Building the api jar (first run only)..."
  mvn -q -pl api -am -DskipTests package
fi

if [ -z "${DATABASE_URL:-}" ]; then
  read -r -p "Neon DATABASE_URL (unpooled): " DATABASE_URL
  export DATABASE_URL
fi

java -cp "$JAR" dk.dtu.api.tools.SeedUser
