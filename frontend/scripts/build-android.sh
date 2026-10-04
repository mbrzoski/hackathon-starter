#!/usr/bin/env bash
# AND-01: builds the Android app (Capacitor) around one of the Angular apps, with the backend address baked in.
#
#   scripts/build-android.sh https://192.168.1.50          # the listening device (Nasłuch), kiosk by default
#   scripts/build-android.sh https://192.168.1.50 senior   # the senior's app
#
# Needs the Android SDK (ANDROID_HOME, platform 35, build tools) and JDK 21. The APK lands in
# android/app/build/outputs/apk/debug/. The device must trust the demo CA (deploy/ca.crt, user certificate).
set -euo pipefail
cd "$(dirname "$0")/.."

BACKEND="${1:?Usage: $0 https://<backend address> [listen|senior]}"
APP="${2:-listen}"
if [[ "$BACKEND" != https://* ]]; then
  echo "The backend must be https:// (AND-02: no cleartext traffic)." >&2
  exit 2
fi
if [[ "$APP" != listen && "$APP" != senior ]]; then
  echo "App must be listen or senior." >&2
  exit 2
fi

npm run generate:api
npx ng build -c "production,$APP" --output-path dist/android-web --define "ANIOL_BACKEND_ORIGIN='$BACKEND'"
npx cap sync android
if [[ -z "${ANDROID_HOME:-}" && ! -d "$HOME/Library/Android/sdk" ]]; then
  echo "Web part ready in android/. Install the Android SDK (or Android Studio) and run: cd android && ./gradlew assembleDebug" >&2
  exit 1
fi
(cd android && ./gradlew assembleDebug)
echo "APK: android/app/build/outputs/apk/debug/app-debug.apk"
