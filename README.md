# TGImageEditor

TGImageEditor is an offline, static-image Android editor extracted from Nekogram's image-editing implementation. It keeps the upstream OpenGL filter and paint code while removing Telegram accounts, network, stories, stickers, custom emoji, and video.

## Features

- Opens directly into a full-screen four-column local gallery grid, without a camera tile, and normalizes every EXIF orientation.
- Non-destructive free crop, common aspect ratios, 90-degree rotation, mirror, and ±45-degree straightening.
- Enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, skin softening, five-point luminance curve, and radial or linear focus blur.
- Pen, marker, neon, blur brush, eraser, arrow, rectangle, and oval tools with color and width controls.
- Text entities with plain, outline, and frame styles plus drag, pinch-scale, and rotation gestures.
- Tool-local paint undo and 30-step document undo/redo across applied crop, adjustment, paint, and text changes.
- Recreation recovery, full edit replay for export, MediaStore save, and Android share-sheet output.

## Modules

- `editor-model` contains immutable, serializable edit documents. It has no Android dependency.
- `nekogram-core` contains the trimmed upstream shader, paint, blur-control, and seek-bar sources plus standalone EGL adapters and small runtime stubs.
- `editor-engine` owns EXIF decoding, crop transforms, calls into the Nekogram GL pipelines, text compositing, and export.
- `editor-ui` owns reusable crop, adjustment, paint, and entity interaction views and directly subclasses the extracted blur control.
- `app` owns the full-screen gallery picker, history, lifecycle recovery, MediaStore, sharing, and the application shell.

See [`docs/architecture.md`](docs/architecture.md) for the dependency rules, render order, and deliberately excluded Telegram features.

## Build

Use JDK 17 or 21 and an Android SDK containing API 36. Set `ANDROID_HOME`, or create the usual untracked `local.properties` with `sdk.dir`. On macOS the verification script automatically uses Android Studio's bundled JBR when `JAVA_HOME` is unset:

```sh
./gradlew :app:assembleDebug
```

Run the complete local verification suite with:

```sh
./scripts/test-editor.sh
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Rendering scope

Committed previews and exports run through an offscreen EGL context. The tonal, curve, sharpen, skin, grain, vignette, enhance, and radial/linear focus passes use Nekogram's `FilterShaders` and the same pass order and parameter transforms as `FilterGLThread`. Enhance uses a Java port of Nekogram's native `calcCDT`. Paint uses Nekogram's `Render`, `ShaderSet`, brush stamp textures, composite shaders, shape shader, and the radius-8 `fastBlurMore` behavior used by the blur brush. Preview decoding is capped at 3840 pixels on the longest edge and export replay at 8192 pixels to bound memory use.

The extraction still adapts bitmap upload/readback, document coordinates, and EGL ownership. Crop, text rendering, editor navigation, and the local MediaStore picker are standalone implementations styled to match the retained Nekogram surfaces. Live paint feedback uses Canvas while the committed image is rendered by the upstream GL paint path. Device-level pixel and visual comparison therefore remains an explicit manual acceptance step.

## Licensing and provenance

This project is licensed under GNU GPL version 2 or later. The pinned reference revision is Nekogram commit `e924154e8d3b99a645b0521013ff9b501b28e8ce`.

See `NOTICE`, `LICENSE`, and [`docs/provenance.md`](docs/provenance.md) for exact and adapted source boundaries.
