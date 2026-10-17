#!/usr/bin/env bash
# Deploy the backend to Fly.io from this machine.
#
# Mirrors .github/workflows/fly-deploy.yml for when GitHub Actions is
# unavailable (for example while the GitHub account is billing-locked).
#
#   scripts/deploy-prod.sh           # deploy HEAD and wait for health
#   scripts/deploy-prod.sh --check   # preflight only, no deploy
set -euo pipefail

cd "$(dirname "$0")/.."

APP="${FLY_APP:-polaroid-glossy-backend}"
CONFIG="fly.toml"
CHECK_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --check|-n) CHECK_ONLY=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

command -v flyctl >/dev/null 2>&1 || { echo "error: flyctl is not on PATH" >&2; exit 1; }
[ -f "$CONFIG" ] || { echo "error: $CONFIG not found" >&2; exit 1; }

if [ -z "${FLY_API_TOKEN:-}" ] && ! flyctl auth whoami >/dev/null 2>&1; then
  echo "error: no Fly credentials (set FLY_API_TOKEN or run flyctl auth login)" >&2
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "warning: working tree has uncommitted changes; the image will include them" >&2
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$branch" != "main" ]; then
  echo "warning: deploying from '$branch' instead of main" >&2
fi

if git rev-parse --verify --quiet origin/main >/dev/null; then
  ahead="$(git rev-list --count origin/main..HEAD)"
  if [ "$ahead" -gt 0 ]; then
    echo "warning: $ahead commit(s) not pushed to origin/main" >&2
  fi
fi

echo "target: $APP  $(git rev-parse --short HEAD) on $branch"

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "preflight ok; skipping deploy (--check)"
  exit 0
fi

flyctl deploy --remote-only --config "$CONFIG" --app "$APP"

echo "confirming $APP is healthy..."
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 "https://$APP.fly.dev/api/health" >/dev/null 2>&1; then
    echo "healthy: https://$APP.fly.dev/api/health"
    exit 0
  fi
  sleep 5
done

echo "error: health check did not pass within 150s; run: flyctl logs --app $APP" >&2
exit 1
