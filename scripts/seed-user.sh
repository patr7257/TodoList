#!/usr/bin/env bash
# Creates (or renames) a TodoList account in the shared Neon Postgres.
# This is the ONLY way an account comes into existence: nothing in the product
# self-signs-up, so a new person needs a row here before they can sign in.
# The account is PASSWORDLESS (pw_hash NULL). Password login was retired in
# issue #61; the new account signs in with a magic link at its email address
# and can enrol a passkey once that first session exists.
# Run: cd /path/to/TodoList && bash scripts/seed-user.sh
# Prompts for the Neon DATABASE_URL (unpooled) if not already in the env, then
# for email / name. Re-running with the same email only renames it.
# Note: the email must also be on the website's TODO_AUTH_ALLOWED_EMAILS list
# (or its built-in default pair) or the magic link is never mailed.
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
