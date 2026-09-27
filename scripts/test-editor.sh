#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "${HOME}/Library/Android/sdk" ]; then
    ANDROID_HOME="${HOME}/Library/Android/sdk"
    export ANDROID_HOME
fi

reference="../Nekogram/TMessagesProj/src/main/java/org/telegram"
if [ -d "$reference" ]; then
    cmp "$reference/ui/Components/FilterShaders.java" \
        nekogram-core/src/main/java/org/telegram/ui/Components/FilterShaders.java
    cmp "$reference/ui/Components/Paint/Render.java" \
        nekogram-core/src/main/java/org/telegram/ui/Components/Paint/Render.java
    cmp "$reference/ui/Components/Paint/ShaderSet.java" \
        nekogram-core/src/main/java/org/telegram/ui/Components/Paint/ShaderSet.java
    cmp "$reference/ui/Components/PhotoEditorSeekBar.java" \
        nekogram-core/src/main/java/org/telegram/ui/Components/PhotoEditorSeekBar.java
fi

./gradlew :editor-model:test :app:assembleDebug :app:lintDebug
