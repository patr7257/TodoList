#!/usr/bin/env bash
# Create or delete a disposable Neon branch of the TodoList production database.
# POSIX/bash 3.2 compatible counterpart to scripts/neon-branch.ps1.
#
# Why this exists: some things can only be verified against real data, above all
# the lists.owner free-text to owner_id backfill. A Neon branch is a
# copy-on-write clone: cheap, isolated, deletable, so migrations and backfills
# can be rehearsed on production-shaped data without touching production.
#
# Usage:
#   bash scripts/neon-branch.sh                      create branch (default name)
#   bash scripts/neon-branch.sh --name my-test       create with a chosen name
#   bash scripts/neon-branch.sh --delete             delete the default-named branch
#   bash scripts/neon-branch.sh --list               list existing branches
#
# Prompts once for a Neon API key (create one at
# https://console.neon.tech/app/settings/api-keys). Never written to disk.
# Requires curl and jq.
#
# NEVER point a local API at the production branch: API startup runs Flyway, so
# it would apply unmerged migrations to production.

set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

API=https://console.neon.tech/api/v2
NAME=todolist-smoke
DO_DELETE=0
DO_LIST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --name) shift; NAME="$1" ;;
    --delete) DO_DELETE=1 ;;
    --list) DO_LIST=1 ;;
    *) echo "Unknown argument: $1"; exit 2 ;;
  esac
  shift
done

for tool in curl jq; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required but not installed."
    exit 1
  fi
done

printf 'Neon API key (not stored): '
stty -echo 2>/dev/null || true
read -r KEY
stty echo 2>/dev/null || true
printf '\n'
if [ -z "$KEY" ]; then
  echo "No API key entered. Nothing done."
  exit 1
fi

neon() {
  # $1 method, $2 path, $3 optional json body
  if [ $# -ge 3 ]; then
    curl -fsS -X "$1" "$API$2" -H "Authorization: Bearer $KEY" \
      -H 'Accept: application/json' -H 'Content-Type: application/json' -d "$3"
  else
    curl -fsS -X "$1" "$API$2" -H "Authorization: Bearer $KEY" -H 'Accept: application/json'
  fi
}

PROJECTS_JSON=$(neon GET /projects) || { echo "Could not list projects. Is the key valid?"; exit 1; }
COUNT=$(echo "$PROJECTS_JSON" | jq '.projects | length')
if [ "$COUNT" -eq 0 ]; then
  echo "No Neon projects on this account."
  exit 1
fi

if [ "$COUNT" -eq 1 ]; then
  PROJECT_ID=$(echo "$PROJECTS_JSON" | jq -r '.projects[0].id')
  PROJECT_NAME=$(echo "$PROJECTS_JSON" | jq -r '.projects[0].name')
else
  echo ""
  echo "Neon projects:"
  echo "$PROJECTS_JSON" | jq -r '.projects | to_entries[] | "  [\(.key)] \(.value.name)  (\(.value.id))"'
  printf 'Which project number holds the TodoList database? '
  read -r PICK
  PROJECT_ID=$(echo "$PROJECTS_JSON" | jq -r --argjson i "$PICK" '.projects[$i].id')
  PROJECT_NAME=$(echo "$PROJECTS_JSON" | jq -r --argjson i "$PICK" '.projects[$i].name')
  if [ "$PROJECT_ID" = "null" ] || [ -z "$PROJECT_ID" ]; then
    echo "Invalid selection. Nothing done."
    exit 1
  fi
fi
echo "Project: $PROJECT_NAME ($PROJECT_ID)"

BRANCHES_JSON=$(neon GET "/projects/$PROJECT_ID/branches")

if [ "$DO_LIST" -eq 1 ]; then
  echo ""
  echo "Branches:"
  echo "$BRANCHES_JSON" | jq -r '.branches[] | "  \(.name)\(if .default then "  (default / production)" else "" end)   id=\(.id)  created=\(.created_at)"'
  exit 0
fi

BRANCH_ID=$(echo "$BRANCHES_JSON" | jq -r --arg n "$NAME" '.branches[] | select(.name == $n) | .id')
IS_DEFAULT=$(echo "$BRANCHES_JSON" | jq -r --arg n "$NAME" '.branches[] | select(.name == $n) | .default')

if [ "$DO_DELETE" -eq 1 ]; then
  if [ -z "$BRANCH_ID" ]; then
    echo "No branch named '$NAME'. Nothing to delete."
    exit 0
  fi
  if [ "$IS_DEFAULT" = "true" ]; then
    echo "Refusing to delete '$NAME': it is the DEFAULT (production) branch."
    exit 1
  fi
  echo ""
  echo "About to DELETE Neon branch '$NAME' (id $BRANCH_ID) in project $PROJECT_NAME."
  printf 'Type the branch name to confirm: '
  read -r CONFIRM
  if [ "$CONFIRM" != "$NAME" ]; then
    echo "Name did not match. Nothing deleted."
    exit 1
  fi
  neon DELETE "/projects/$PROJECT_ID/branches/$BRANCH_ID" >/dev/null
  echo "Deleted branch '$NAME'."
  exit 0
fi

if [ -n "$BRANCH_ID" ]; then
  echo "Branch '$NAME' already exists (id $BRANCH_ID); reusing it."
else
  PARENT_ID=$(echo "$BRANCHES_JSON" | jq -r '.branches[] | select(.default == true) | .id')
  PARENT_NAME=$(echo "$BRANCHES_JSON" | jq -r '.branches[] | select(.default == true) | .name')
  if [ -z "$PARENT_ID" ]; then
    echo "Could not find the default branch to clone from."
    exit 1
  fi
  echo "Creating branch '$NAME' from default branch '$PARENT_NAME' ..."
  BODY=$(jq -n --arg n "$NAME" --arg p "$PARENT_ID" \
    '{branch: {name: $n, parent_id: $p}, endpoints: [{type: "read_write"}]}')
  BRANCH_ID=$(neon POST "/projects/$PROJECT_ID/branches" "$BODY" | jq -r '.branch.id')
  echo "Created branch id $BRANCH_ID."
fi

DB_NAME=$(neon GET "/projects/$PROJECT_ID/branches/$BRANCH_ID/databases" | jq -r '.databases[0].name')
ROLE_NAME=$(neon GET "/projects/$PROJECT_ID/branches/$BRANCH_ID/roles" | jq -r '.roles[0].name')
URI=$(neon GET "/projects/$PROJECT_ID/connection_uri?branch_id=$BRANCH_ID&database_name=$DB_NAME&role_name=$ROLE_NAME&pooled=false" | jq -r '.uri')

echo ""
echo "Branch connection string (UNPOOLED). Run the API with:"
echo ""
echo "  DATABASE_URL='$URI' TODO_SESSION_SECRET='smoke-secret' mvn -pl api exec:java"
echo ""
echo "This is a CLONE. Writes here never reach production. Delete it when finished:"
echo "  bash scripts/neon-branch.sh --delete --name $NAME"
echo ""

KEY=""
