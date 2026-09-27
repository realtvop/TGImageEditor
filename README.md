# TGImageEditor

TGImageEditor is an offline, static-image Android editor based on the editor boundaries and interaction model studied in Telegram for Android and Nekogram. It has no account, network, story, sticker, custom emoji, or video dependency.

## Features

- Opens images through Android's document picker and normalizes every EXIF orientation.
- Non-destructive free crop, common aspect ratios, 90-degree rotation, mirror, and ±45-degree straightening.
- Enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, skin softening, five-point luminance curve, and radial or linear focus blur.
- Pen, marker, neon, blur brush, eraser, arrow, rectangle, and oval tools with color and width controls.
- Text entities with plain, outline, and frame styles plus drag, pinch-scale, and rotation gestures.
- Tool-local paint undo and 30-step document undo/redo across applied crop, adjustment, paint, and text changes.
- Recreation recovery, full edit replay for export, MediaStore save, and Android share-sheet output.

## Modules

- `editor-model` contains immutable, serializable edit documents. It has no Android dependency.
- `editor-engine` owns EXIF decoding, crop transforms, CPU adjustment rendering, paint/text compositing, and export.
- `editor-ui` owns reusable crop, adjustment, paint, and entity interaction views.
- `app` owns the document picker, history, lifecycle recovery, MediaStore, sharing, and the application shell.

See [`docs/architecture.md`](docs/architecture.md) for the dependency rules, render order, and deliberately excluded Telegram features.

## Build

Use JDK 17 or newer and an Android SDK containing API 36. Set `ANDROID_HOME`, or create the usual untracked `local.properties` with `sdk.dir`:

```sh
./gradlew :app:assembleDebug
```

Run the complete local verification suite with:

```sh
./scripts/test-editor.sh
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Rendering scope

The app uses a deterministic CPU bitmap renderer so the committed preview and saved image share one edit document and render order. Telegram's original editor uses EGL/OpenGL and native enhancement code, so exact pixel parity and real-time performance are outside this extraction. Preview decoding is capped at 3840 pixels on the longest edge and export replay at 8192 pixels to bound memory use.

## Licensing and provenance

This project is licensed under GNU GPL version 2 or later. The initial reference revision is Nekogram commit `e924154e8d3b99a645b0521013ff9b501b28e8ce`.

See `NOTICE` and `LICENSE` for details.
