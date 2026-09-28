# Nekogram source provenance

The extraction is pinned to Nekogram revision `e924154e8d3b99a645b0521013ff9b501b28e8ce` under GNU GPL version 2 or later.

## Byte-identical retained sources

The following files match the corresponding path below `TMessagesProj/src/main/java/` at the pinned revision:

| Local path below `nekogram-core/src/main/java/` | Role |
| --- | --- |
| `org/telegram/ui/Components/FilterShaders.java` | Filter shader programs and pass implementation |
| `org/telegram/ui/Components/Paint/Render.java` | Brush path tessellation and stamp spacing |
| `org/telegram/ui/Components/Paint/ShaderSet.java` | Paint, composite, blur-brush, and shape programs |
| `org/telegram/ui/Components/PhotoEditorSeekBar.java` | Adjustment control |

## Adapted upstream sources

| Local source | Adaptation |
| --- | --- |
| `org/telegram/ui/Components/Paint/Brush.java` | Replaces Telegram resource loading with embedded copies of the same WebP brush assets. |
| `org/telegram/ui/Components/PhotoFilterBlurControl.java` | Adds a setter so a serialized standalone blur state can restore the upstream control. |
| Paint support classes (`GLMatrix`, `Path`, `Point`, `RenderState`, `Shader`, `Swatch`, `Utils`) | Retained dependencies compiled against the trimmed runtime boundary. |
| `org/telegram/messenger/Utilities.java` | Java implementation of the relevant `calcCDT` behavior from `TMessagesProj/jni/image.cpp`. |

The embedded `paint_radial_brush.webp`, `paint_elliptical_brush.webp`, and `paint_neon_brush.webp` payloads are byte-identical to the pinned Nekogram resources before Base64 wrapping.

The editor chrome embeds the pinned xhdpi `media_crop`, `media_draw`, `media_settings`, `msg_photo_rotate`, `msg_photo_flip`, `msg_photo_settings`, `msg_photo_blur`, and `msg_photo_curve` WebP payloads without modifying their bytes.

## Standalone adapters

`NekogramFilterPipeline` creates an offscreen EGL context and invokes the upstream filter passes in `FilterGLThread` order. `NekogramPaintPipeline` maps normalized edit-document strokes to the upstream renderer and shader set, owns offscreen textures, and ports the blur brush's one-eighth downsample plus native radius-8 `fastBlurMore` step.

Small classes under `org.telegram.messenger`, `org.telegram.ui`, and `org.telegram.ui.Stories.recorder` provide only the fields or methods required by retained sources. They do not implement Telegram accounts, messages, downloads, network access, stories, or media playback.

## Independent application code

The document model, EXIF decoder, crop renderer and interaction, text renderer and interaction, lifecycle/history layer, MediaStore export, share flow, gallery query/thumbnail loader, action bar shell, and live Canvas paint feedback are standalone code. The gallery uses the reference three-column portrait layout and omits the camera cell, but it does not copy Telegram's picker runtime.

Run `./scripts/test-editor.sh` to compile, lint, test the model, and verify the four byte-identical Java sources against a local Nekogram checkout when it is available at `../Nekogram`.
