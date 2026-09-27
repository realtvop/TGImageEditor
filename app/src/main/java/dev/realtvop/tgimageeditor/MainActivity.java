package dev.realtvop.tgimageeditor;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.realtvop.tgimageeditor.engine.DecodedImage;
import dev.realtvop.tgimageeditor.engine.ImageDecoder;
import dev.realtvop.tgimageeditor.engine.ImageExporter;
import dev.realtvop.tgimageeditor.ui.EditorView;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_IMAGE = 100;
    private static final int REQUEST_WRITE_IMAGES = 101;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditorView editorView;
    private Button saveButton;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(createContent());
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);
        editorView = new EditorView(this);
        root.addView(editorView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(12), dp(8), dp(12), dp(8));

        Button openButton = new Button(this);
        openButton.setText("Open");
        openButton.setOnClickListener(v -> openImage());
        actions.addView(openButton);

        saveButton = new Button(this);
        saveButton.setText("Save copy");
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveCopy());
        actions.addView(saveButton);

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(actions, actionParams);
        return root;
    }

    private void openImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_OPEN_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        loadImage(uri);
    }

    private void loadImage(Uri uri) {
        saveButton.setEnabled(false);
        worker.execute(() -> {
            try {
                DecodedImage image = ImageDecoder.decode(getContentResolver(), uri, 3840);
                runOnUiThread(() -> {
                    Bitmap previous = bitmap;
                    bitmap = image.bitmap();
                    editorView.setBitmap(bitmap);
                    saveButton.setEnabled(true);
                    if (previous != null && previous != bitmap) previous.recycle();
                });
            } catch (Exception error) {
                showError("Unable to open image", error);
            }
        });
    }

    private void saveCopy() {
        Bitmap snapshot = bitmap;
        if (snapshot == null) return;
        if (android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_IMAGES);
            return;
        }
        saveButton.setEnabled(false);
        worker.execute(() -> {
            Uri outputUri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "TGImageEditor_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TGImageEditor");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) throw new IllegalStateException("MediaStore insert failed");
                try (OutputStream output = getContentResolver().openOutputStream(outputUri, "w")) {
                    if (output == null) throw new IllegalStateException("Unable to open output");
                    ImageExporter.write(snapshot, output, Bitmap.CompressFormat.JPEG, 95);
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(outputUri, values, null, null);
                }
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    Toast.makeText(this, "Saved to Pictures/TGImageEditor", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                if (outputUri != null) getContentResolver().delete(outputUri, null, null);
                showError("Unable to save image", error);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_IMAGES && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveCopy();
        } else if (requestCode == REQUEST_WRITE_IMAGES) {
            Toast.makeText(this, "Storage permission is required to save a copy", Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message, Exception error) {
        runOnUiThread(() -> {
            saveButton.setEnabled(bitmap != null);
            Toast.makeText(this, message + ": " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
