#!/usr/bin/env bash
# Trusts the demo CA (deploy/ca.crt, written by run-demo.sh) on this laptop, so its browsers open https://<ip>/...
# without warnings. Tablets and phones: open http://<laptop-ip>/ and follow the start page (README).
set -euo pipefail
CERT="$(cd "$(dirname "$0")" && pwd)/ca.crt"
[[ -f "$CERT" ]] || { echo "No $CERT yet: run deploy/run-demo.sh first." >&2; exit 1; }

case "$(uname -s)" in
  Darwin)
    # Login keychain: no sudo; macOS asks for your password once to change trust settings. Chrome and Safari use it.
    security add-trusted-cert -r trustRoot -k "$HOME/Library/Keychains/login.keychain-db" "$CERT"
    ;;
  Linux)
    sudo cp "$CERT" /usr/local/share/ca-certificates/aniol-stroz-demo.crt
    sudo update-ca-certificates
    ;;
  *)
    echo "Import $CERT by hand as a trusted root certificate." >&2
    exit 1
    ;;
esac
echo "Trusted: $(openssl x509 -in "$CERT" -noout -subject). Firefox keeps its own list: import the file there if needed."
