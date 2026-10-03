#!/usr/bin/env bash
# Builds the frontend (three apps) and the backend, then starts backend + Caddy (+ tunnel) with Docker Compose.
#
#   deploy/run-demo.sh          # variant (a): HTTPS in the LAN, Caddy's own CA
#   deploy/run-demo.sh tunnel   # variant (b): public HTTPS through a Cloudflare quick tunnel
#
# Settings come from .env in the repository root (ANTHROPIC_API_KEY, APP_MODE) and from the environment
# (LAN_IP to override the detected address). Ctrl+C stops everything.
set -euo pipefail

VARIANT="${1:-lan}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/deploy/docker-compose.yml")

if [[ "$VARIANT" != "lan" && "$VARIANT" != "tunnel" ]]; then
  echo "Usage: $0 [lan|tunnel]" >&2
  exit 2
fi

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

# prod refuses to start without a key unless the mode is MOCK (rule 7): say so instead of failing later.
if [[ -z "${ANTHROPIC_API_KEY:-}" && "${APP_MODE:-}" != "MOCK" ]]; then
  echo "ANTHROPIC_API_KEY is not set: starting with APP_MODE=MOCK (canned AI answers, badge MOCK)."
  export APP_MODE=MOCK
fi

# Docker Desktop on macOS: its credential helper (needed to pull images) is often not on PATH in a plain shell.
DOCKER_DESKTOP_BIN=/Applications/Docker.app/Contents/Resources/bin
[[ -d "$DOCKER_DESKTOP_BIN" ]] && export PATH="$PATH:$DOCKER_DESKTOP_BIN"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker Desktop and try again." >&2
  exit 1
fi

if [[ "$VARIANT" == "lan" ]]; then
  LAN_IP="${LAN_IP:-$(ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || true)}"
  if [[ -z "$LAN_IP" ]]; then
    echo "Cannot detect the LAN address. Run again with LAN_IP=192.168.x.y $0" >&2
    exit 1
  fi
  export CADDYFILE=Caddyfile.lan
  export SITE_ADDRESS="https://$LAN_IP, https://localhost"
  export PUBLIC_ORIGIN="https://$LAN_IP,https://localhost"
  export DEFAULT_SNI="$LAN_IP"
  PROFILE=()
else
  export CADDYFILE=Caddyfile.tunnel
  # The tunnel address is random and only known later; same-origin checks work through X-Forwarded-*.
  export PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-}"
  PROFILE=(--profile tunnel)
fi

echo "== Building the frontend (senior, listen, family)"
(cd "$ROOT/frontend" && npm install --no-audit --no-fund && npm run build)

echo "== Building the backend"
(cd "$ROOT/backend" && ./mvnw -q -DskipTests package)

SITE="$ROOT/deploy/site"
CA_COPY="$ROOT/deploy/ca.crt"

# On any exit (Ctrl+C, closed terminal, error, normal end): stop the containers and delete what this script
# generated (deploy/site, deploy/ca.crt). The CA itself stays in the caddy-data volume, so the certificate
# installed on the devices keeps working next time. `make clean-demo` does the same by hand.
STARTED=0
CLEANED=0
cleanup() {
  [[ "$CLEANED" == 1 ]] && return
  CLEANED=1
  echo
  if [[ "$STARTED" == 1 ]]; then
    echo "== Stopping the demo"
    "${COMPOSE[@]}" ${PROFILE[@]+"${PROFILE[@]}"} down || true
  fi
  rm -rf "$SITE" "$CA_COPY"
  echo "== Removed deploy/site and deploy/ca.crt"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

echo "== Assembling deploy/site"
rm -rf "$SITE"
mkdir -p "$SITE"
for app in senior listen family; do
  # The three builds only differ in index.html (everything else is content-hashed or identical).
  cp -R "$ROOT/frontend/dist/$app/browser/." "$SITE/"
  mv "$SITE/index.html" "$SITE/index-$app.html"
done

echo "== Starting backend and Caddy"
STARTED=1
"${COMPOSE[@]}" ${PROFILE[@]+"${PROFILE[@]}"} up -d --build

echo "== Waiting for Caddy and the backend"
WAIT_URL="https://localhost/api/status"
[[ "$VARIANT" == "tunnel" ]] && WAIT_URL="http://localhost/api/status"
for _ in $(seq 1 90); do
  curl -sfk -o /dev/null "$WAIT_URL" 2>/dev/null && break
  sleep 1
done

if [[ "$VARIANT" == "lan" ]]; then
  # Caddy's CA for this laptop (deploy/trust-ca.sh) and for the smoke test; the devices download it from the start page.
  "${COMPOSE[@]}" cp caddy:/data/caddy/pki/authorities/local/root.crt "$CA_COPY" >/dev/null
  echo "== Smoke test"
  node "$ROOT/deploy/smoke-test.mjs" --host "$LAN_IP" || echo "Smoke test failed: see above."
  echo
  echo "On each tablet or phone (same Wi-Fi) open:   http://$LAN_IP"
  echo "  The start page has the certificate (once per device) and the buttons for senior / listen / family."
  if command -v qrencode >/dev/null; then
    qrencode -t ansiutf8 "http://$LAN_IP"
  else
    echo "  (brew install qrencode prints a QR code of this address here)"
  fi
  echo "This laptop: deploy/trust-ca.sh once, then https://$LAN_IP/senior, /listen, /family."
else
  echo "Waiting for the tunnel address..."
  URL=""
  for _ in $(seq 1 60); do
    URL="$("${COMPOSE[@]}" ${PROFILE[@]+"${PROFILE[@]}"} logs cloudflared 2>/dev/null | grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)"
    [[ -n "$URL" ]] && break
    sleep 1
  done
  if [[ -n "$URL" ]]; then
    echo "== Smoke test"
    node "$ROOT/deploy/smoke-test.mjs" --tunnel "$URL" || echo "Smoke test failed (the tunnel can need a few more seconds): see above."
    echo
    echo "  senior:  $URL/senior"
    echo "  listen:  $URL/listen"
    echo "  family:  $URL/family"
    command -v qrencode >/dev/null && qrencode -t ansiutf8 "$URL"
  else
    echo "No tunnel address yet: check '${COMPOSE[*]} --profile tunnel logs cloudflared'."
  fi
fi
echo
echo "Logs follow. Ctrl+C stops the demo and removes deploy/site and deploy/ca.crt."
"${COMPOSE[@]}" ${PROFILE[@]+"${PROFILE[@]}"} logs -f backend caddy
