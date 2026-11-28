#!/usr/bin/env bash
# Fetches llama.cpp for the native build.
# Pinned to a commit that is known to build, so upstream changes cannot break CI.
# Override with any commit, tag or branch: LLAMA_CPP_TAG=master bash fetch_llama_cpp.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ -f llama.cpp/CMakeLists.txt ]; then
    echo "llama.cpp already present"
    exit 0
fi

REF="${LLAMA_CPP_TAG:-8e23278c7c36f4429fb237467d50e2935a91e655}"
git init -q llama.cpp
git -C llama.cpp fetch -q --depth 1 https://github.com/ggml-org/llama.cpp.git "$REF"
git -C llama.cpp checkout -q FETCH_HEAD
echo "llama.cpp cloned at $(git -C llama.cpp rev-parse --short HEAD)"
