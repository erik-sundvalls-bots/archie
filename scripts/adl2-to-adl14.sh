#!/usr/bin/env bash
# Down-convert ADL 2 → ADL 1.4 using this Archie fork's CLI.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <input.adls|adl> [output.adl]" >&2
  echo "See docs/ADL2_TO_ADL14.md" >&2
  exit 2
fi
ARGS="$1"
if [[ $# -eq 2 ]]; then
  ARGS="$1 $2"
fi
exec ./gradlew :tools:runADL2ToADL14 -PcliArgs="$ARGS"
