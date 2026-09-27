# Architecture

## Dependency boundary

```text
app ───────────────► editor-ui ─────► editor-model
 │                       │                  ▲
 ├────► editor-engine ◄──┘                  │
 │             │                            │
 └────► nekogram-core ◄─────────────────────┘
```

`editor-model` is the stable contract. An `EditDocument` stores the normalized source identity plus crop, filter, paint stroke, and text entity state. Every update returns a new document, which lets the app keep bounded undo and redo stacks without copying bitmaps.

`nekogram-core` is the extraction boundary. It owns the retained `org.telegram` filter/paint sources, the minimum compatibility stubs needed to compile them, and the standalone offscreen EGL hosts. It has no dependency on `app`, `editor-ui`, or `editor-engine`.

`editor-engine` decodes source images, coordinates the render order, and delegates filter and paint pixels to `nekogram-core`. Its authoritative order is:

1. Normalize source orientation while decoding.
2. Rotate and mirror, straighten, then crop.
3. Apply tonal adjustments, curve, sharpening, and focus blur.
4. Composite paint and blur-brush strokes.
5. Draw text entities.

The same `ImagePipeline` renders committed previews, history navigation, restored sessions, saved images, and shared images. Export reopens the persisted source URI and replays the document at a larger decode size, so it does not upscale the preview bitmap.

`editor-ui` converts gestures and controls into normalized model values. Coordinates stay in `[0, 1]`, allowing the engine to replay an edit at another resolution. It reuses the extracted `PhotoEditorSeekBar` and `PhotoFilterBlurControl`; crop, paint gesture capture, and text interaction are standalone views. It may call engine drawing helpers for live previews, but it does not own source decoding, export, MediaStore, or lifecycle state.

`app` owns Android integration. It opens `GalleryPickerActivity` on launch, queries the local `MediaStore` image collection into a full-screen four-column grid, deliberately omits Nekogram's camera cell, and hands one selected content URI to `MainActivity`. It keeps at most 30 previous document snapshots, serializes the active document into an internal cache file for Activity recreation, and relies on scoped media permission for picker reads.

## Extraction boundary

The project keeps the parts required for offline static-image editing:

- crop and transforms;
- tonal controls, curve, sharpening, and focus blur;
- Nekogram stamped brushes, paint shaders, shapes, eraser, blur brush, and standalone text entities;
- document history and export replay.

It excludes Telegram accounts and API access, remote stickers and custom emoji, messages, reactions, weather and location entities, stories, video timelines, audio, and upload flows. These features cross into Telegram session, media, or protocol layers and are not required by the standalone editor.

## Fidelity boundary

The committed filter image uses Nekogram's original shader strings and upstream pass order. The committed paint image uses its original path stamper, shader set, brush textures, composite programs, and shape fragment shader. Standalone hosts replace Telegram's GL thread, native JNI entry points, model objects, and undo store while preserving their externally visible inputs.

Crop, text entities, gallery data loading, top-level navigation, and live paint feedback are local implementations. The gallery keeps the reference grid geometry and Material You-aware colors but does not import `PhotoPickerActivity`, `PhotoAttachPhotoCell`, `ActionBar`, or Telegram media controllers because those classes pull in session, messaging, download, and story runtime layers. See `provenance.md` for the file-level boundary.
