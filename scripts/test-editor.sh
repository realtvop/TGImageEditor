#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
./gradlew :editor-model:test :app:assembleDebug :app:lintDebug
