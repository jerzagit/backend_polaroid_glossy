#!/usr/bin/env bash
# Flip production mail from the personal Gmail account to Resend on polaroidglossy.my.
#
# Run this once the four Resend DNS records exist in the Cloudflare zone and
# Resend reports the domain as verified. Steps:
#   1. confirm the records resolve publicly (DNS-over-HTTPS)
#   2. wait for Resend to mark the domain verified
#   3. set the Fly secrets the backend reads (spring.mail.*)
#   4. deploy and health-check
#   5. send one real test email through Resend
#
#   scripts/enable-resend-mail.sh you@example.com     # test recipient
#   scripts/enable-resend-mail.sh --check             # DNS/verification only
set -euo pipefail

cd "$(dirname "$0")/.."

APP="${FLY_APP:-polaroid-glossy-backend}"
DOMAIN="polaroidglossy.my"
FROM_ADDR="no-reply@polaroidglossy.my"
TOKEN_FILE=".resend-token.txt"
CHECK_ONLY=0
TEST_TO=""

for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=1 ;;
    -* ) echo "unknown argument: $arg" >&2; exit 2 ;;
    *) TEST_TO="$arg" ;;
  esac
done

[ -f "$TOKEN_FILE" ] || { echo "error: $TOKEN_FILE missing (Resend API key, one line)" >&2; exit 1; }
RT="$(tr -d ' \r\n' < "$TOKEN_FILE")"
API="https://api.resend.com"

say() { printf '%s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

# --- 1. DNS records ------------------------------------------------------
say "== DNS records for $DOMAIN =="
missing=0
lookup() { # name type expected-fragment
  local raw
  raw="$(curl -fsS --max-time 20 -H 'accept: application/dns-json' \
      "https://cloudflare-dns.com/dns-query?name=$1&type=$2" || true)"
  if printf '%s' "$raw" | grep -qi -- "$3"; then
    printf '  ok   %-6s %-45s\n' "$2" "$1"
  else
    printf '  MISS %-6s %-45s\n' "$2" "$1"
    missing=$((missing + 1))
  fi
}

lookup "resend._domainkey.$DOMAIN" TXT "p=MIGf"
lookup "send.$DOMAIN"              MX  "feedback-smtp"
lookup "send.$DOMAIN"              TXT "include:amazonses.com"
lookup "rsend.$DOMAIN"             CNAME "forge.rmta.net"

if [ "$missing" -ne 0 ]; then
  say ""
  say "$missing record(s) missing. Add them in Cloudflare (DNS -> Records) and re-run:"
  say "  TXT   resend._domainkey   p=MIGf...            (DKIM, from Resend dashboard)"
  say "  MX    send  prio 10       feedback-smtp.ap-northeast-1.amazonses.com"
  say "  TXT   send                v=spf1 include:amazonses.com ~all"
  say "  CNAME rsend               send.forge.rmta.net   (proxy OFF)"
  exit 1
fi

# --- 2. Resend verification ---------------------------------------------
say ""
say "== Resend domain status =="
DOM_ID="$(curl -fsS --max-time 30 -H "Authorization: Bearer $RT" "$API/domains" \
  | tr '}' '\n' | grep -F "\"name\":\"$DOMAIN\"" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)"
[ -n "$DOM_ID" ] || fail "domain $DOMAIN is not registered in Resend"

status=""
for attempt in $(seq 1 20); do
  body="$(curl -fsS --max-time 30 -H "Authorization: Bearer $RT" "$API/domains/$DOM_ID")"
  status="$(printf '%s' "$body" | sed -n 's/.*"status":"\([a-z_]*\)".*/\1/p' | head -1)"
  say "  [$attempt] $status"
  [ "$status" = "verified" ] && break
  case "$status" in failed|failure|temporary_failure) fail "Resend could not verify: $body" ;; esac
  sleep 15
done
[ "$status" = "verified" ] || fail "domain still '$status' after 5 minutes; check the DKIM/MX records"
say "  domain verified"

if [ "$CHECK_ONLY" -eq 1 ]; then
  say ""
  say "records + verification ok; skipping secrets/deploy (--check)"
  exit 0
fi

# --- 3. Fly secrets ------------------------------------------------------
say ""
say "== Fly secrets =="
flyctl secrets set \
  --app "$APP" \
  SPRING_MAIL_HOST=smtp.resend.com \
  SPRING_MAIL_PORT=587 \
  SPRING_MAIL_USERNAME=resend \
  SPRING_MAIL_PASSWORD="$RT" \
  MAIL_FROM="$FROM_ADDR" \
  SUPPORT_EMAIL="payment@$DOMAIN" >/dev/null
say "  set SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD, MAIL_FROM, SUPPORT_EMAIL"

# --- 4. Deploy -----------------------------------------------------------
say ""
say "== Deploy =="
scripts/deploy-prod.sh

# --- 5. Test send --------------------------------------------------------
if [ -z "$TEST_TO" ]; then
  say ""
  say "no test recipient given; run: scripts/enable-resend-mail.sh you@example.com"
  exit 0
fi

say ""
say "== Test send to $TEST_TO =="
resp="$(curl -fsS --max-time 40 -X POST "$API/emails" \
  -H "Authorization: Bearer $RT" -H 'Content-Type: application/json' \
  -d "{\"from\":\"Polaroid Glossy <$FROM_ADDR>\",\"to\":[\"$TEST_TO\"],\"subject\":\"Mail setup test - polaroidglossy.my\",\"text\":\"If you can read this, Resend is sending as $FROM_ADDR with SPF, DKIM and DMARC aligned.\"}")"
say "  $resp"
say ""
say "Check the delivered message's headers for spf=pass, dkim=pass, dmarc=pass."
