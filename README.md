# TGImageEditor

An offline Android image editor extracted and adapted from the open-source Telegram/Nekogram editor architecture.

The project is split into four modules:

- `editor-model`: platform-independent edit document and parameter models.
- `editor-engine`: Android bitmap decoding, rendering, and export primitives.
- `editor-ui`: reusable editor UI and tool coordination.
- `app`: document picker, lifecycle, MediaStore integration, and the application shell.

The initial milestone provides an import/preview/export loop with EXIF orientation normalization. Crop, filter, paint, and text tools will be added as independent vertical slices.

## Build

Use JDK 17 or newer and an Android SDK containing API 36:

```sh
./gradlew :app:assembleDebug
```

## Licensing and provenance

This project is licensed under GNU GPL version 2 or later. Portions adapted from Telegram for Android and Nekogram retain their source headers. The initial reference revision is Nekogram commit `e924154e8d3b99a645b0521013ff9b501b28e8ce`.

See `NOTICE` and `LICENSE` for details.

