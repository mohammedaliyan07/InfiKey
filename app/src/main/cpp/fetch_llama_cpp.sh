#!/usr/bin/env bash
# Fetches llama.cpp for the native build.
# LFM2.5 support requires a recent llama.cpp, so we default to master.
# Pin a tag with: LLAMA_CPP_TAG=b5890 bash fetch_llama_cpp.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ -d llama.cpp/CMakeLists.txt ] || [ -f llama.cpp/CMakeLists.txt ]; then
    echo "llama.cpp already present"
    exit 0
fi

TAG="${LLAMA_CPP_TAG:-master}"
if ! git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git llama.cpp 2>/dev/null; then
    echo "tag '$TAG' not found, falling back to master"
    git clone --depth 1 https://github.com/ggml-org/llama.cpp.git llama.cpp
fi
echo "llama.cpp cloned at $(git -C llama.cpp rev-parse --short HEAD)"
