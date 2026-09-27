# Architecture

## Dependency boundary

```text
app ───────────────► editor-ui ─────► editor-model
 │                       │
 └────► editor-engine ◄──┘
               │
               └───────────────► editor-model
```

`editor-model` is the stable contract. An `EditDocument` stores the normalized source identity plus crop, filter, paint stroke, and text entity state. Every update returns a new document, which lets the app keep bounded undo and redo stacks without copying bitmaps.

`editor-engine` is the only module that decodes or produces image pixels. Its authoritative order is:

1. Normalize source orientation while decoding.
2. Rotate and mirror, straighten, then crop.
3. Apply tonal adjustments, curve, sharpening, and focus blur.
4. Composite paint and blur-brush strokes.
5. Draw text entities.

The same `ImagePipeline` renders committed previews, history navigation, restored sessions, saved images, and shared images. Export reopens the persisted source URI and replays the document at a larger decode size, so it does not upscale the preview bitmap.

`editor-ui` converts gestures and controls into normalized model values. Coordinates stay in `[0, 1]`, allowing the engine to replay an edit at another resolution. It may call engine drawing helpers for live previews, but it does not own source decoding, export, MediaStore, or lifecycle state.

`app` owns Android integration. It keeps at most 30 previous document snapshots, serializes the active document into an internal cache file for Activity recreation, and persists picker read permission when the provider supports it.

## Extraction boundary

The project keeps the parts required for offline static-image editing:

- crop and transforms;
- tonal controls, curve, sharpening, and focus blur;
- brushes, shapes, eraser, blur brush, and text entities;
- document history and export replay.

It excludes Telegram accounts and API access, remote stickers and custom emoji, messages, reactions, weather and location entities, stories, video timelines, audio, and upload flows. These features cross into Telegram session, media, or protocol layers and are not required by the standalone editor.

## Differences from Telegram/Nekogram

Telegram's production editor uses EGL/OpenGL shaders and native code for several effects. This project implements the equivalent standalone feature set with Android bitmap and Canvas operations. The module boundary and parameter semantics support replacing `editor-engine` with a GL renderer later, but the current renderer does not promise pixel-identical output.
