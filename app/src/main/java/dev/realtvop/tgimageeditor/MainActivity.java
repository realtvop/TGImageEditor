package dev.realtvop.tgimageeditor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.TextView;

import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import dev.realtvop.tgimageeditor.nekogram.NekogramActionBar;
import dev.realtvop.tgimageeditor.nekogram.NekogramEditorIcons;

public final class MainActivity extends Activity {
    private static final String STATE_DOCUMENT_PATH = "editor_document_path";
    private static final String STATE_FILE_NAME = "restorable-editor-document.bin";
    private enum Tool { NONE, CROP, FILTER, PAINT }
    private static final int REQUEST_OPEN_IMAGE = 100;
    private static final int REQUEST_WRITE_IMAGES = 101;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditorView editorView;
    private LinearLayout actions;
    private NekogramActionBar topBar;
    private View shareButton;
    private View cropButton;
    private View filterButton;
    private View paintButton;
    private View documentUndoButton;
    private View documentRedoButton;
    private View paintUndoButton;
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
    private boolean shareAfterPermission;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(createContent());
        if (android.os.Build.VERSION.SDK_INT >= 33) Api33Back.register(this);
        if (state != null) {
            EditDocument restored = restoreDocument(state.getString(STATE_DOCUMENT_PATH));
            if (restored != null) {
                loadImage(Uri.parse(restored.source().id()), restored);
            } else {
                openImage();
            }
        } else {
            openImage();
        }
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        editorView = new EditorView(this);
        root.addView(editorView, new FrameLayout.LayoutParams(-1, -1));

        filterControls = new FilterControls(this);
        filterControls.setVisibility(View.GONE);
        filterControls.setActions(() -> finishTool(false), () -> finishTool(true));
        FrameLayout.LayoutParams filterParams = new FrameLayout.LayoutParams(-1, dp(186), Gravity.BOTTOM);
        root.addView(filterControls, filterParams);

        paintControls = new PaintControls(this);
        paintControls.setVisibility(View.GONE);
        paintControls.setListener(brush -> editorView.setPaintBrush(brush));
        paintControls.setTextRequestListener(this::requestText);
        paintControls.setActions(() -> finishTool(false), () -> finishTool(true));
        FrameLayout.LayoutParams paintParams = new FrameLayout.LayoutParams(-1, dp(104), Gravity.BOTTOM);
        root.addView(paintControls, paintParams);

        cropControls = new CropControls(this);
        cropControls.setVisibility(View.GONE);
        cropControls.setActions(() -> finishTool(false), () -> finishTool(true));
        FrameLayout.LayoutParams cropParams = new FrameLayout.LayoutParams(-1, dp(112), Gravity.BOTTOM);
        root.addView(cropControls, cropParams);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(2), 0, dp(2), 0);
        GradientDrawable toolBackground = new GradientDrawable();
        toolBackground.setColor(0xcc1a1a1a);
        toolBackground.setCornerRadius(dp(22));
        actions.setBackground(toolBackground);

        cropButton = iconAction(NekogramEditorIcons.Icon.CROP, v -> beginCrop());
        cropButton.setEnabled(false);
        actions.addView(cropButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        paintButton = iconAction(NekogramEditorIcons.Icon.DRAW, v -> beginPaint());
        paintButton.setEnabled(false);
        actions.addView(paintButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        filterButton = iconAction(NekogramEditorIcons.Icon.ADJUST, v -> beginFilter());
        filterButton.setEnabled(false);
        actions.addView(filterButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        documentUndoButton = symbolAction("↶", v -> navigateHistory(true));
        documentUndoButton.setEnabled(false);
        actions.addView(documentUndoButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        documentRedoButton = symbolAction("↷", v -> navigateHistory(false));
        documentRedoButton.setEnabled(false);
        actions.addView(documentRedoButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        shareButton = symbolAction("↗", v -> shareCopy());
        shareButton.setEnabled(false);
        actions.addView(shareButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        topBar = new NekogramActionBar(this);
        topBar.setPhotoViewerMode();
        topBar.setTitle(getString(R.string.app_name));
        topBar.setBackAction(v -> openImage());
        topBar.setAction(getString(R.string.action_save_copy), v -> saveCopy());
        topBar.setActionEnabled(false);
        root.addView(topBar, new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP));

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionParams.bottomMargin = dp(8);
        root.addView(actions, actionParams);

        paintUndoButton = symbolAction("↶", v -> editorView.undoPaint());
        paintUndoButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams undoParams = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.LEFT);
        undoParams.leftMargin = dp(8);
        undoParams.topMargin = dp(8);
        root.addView(paintUndoButton, undoParams);
        return root;
    }

    private ImageView iconAction(NekogramEditorIcons.Icon icon, View.OnClickListener listener) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageDrawable(NekogramEditorIcons.drawable(this, icon));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView symbolAction(String symbol, View.OnClickListener listener) {
        TextView view = new TextView(this);
        view.setText(symbol);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(listener);
        return view;
    }

    private void openImage() {
        startActivityForResult(new Intent(this, GalleryPickerActivity.class), REQUEST_OPEN_IMAGE);
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
        topBar.setBackEnabled(false);
        cropButton.setEnabled(false);
        filterButton.setEnabled(false);
        paintButton.setEnabled(false);
        topBar.setActionEnabled(false);
        shareButton.setEnabled(false);
        worker.execute(() -> {
            try {
                DecodedImage image = ImageDecoder.decode(getContentResolver(), uri, 3840);
                Bitmap restoredBitmap = restoredDocument == null
                        ? image.bitmap() : ImagePipeline.render(image.bitmap(), restoredDocument);
                runOnUiThread(() -> {
                    if (destroyed) {
                        if (restoredBitmap != image.bitmap()) restoredBitmap.recycle();
                        image.bitmap().recycle();
                        return;
                    }
                    Bitmap previous = bitmap;
                    Bitmap previousRendered = renderedBitmap;
                    bitmap = image.bitmap();
                    document = restoredDocument == null ? image.document() : restoredDocument;
                    undoHistory.clear();
                    redoHistory.clear();
                    historyRendering = false;
                    renderedBitmap = restoredBitmap;
                    editorView.setBitmap(renderedBitmap);
                    topBar.setBackEnabled(true);
                    topBar.setActionEnabled(true);
                    cropButton.setEnabled(true);
                    filterButton.setEnabled(true);
                    paintButton.setEnabled(true);
                    shareButton.setEnabled(true);
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
        if (document == null) return;
        File stateFile = new File(getCacheDir(), STATE_FILE_NAME);
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(stateFile))) {
            output.writeObject(document);
            outState.putString(STATE_DOCUMENT_PATH, stateFile.getAbsolutePath());
        } catch (Exception ignored) {
            // The source remains available through the persisted picker permission.
        }
    }

    private EditDocument restoreDocument(String path) {
        if (path == null) return null;
        File stateFile = new File(path);
        try {
            if (!stateFile.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath() + File.separator)) {
                return null;
            }
            try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(stateFile))) {
                return (EditDocument) input.readObject();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveCopy() {
        exportCopy(false);
    }

    private void shareCopy() {
        exportCopy(true);
    }

    private void exportCopy(boolean share) {
        if (renderedBitmap == null || document == null) return;
        if (android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            shareAfterPermission = share;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_IMAGES);
            return;
        }
        topBar.setActionEnabled(false);
        topBar.setBackEnabled(false);
        cropButton.setEnabled(false);
        filterButton.setEnabled(false);
        paintButton.setEnabled(false);
        shareButton.setEnabled(false);
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
                Uri completedUri = outputUri;
                runOnUiThread(() -> {
                    if (destroyed) return;
                    topBar.setBackEnabled(true);
                    cropButton.setEnabled(true);
                    filterButton.setEnabled(true);
                    paintButton.setEnabled(true);
                    topBar.setActionEnabled(true);
                    shareButton.setEnabled(true);
                    Toast.makeText(this, R.string.saved_message, Toast.LENGTH_SHORT).show();
                    if (share) launchShare(completedUri);
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

    private void launchShare(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/jpeg");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(getString(R.string.app_name), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser_title)));
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
        editorView.beginBlur(pendingFilter.blur(), blur -> scheduleFilterPreview(pendingFilter.withBlur(blur)));
        filterControls.setVisibility(View.VISIBLE);
        setToolActionsVisible(true);
    }

    private void scheduleFilterPreview(FilterState filter) {
        pendingFilter = filter;
        filterControls.setState(filter);
        editorView.updateBlur(filter.blur());
        filterPreviewBitmap = null;
        filterControls.setDoneEnabled(false);
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
                if (destroyed || generation != filterGeneration || activeTool != Tool.FILTER) {
                    if (result != base) result.recycle();
                    return;
                }
                Bitmap previous = filterPreviewBitmap;
                filterPreviewBitmap = result;
                editorView.setBitmap(result);
                filterControls.setDoneEnabled(true);
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
        editorView.endBlur();
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
        Bitmap baseToRelease = filterBaseBitmap;
        filterBaseBitmap = null;
        filterPreviewBitmap = null;
        worker.execute(() -> mainHandler.post(() -> recycleIfTemporary(baseToRelease)));
    }

    private void finishTool(boolean apply) {
        if (activeTool == Tool.CROP) finishCrop(apply);
        if (activeTool == Tool.FILTER) finishFilter(apply);
        if (activeTool == Tool.PAINT) finishPaint(apply);
        activeTool = Tool.NONE;
        setToolActionsVisible(false);
    }

    private void setToolActionsVisible(boolean editing) {
        actions.setVisibility(editing ? View.GONE : View.VISIBLE);
        topBar.setVisibility(editing ? View.GONE : View.VISIBLE);
        paintUndoButton.setVisibility(editing && activeTool == Tool.PAINT ? View.VISIBLE : View.GONE);
        filterControls.setDoneEnabled(true);
    }

    private void beginPaint() {
        if (bitmap == null || document == null) return;
        activeTool = Tool.PAINT;
        pendingPaint = new ArrayList<>(document.paintStrokes());
        pendingText = new ArrayList<>(document.textEntities());
        paintBaseBitmap = ImagePipeline.renderBase(bitmap, document);
        editorView.setBitmap(paintBaseBitmap);
        editorView.beginPaint(paintBaseBitmap, pendingPaint, pendingText, (strokes, entities) -> {
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
        recycleIfTemporary(paintBaseBitmap);
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
                    if (destroyed) {
                        if (next != bitmap) next.recycle();
                        return;
                    }
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
        topBar.setBackEnabled(enabled);
        cropButton.setEnabled(enabled && bitmap != null);
        filterButton.setEnabled(enabled && bitmap != null);
        paintButton.setEnabled(enabled && bitmap != null);
        topBar.setActionEnabled(enabled && bitmap != null);
        shareButton.setEnabled(enabled && bitmap != null);
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (documentUndoButton == null) return;
        documentUndoButton.setEnabled(!historyRendering && !undoHistory.isEmpty());
        documentRedoButton.setEnabled(!historyRendering && !redoHistory.isEmpty());
    }

    private void recycleIfTemporary(Bitmap candidate) {
        if (candidate != null && candidate != bitmap && candidate != renderedBitmap && !candidate.isRecycled()) {
            candidate.recycle();
        }
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (activeTool != Tool.NONE) {
            finishTool(false);
        } else {
            finishAfterTransition();
        }
    }

    private static final class Api33Back {
        private Api33Back() {}

        @android.annotation.SuppressLint({"NewApi", "InlinedApi"})
        static void register(MainActivity activity) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, activity::handleBack);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_IMAGES && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            boolean share = shareAfterPermission;
            shareAfterPermission = false;
            exportCopy(share);
        } else if (requestCode == REQUEST_WRITE_IMAGES) {
            shareAfterPermission = false;
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message, Exception error) {
        runOnUiThread(() -> {
            if (destroyed) return;
            topBar.setBackEnabled(true);
            cropButton.setEnabled(bitmap != null);
            filterButton.setEnabled(bitmap != null);
            paintButton.setEnabled(bitmap != null);
            topBar.setActionEnabled(bitmap != null);
            shareButton.setEnabled(bitmap != null);
            Toast.makeText(this, getString(R.string.error_with_reason, message, error.getMessage()), Toast.LENGTH_LONG).show();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }
}
