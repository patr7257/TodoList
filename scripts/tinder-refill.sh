#!/usr/bin/env bash
# Posts a batch of TodoTinder cards to the authenticated refill import endpoint
# (issue #59). The mac/Linux twin of scripts/tinder-refill.ps1; Windows is the
# default, so the refill prompt hands over the .ps1 form.
# Run: cd /path/to/TodoList && bash scripts/tinder-refill.sh
#
# It exists so the refill prompt never has to contain a token. A Claude session
# writes the batch to a JSON file and stops there; this script prompts for the
# session token, shows what would be sent, and asks before sending.
#
# The file it reads is exactly what the endpoint takes:
#   {"source":"claude-refill","entries":[{"text":"...","metadata":{}}]}
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"

DEFAULT_API="https://api.todolist.patrickrobel.dk"
read -r -p "API base URL (Enter for $DEFAULT_API): " API_BASE
if [ -z "${API_BASE:-}" ]; then API_BASE="$DEFAULT_API"; fi
API_BASE="${API_BASE%/}"

read -r -p "Deck key (for example: aktiviteter): " DECK
if [ -z "${DECK:-}" ]; then echo "No deck key given, nothing to do."; exit 0; fi

DEFAULT_FILE="refill.json"
read -r -p "Path to the entries JSON file (Enter for $DEFAULT_FILE): " FILE
if [ -z "${FILE:-}" ]; then FILE="$DEFAULT_FILE"; fi
if [ ! -f "$FILE" ]; then echo "No such file: $FILE"; exit 1; fi

URL="$API_BASE/api/todo/tinder/decks/$DECK/entries"

echo
echo "About to send:"
echo "  endpoint : POST $URL"
echo "  file     : $FILE"
echo "Cards already in the deck are skipped by the endpoint, not duplicated."
echo

read -r -p "Send it? (y/N): " GO
if [ "${GO:-}" != "y" ] && [ "${GO:-}" != "Y" ]; then echo "Cancelled, nothing was sent."; exit 0; fi

# -s keeps the token off the terminal; it stays in a shell variable that dies
# with this process and is never exported.
read -r -s -p "TodoList session token (input hidden): " TOKEN
echo
if [ -z "${TOKEN:-}" ]; then echo "No token given, nothing was sent."; exit 0; fi

curl -sS -X POST "$URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary "@$FILE"
echo
