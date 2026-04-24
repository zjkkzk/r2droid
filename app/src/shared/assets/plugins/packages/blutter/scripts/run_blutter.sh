#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-}"
OUT_DIR="${2:-}"

if [ -z "$APK_PATH" ] || [ -z "$OUT_DIR" ]; then
  echo "Usage: run_blutter.sh <apk-path> <output-dir>" >&2
  exit 2
fi

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 2
fi

export DEBIAN_FRONTEND=noninteractive
export BLUTTER_DIR="/opt/blutter"
export VENV_DIR="/opt/r2droid-plugin-venvs/blutter"
export PATH="$VENV_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 127
  }
}

ensure_blutter() {
  if [ ! -x "$VENV_DIR/bin/python" ]; then
    echo "Creating Python venv: $VENV_DIR"
    python3 -m venv "$VENV_DIR"
    "$VENV_DIR/bin/python" -m pip install --upgrade pip setuptools wheel
  fi

  if [ ! -d "$BLUTTER_DIR/.git" ]; then
    echo "Cloning blutter..."
    mkdir -p /opt
    rm -rf "$BLUTTER_DIR"
    git clone --depth 1 https://github.com/worawit/blutter "$BLUTTER_DIR"
  else
    echo "Updating blutter..."
    (
      cd "$BLUTTER_DIR"
      git fetch --depth 1 origin '+refs/heads/*:refs/remotes/origin/*'
      DEFAULT_BRANCH="$(git remote show origin 2>/dev/null | awk '/HEAD branch/ {print $NF}' || true)"
      if [ -z "$DEFAULT_BRANCH" ]; then
        if git show-ref --verify --quiet refs/remotes/origin/main; then
          DEFAULT_BRANCH=main
        elif git show-ref --verify --quiet refs/remotes/origin/master; then
          DEFAULT_BRANCH=master
        else
          DEFAULT_BRANCH="$(git for-each-ref --format='%(refname:short)' refs/remotes/origin | sed 's#^origin/##' | grep -v '^HEAD$' | head -n 1)"
        fi
      fi
      [ -n "$DEFAULT_BRANCH" ] || { echo "Cannot determine blutter default branch" >&2; exit 128; }
      git reset --hard "origin/$DEFAULT_BRANCH"
    )
  fi

  echo "Ensuring blutter Python dependencies..."
  "$VENV_DIR/bin/pip" install requests pyelftools

  if [ -f "$BLUTTER_DIR/requirements.txt" ]; then
    echo "Installing/updating blutter Python requirements..."
    "$VENV_DIR/bin/pip" install -r "$BLUTTER_DIR/requirements.txt"
  fi
}

find_abi_dir() {
  local root="$1"
  for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    if [ -f "$root/lib/$abi/libapp.so" ]; then
      echo "$root/lib/$abi"
      return 0
    fi
  done

  local found
  found="$(find "$root" -path '*/libapp.so' -type f 2>/dev/null | head -n 1 || true)"
  if [ -n "$found" ]; then
    dirname "$found"
    return 0
  fi

  return 1
}

need_cmd unzip
need_cmd python3
need_cmd git

if ! pkg-config --exists capstone 2>/dev/null; then
  echo "Missing capstone development package; installing libcapstone-dev..."
  apt-get update
  apt-get install -y --no-install-recommends libcapstone-dev capstone-tool
fi

ensure_blutter

RUN_ID="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="/tmp/r2droid-blutter-$RUN_ID"
EXTRACT_DIR="$WORK_DIR/apk"
mkdir -p "$EXTRACT_DIR" "$OUT_DIR"

cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

echo "Extracting APK..."
unzip -q "$APK_PATH" -d "$EXTRACT_DIR"

ABI_DIR="$(find_abi_dir "$EXTRACT_DIR")" || {
  echo "No Flutter libapp.so found in APK. Is this a Flutter APK?" >&2
  exit 3
}

if [ ! -f "$ABI_DIR/libflutter.so" ]; then
  echo "Warning: libflutter.so was not found next to libapp.so ($ABI_DIR). Blutter may fail." >&2
fi

echo "Selected ABI directory: $ABI_DIR"
echo "Output directory: $OUT_DIR"

BLUTTER_SCRIPT="$BLUTTER_DIR/blutter.py"
if [ ! -f "$BLUTTER_SCRIPT" ]; then
  BLUTTER_SCRIPT="$(find "$BLUTTER_DIR" -maxdepth 2 -name 'blutter.py' -type f | head -n 1 || true)"
fi

if [ -z "$BLUTTER_SCRIPT" ] || [ ! -f "$BLUTTER_SCRIPT" ]; then
  echo "blutter.py not found under $BLUTTER_DIR" >&2
  exit 4
fi

cd "$BLUTTER_DIR"
echo "Running blutter..."
"$VENV_DIR/bin/python" "$BLUTTER_SCRIPT" "$ABI_DIR" "$OUT_DIR"

echo "Done. Results are in: $OUT_DIR"
