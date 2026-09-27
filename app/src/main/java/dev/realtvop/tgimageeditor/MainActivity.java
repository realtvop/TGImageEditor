package dev.realtvop.tgimageeditor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.EditText;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

import dev.realtvop.tgimageeditor.engine.DecodedImage;
import dev.realtvop.tgimageeditor.engine.ImageDecoder;
import dev.realtvop.tgimageeditor.engine.ImageExporter;
import dev.realtvop.tgimageeditor.engine.CropRenderer;
import dev.realtvop.tgimageeditor.engine.FilterRenderer;
import dev.realtvop.tgimageeditor.engine.ImagePipeline;
import dev.realtvop.tgimageeditor.engine.PaintRenderer;
import dev.realtvop.tgimageeditor.model.CropState;
import dev.realtvop.tgimageeditor.model.EditDocument;
import dev.realtvop.tgimageeditor.model.FilterState;
import dev.realtvop.tgimageeditor.model.PaintStroke;
import dev.realtvop.tgimageeditor.model.TextEntity;
import dev.realtvop.tgimageeditor.ui.EditorView;
import dev.realtvop.tgimageeditor.ui.FilterControls;
import dev.realtvop.tgimageeditor.ui.PaintControls;
import dev.realtvop.tgimageeditor.ui.CropControls;

public final class MainActivity extends Activity {
    private static final String STATE_DOCUMENT = "editor_document";
    private enum Tool { NONE, CROP, FILTER, PAINT }
    private static final int REQUEST_OPEN_IMAGE = 100;
    private static final int REQUEST_WRITE_IMAGES = 101;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditorView editorView;
    private LinearLayout actions;
    private Button openButton;
    private Button saveButton;
    private Button cropButton;
    private Button filterButton;
    private Button paintButton;
    private Button documentUndoButton;
    private Button documentRedoButton;
    private Button rotateButton;
    private Button mirrorButton;
    private Button cancelButton;
    private Button doneButton;
    private Button undoButton;
    private Bitmap bitmap;
    private Bitmap renderedBitmap;
    private Bitmap cropSurfaceBitmap;
    private Bitmap filterBaseBitmap;
    private Bitmap filterPreviewBitmap;
    private EditDocument document;
    private CropState pendingCrop;
    private FilterState pendingFilter;
    private List<PaintStroke> pendingPaint;
    private List<TextEntity> pendingText;
    private Bitmap paintBaseBitmap;
    private FilterControls filterControls;
    private PaintControls paintControls;
    private CropControls cropControls;
    private Tool activeTool = Tool.NONE;
    private int filterGeneration;
    private final Runnable renderFilter = this::enqueueFilterPreview;
    private final ArrayDeque<EditDocument> undoHistory = new ArrayDeque<>();
    private final ArrayDeque<EditDocument> redoHistory = new ArrayDeque<>();
    private boolean historyRendering;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(createContent());
        if (state != null) {
            @SuppressWarnings("deprecation")
            EditDocument restored = (EditDocument) state.getSerializable(STATE_DOCUMENT);
            if (restored != null) loadImage(Uri.parse(restored.source().id()), restored);
        }
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);
        editorView = new EditorView(this);
        root.addView(editorView, new FrameLayout.LayoutParams(-1, -1));

        filterControls = new FilterControls(this);
        filterControls.setVisibility(View.GONE);
        FrameLayout.LayoutParams filterParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        filterParams.bottomMargin = dp(64);
        root.addView(filterControls, filterParams);

        paintControls = new PaintControls(this);
        paintControls.setVisibility(View.GONE);
        paintControls.setListener(brush -> editorView.setPaintBrush(brush));
        paintControls.setTextRequestListener(this::requestText);
        FrameLayout.LayoutParams paintParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        paintParams.bottomMargin = dp(64);
        root.addView(paintControls, paintParams);

        cropControls = new CropControls(this);
        cropControls.setVisibility(View.GONE);
        FrameLayout.LayoutParams cropParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        cropParams.bottomMargin = dp(64);
        root.addView(cropControls, cropParams);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(12), dp(8), dp(12), dp(8));

        openButton = new Button(this);
        openButton.setText(R.string.action_open);
        openButton.setOnClickListener(v -> openImage());
        actions.addView(openButton);

        cropButton = actionButton(R.string.action_crop, v -> beginCrop());
        cropButton.setEnabled(false);
        actions.addView(cropButton);

        filterButton = actionButton(R.string.action_adjust, v -> beginFilter());
        filterButton.setEnabled(false);
        actions.addView(filterButton);

        paintButton = actionButton(R.string.action_draw, v -> beginPaint());
        paintButton.setEnabled(false);
        actions.addView(paintButton);

        documentUndoButton = actionButton(R.string.action_undo, v -> navigateHistory(true));
        documentUndoButton.setEnabled(false);
        actions.addView(documentUndoButton);

        documentRedoButton = actionButton(R.string.action_redo, v -> navigateHistory(false));
        documentRedoButton.setEnabled(false);
        actions.addView(documentRedoButton);

        saveButton = new Button(this);
        saveButton.setText(R.string.action_save_copy);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveCopy());
        actions.addView(saveButton);

        rotateButton = actionButton(R.string.action_rotate, v -> updateCrop(pendingCrop.rotateClockwise()));
        mirrorButton = actionButton(R.string.action_mirror, v -> updateCrop(pendingCrop.toggleMirror()));
        cancelButton = actionButton(R.string.action_cancel, v -> finishTool(false));
        doneButton = actionButton(R.string.action_done, v -> finishTool(true));
        undoButton = actionButton(R.string.action_undo, v -> editorView.undoPaint());
        actions.addView(rotateButton);
        actions.addView(mirrorButton);
        actions.addView(cancelButton);
        actions.addView(doneButton);
        actions.addView(undoButton);
        setToolActionsVisible(false);

        HorizontalScrollView actionScroller = new HorizontalScrollView(this);
        actionScroller.setHorizontalScrollBarEnabled(false);
        actionScroller.addView(actions, new HorizontalScrollView.LayoutParams(-2, -2));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(actionScroller, actionParams);
        return root;
    }

    private Button actionButton(int label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
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
        loadImage(uri, null);
    }

    private void loadImage(Uri uri, EditDocument restoredDocument) {
        historyRendering = true;
        updateHistoryButtons();
        openButton.setEnabled(false);
        cropButton.setEnabled(false);
        filterButton.setEnabled(false);
        paintButton.setEnabled(false);
        saveButton.setEnabled(false);
        worker.execute(() -> {
            try {
                DecodedImage image = ImageDecoder.decode(getContentResolver(), uri, 3840);
                Bitmap restoredBitmap = restoredDocument == null
                        ? image.bitmap() : ImagePipeline.render(image.bitmap(), restoredDocument);
                runOnUiThread(() -> {
                    Bitmap previous = bitmap;
                    Bitmap previousRendered = renderedBitmap;
                    bitmap = image.bitmap();
                    document = restoredDocument == null ? image.document() : restoredDocument;
                    undoHistory.clear();
                    redoHistory.clear();
                    historyRendering = false;
                    renderedBitmap = restoredBitmap;
                    editorView.setBitmap(renderedBitmap);
                    openButton.setEnabled(true);
                    saveButton.setEnabled(true);
                    cropButton.setEnabled(true);
                    filterButton.setEnabled(true);
                    paintButton.setEnabled(true);
                    updateHistoryButtons();
                    if (previous != null && previous != bitmap) previous.recycle();
                    if (previousRendered != null && previousRendered != previous && previousRendered != bitmap) {
                        previousRendered.recycle();
                    }
                });
            } catch (Exception error) {
                historyRendering = false;
                showError(getString(R.string.error_open_image), error);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (document != null) outState.putSerializable(STATE_DOCUMENT, document);
    }

    private void saveCopy() {
        if (renderedBitmap == null || document == null) return;
        if (android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_IMAGES);
            return;
        }
        saveButton.setEnabled(false);
        openButton.setEnabled(false);
        cropButton.setEnabled(false);
        filterButton.setEnabled(false);
        paintButton.setEnabled(false);
        EditDocument snapshot = document;
        worker.execute(() -> {
            Uri outputUri = null;
            Bitmap source = null;
            Bitmap result = null;
            try {
                source = ImageDecoder.decodeBitmap(getContentResolver(), Uri.parse(snapshot.source().id()), 8192);
                result = ImagePipeline.render(source, snapshot);
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
                    ImageExporter.write(result, output, Bitmap.CompressFormat.JPEG, 95);
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(outputUri, values, null, null);
                }
                runOnUiThread(() -> {
                    openButton.setEnabled(true);
                    cropButton.setEnabled(true);
                    filterButton.setEnabled(true);
                    paintButton.setEnabled(true);
                    saveButton.setEnabled(true);
                    Toast.makeText(this, R.string.saved_message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                if (outputUri != null) getContentResolver().delete(outputUri, null, null);
                showError(getString(R.string.error_save_image), error);
            } finally {
                if (result != null && result != source) result.recycle();
                if (source != null) source.recycle();
            }
        });
    }

    private void beginCrop() {
        if (bitmap == null || document == null) return;
        pendingCrop = document.crop();
        activeTool = Tool.CROP;
        showCropSurface();
        editorView.beginCrop(pendingCrop, this::onCropBoundsChanged);
        float originalRatio = (float) cropSurfaceBitmap.getWidth() / cropSurfaceBitmap.getHeight();
        cropControls.bind(pendingCrop, originalRatio, this::updateCrop, editorView::setCropAspectRatio);
        cropControls.setVisibility(View.VISIBLE);
        setToolActionsVisible(true);
    }

    private void updateCrop(CropState crop) {
        pendingCrop = crop;
        cropControls.setState(crop);
        showCropSurface();
        editorView.updateCrop(pendingCrop);
    }

    private void onCropBoundsChanged(CropState crop) {
        pendingCrop = crop;
        cropControls.setState(crop);
    }

    private void showCropSurface() {
        Bitmap next = CropRenderer.transformSource(bitmap, pendingCrop);
        if (cropSurfaceBitmap != null && cropSurfaceBitmap != bitmap && cropSurfaceBitmap != next) {
            cropSurfaceBitmap.recycle();
        }
        cropSurfaceBitmap = next;
        editorView.setBitmap(cropSurfaceBitmap);
    }

    private void finishCrop(boolean apply) {
        editorView.endCrop();
        cropControls.setVisibility(View.GONE);
        if (apply) {
            EditDocument nextDocument = document.withCrop(pendingCrop);
            Bitmap next = ImagePipeline.render(bitmap, nextDocument);
            acceptDocument(nextDocument, next);
        }
        if (cropSurfaceBitmap != null && cropSurfaceBitmap != bitmap && cropSurfaceBitmap != renderedBitmap) {
            cropSurfaceBitmap.recycle();
        }
        cropSurfaceBitmap = null;
        editorView.setBitmap(renderedBitmap);
    }

    private void beginFilter() {
        if (bitmap == null || document == null) return;
        activeTool = Tool.FILTER;
        pendingFilter = document.filter();
        filterBaseBitmap = CropRenderer.render(bitmap, document.crop());
        filterPreviewBitmap = renderedBitmap;
        filterControls.bind(pendingFilter, this::scheduleFilterPreview);
        filterControls.setVisibility(View.VISIBLE);
        setToolActionsVisible(true);
    }

    private void scheduleFilterPreview(FilterState filter) {
        pendingFilter = filter;
        filterPreviewBitmap = null;
        doneButton.setEnabled(false);
        filterGeneration++;
        mainHandler.removeCallbacks(renderFilter);
        mainHandler.postDelayed(renderFilter, 50);
    }

    private void enqueueFilterPreview() {
        final int generation = filterGeneration;
        final FilterState filter = pendingFilter;
        final Bitmap base = filterBaseBitmap;
        worker.execute(() -> {
            Bitmap filtered = FilterRenderer.render(base, filter);
            Bitmap result = PaintRenderer.render(filtered, document.paintStrokes(), document.textEntities());
            if (filtered != base && filtered != result) filtered.recycle();
            runOnUiThread(() -> {
                if (generation != filterGeneration || activeTool != Tool.FILTER) {
                    if (result != base) result.recycle();
                    return;
                }
                Bitmap previous = filterPreviewBitmap;
                filterPreviewBitmap = result;
                editorView.setBitmap(result);
                doneButton.setEnabled(true);
                if (previous != null && previous != renderedBitmap && previous != base && previous != result) {
                    previous.recycle();
                }
            });
        });
    }

    private void finishFilter(boolean apply) {
        mainHandler.removeCallbacks(renderFilter);
        filterGeneration++;
        filterControls.setVisibility(View.GONE);
        if (apply && filterPreviewBitmap != null) {
            acceptDocument(document.withFilter(pendingFilter), filterPreviewBitmap);
        } else {
            editorView.setBitmap(renderedBitmap);
        }
        // A cancelled background preview may still be reading the base bitmap.
        // Release the strong reference and let the runtime reclaim it safely.
        if (!apply && filterPreviewBitmap != null && filterPreviewBitmap != renderedBitmap
                && filterPreviewBitmap != filterBaseBitmap) {
            filterPreviewBitmap.recycle();
        }
        filterBaseBitmap = null;
        filterPreviewBitmap = null;
    }

    private void finishTool(boolean apply) {
        if (activeTool == Tool.CROP) finishCrop(apply);
        if (activeTool == Tool.FILTER) finishFilter(apply);
        if (activeTool == Tool.PAINT) finishPaint(apply);
        activeTool = Tool.NONE;
        setToolActionsVisible(false);
    }

    private void setToolActionsVisible(boolean editing) {
        int normalVisibility = editing ? View.GONE : View.VISIBLE;
        for (int index = 0; index < 7; index++) actions.getChildAt(index).setVisibility(normalVisibility);
        boolean crop = editing && activeTool == Tool.CROP;
        rotateButton.setVisibility(crop ? View.VISIBLE : View.GONE);
        mirrorButton.setVisibility(crop ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        doneButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        undoButton.setVisibility(editing && activeTool == Tool.PAINT ? View.VISIBLE : View.GONE);
        doneButton.setEnabled(true);
    }

    private void beginPaint() {
        if (bitmap == null || document == null) return;
        activeTool = Tool.PAINT;
        pendingPaint = new ArrayList<>(document.paintStrokes());
        pendingText = new ArrayList<>(document.textEntities());
        paintBaseBitmap = ImagePipeline.renderBase(bitmap, document);
        editorView.setBitmap(paintBaseBitmap);
        editorView.beginPaint(pendingPaint, pendingText, (strokes, entities) -> {
            pendingPaint = strokes;
            pendingText = entities;
        });
        paintControls.setVisibility(View.VISIBLE);
        setToolActionsVisible(true);
    }

    private void finishPaint(boolean apply) {
        editorView.endPaint();
        paintControls.setVisibility(View.GONE);
        if (apply) {
            EditDocument nextDocument = document.withDrawing(pendingPaint, pendingText);
            Bitmap next = ImagePipeline.render(bitmap, nextDocument);
            acceptDocument(nextDocument, next);
        }
        editorView.setBitmap(renderedBitmap);
        paintBaseBitmap = null;
        pendingPaint = null;
        pendingText = null;
    }

    private void requestText() {
        EditText input = new EditText(this);
        input.setHint(R.string.add_text_hint);
        input.setSingleLine(false);
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_text_title)
                .setView(input)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) editorView.addPaintText(text);
                })
                .show();
    }

    private void acceptDocument(EditDocument nextDocument, Bitmap nextBitmap) {
        if (document != null) {
            undoHistory.push(document);
            while (undoHistory.size() > 30) undoHistory.removeLast();
        }
        redoHistory.clear();
        replaceDocument(nextDocument, nextBitmap);
    }

    private void replaceDocument(EditDocument nextDocument, Bitmap nextBitmap) {
        Bitmap previous = renderedBitmap;
        document = nextDocument;
        renderedBitmap = nextBitmap;
        editorView.setBitmap(nextBitmap);
        if (previous != null && previous != bitmap && previous != nextBitmap
                && previous != cropSurfaceBitmap && previous != filterBaseBitmap
                && previous != paintBaseBitmap) {
            previous.recycle();
        }
        updateHistoryButtons();
    }

    private void navigateHistory(boolean undo) {
        ArrayDeque<EditDocument> source = undo ? undoHistory : redoHistory;
        if (historyRendering || document == null || source.isEmpty()) return;
        EditDocument current = document;
        EditDocument target = source.peek();
        historyRendering = true;
        setNormalActionsEnabled(false);
        worker.execute(() -> {
            try {
                Bitmap next = ImagePipeline.render(bitmap, target);
                runOnUiThread(() -> {
                    source.pop();
                    ArrayDeque<EditDocument> destination = undo ? redoHistory : undoHistory;
                    destination.push(current);
                    replaceDocument(target, next);
                    historyRendering = false;
                    setNormalActionsEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    historyRendering = false;
                    setNormalActionsEnabled(true);
                    showError(getString(R.string.error_render_image), error);
                });
            }
        });
    }

    private void setNormalActionsEnabled(boolean enabled) {
        openButton.setEnabled(enabled);
        cropButton.setEnabled(enabled && bitmap != null);
        filterButton.setEnabled(enabled && bitmap != null);
        paintButton.setEnabled(enabled && bitmap != null);
        saveButton.setEnabled(enabled && bitmap != null);
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (documentUndoButton == null) return;
        documentUndoButton.setEnabled(!historyRendering && !undoHistory.isEmpty());
        documentRedoButton.setEnabled(!historyRendering && !redoHistory.isEmpty());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_IMAGES && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveCopy();
        } else if (requestCode == REQUEST_WRITE_IMAGES) {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message, Exception error) {
        runOnUiThread(() -> {
            openButton.setEnabled(true);
            cropButton.setEnabled(bitmap != null);
            filterButton.setEnabled(bitmap != null);
            paintButton.setEnabled(bitmap != null);
            saveButton.setEnabled(bitmap != null);
            Toast.makeText(this, getString(R.string.error_with_reason, message, error.getMessage()), Toast.LENGTH_LONG).show();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }
}
