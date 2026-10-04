#!/usr/bin/env bash
# Downloads the Polish Vosk model (about 50 MB, Apache 2.0) into backend/models/.
# Idempotent: does nothing if the model directory already exists.
set -euo pipefail

MODEL_NAME="vosk-model-small-pl-0.22"
MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${ROOT}/backend/models"

if [ -d "${TARGET}/${MODEL_NAME}" ]; then
    echo "Model already present: ${TARGET}/${MODEL_NAME}"
    exit 0
fi

mkdir -p "${TARGET}"
ZIP="$(mktemp "${TMPDIR:-/tmp}/${MODEL_NAME}.XXXXXX.zip")"
trap 'rm -f "${ZIP}"' EXIT

echo "Downloading ${MODEL_URL}"
if ! curl --fail --location --silent --show-error --output "${ZIP}" "${MODEL_URL}"; then
    echo "ERROR: could not download the Vosk model from ${MODEL_URL}. Check your connection or the address at https://alphacephei.com/vosk/models" >&2
    exit 1
fi

if ! unzip -q -o "${ZIP}" -d "${TARGET}"; then
    rm -rf "${TARGET:?}/${MODEL_NAME}"
    echo "ERROR: the downloaded file is not a valid zip archive." >&2
    exit 1
fi

if [ ! -d "${TARGET}/${MODEL_NAME}" ]; then
    echo "ERROR: archive did not contain ${MODEL_NAME}." >&2
    exit 1
fi
echo "Model ready: ${TARGET}/${MODEL_NAME}"
