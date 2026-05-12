#!/usr/bin/env bash
#
# compress-glb.sh — Phase A asset pipeline (FR §3 hybrid strategy step).
#
# Compresses a raw .glb (Sketchfab CC0 download, typically 50–200 MB) into a
# mobile-friendly Draco-compressed .glb (target 5–15 MB) using gltf-pipeline.
# Idempotent: if the output is newer than the input, skips the file.
#
# Usage:
#   scripts/compress-glb.sh <path-to.glb>                 # writes <name>.compressed.glb beside it
#   scripts/compress-glb.sh <path-to.glb> <out.glb>        # explicit output path
#   scripts/compress-glb.sh <dir>                          # processes every *.glb in dir
#   scripts/compress-glb.sh --inplace <path>               # overwrites input (backup .orig kept)
#
# Requires: Node.js 18+ (npx). gltf-pipeline is fetched on first run via
# `npx --yes gltf-pipeline@latest`; the package is cached after that.
#
# Notes on tuning:
#   --draco.compressionLevel 10 is the slowest/smallest preset; lower it
#   (e.g. 7) if encode time becomes a bottleneck during bulk runs.
#   Position bits 14 is sufficient for furniture-scale models; raise to 16
#   if you see visible faceting after compression.

set -euo pipefail

INPLACE=0
if [[ "${1:-}" == "--inplace" ]]; then
  INPLACE=1
  shift
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 [--inplace] <file.glb|directory> [output.glb]" >&2
  exit 2
fi

INPUT="$1"
OUTPUT="${2:-}"

if ! command -v npx >/dev/null 2>&1; then
  echo "error: npx not found; install Node.js (>=18)" >&2
  exit 1
fi

compress_one() {
  local in="$1"
  local out="$2"

  # Skip when output already exists and is newer than input.
  if [[ -f "$out" && "$out" -nt "$in" ]]; then
    echo "skip  $(basename "$in") (already up-to-date)"
    return 0
  fi

  local in_size
  in_size=$(wc -c <"$in" | tr -d ' ')

  npx --yes gltf-pipeline@latest \
    -i "$in" \
    -o "$out" \
    --draco.compressionLevel=10 \
    --draco.quantizePositionBits=14 \
    --draco.quantizeNormalBits=10 \
    --draco.quantizeTexcoordBits=12 \
    --draco.quantizeColorBits=8 \
    --draco.quantizeGenericBits=12 \
    >/dev/null

  local out_size
  out_size=$(wc -c <"$out" | tr -d ' ')
  local pct
  if [[ "$in_size" -gt 0 ]]; then
    pct=$(awk -v a="$in_size" -v b="$out_size" 'BEGIN { printf "%.1f", (1.0 - b/a) * 100.0 }')
  else
    pct="0.0"
  fi
  printf "done  %-40s %8s B -> %8s B  (%s%% smaller)\n" \
    "$(basename "$in")" "$in_size" "$out_size" "$pct"
}

process_file() {
  local in="$1"
  local out
  if [[ "$INPLACE" -eq 1 ]]; then
    cp "$in" "${in}.orig"
    local tmp
    tmp="${in}.tmp.glb"
    compress_one "${in}.orig" "$tmp"
    mv "$tmp" "$in"
  else
    if [[ -n "$OUTPUT" ]]; then
      out="$OUTPUT"
    else
      out="${in%.glb}.compressed.glb"
    fi
    compress_one "$in" "$out"
  fi
}

if [[ -d "$INPUT" ]]; then
  shopt -s nullglob
  for f in "$INPUT"/*.glb; do
    # Skip files our own outputs would have produced — keeps re-runs idempotent.
    case "$f" in
      *.compressed.glb|*.orig) continue ;;
    esac
    process_file "$f"
  done
elif [[ -f "$INPUT" ]]; then
  process_file "$INPUT"
else
  echo "error: $INPUT does not exist" >&2
  exit 1
fi
