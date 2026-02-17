#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

PROTO_DIR="$PROJECT_ROOT/shared/proto"
OUTPUT_DIR="$PROJECT_ROOT/client/Assets/Scripts/Generated/Proto"

if ! command -v protoc &> /dev/null; then
    echo "Error: protoc is not installed. Install via: brew install protobuf"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "Generating C# protobuf files..."
protoc \
    --proto_path="$PROTO_DIR" \
    --csharp_out="$OUTPUT_DIR" \
    "$PROTO_DIR"/*.proto

echo "Generated C# files in $OUTPUT_DIR:"
ls -la "$OUTPUT_DIR"
