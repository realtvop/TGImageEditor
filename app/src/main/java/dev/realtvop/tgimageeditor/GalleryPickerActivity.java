package dev.realtvop.tgimageeditor;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AbsListView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.LruCache;

import dev.realtvop.tgimageeditor.nekogram.NekogramActionBar;
import dev.realtvop.tgimageeditor.nekogram.NekogramColors;

/** Full-screen local photo picker modeled after Nekogram's gallery surface. */
public final class GalleryPickerActivity extends Activity {
    private static final int REQUEST_READ_MEDIA = 300;
    private final ArrayList<Photo> photos = new ArrayList<>();
    private LinearLayout root;
    private NekogramActionBar actionBar;
    private FrameLayout content;
    private GridView grid;
    private TextView emptyView;
    private Object backCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(getString(R.string.gallery_local_only));
        setContentView(createContent());
        SystemBars.install(this, root, this::applySystemBarInsets);
        if (Build.VERSION.SDK_INT >= 34) {
            backCallback = Api34Back.register(this);
        } else if (Build.VERSION.SDK_INT >= 33) {
            backCallback = Api33Back.register(this);
        }
        if (hasReadPermission()) {
            loadPhotos();
        } else {
            requestReadPermission();
        }
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        finishAfterTransition();
    }

    private void updateBackPreview(float progress, int swipeEdge) {
        root.animate().cancel();
        float direction = swipeEdge == android.window.BackEvent.EDGE_LEFT ? 1f : -1f;
        root.setTranslationX(direction * dp(16) * progress);
        float scale = 1f - .02f * progress;
        root.setScaleX(scale);
        root.setScaleY(scale);
        root.setAlpha(1f - .08f * progress);
    }

    private void resetBackPreview(boolean animate) {
        if (root == null) return;
        root.animate().cancel();
        if (animate) {
            root.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
        } else {
            root.setTranslationX(0);
            root.setScaleX(1f);
            root.setScaleY(1f);
            root.setAlpha(1f);
        }
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null) {
            if (Build.VERSION.SDK_INT >= 34) {
                Api34Back.unregister(this, backCallback);
            } else if (Build.VERSION.SDK_INT >= 33) {
                Api33Back.unregister(this, backCallback);
            }
            backCallback = null;
        }
        if (grid != null && grid.getAdapter() instanceof PhotoAdapter) {
            ((PhotoAdapter) grid.getAdapter()).shutdown();
        }
        super.onDestroy();
    }

    private View createContent() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NekogramColors.surface(this));

        actionBar = new NekogramActionBar(this);
        actionBar.setTitle(getString(R.string.gallery_local_only));
        actionBar.setBackAction(v -> handleBack());
        actionBar.setAction("", null);
        root.addView(actionBar, new LinearLayout.LayoutParams(-1, dp(56)));

        content = new FrameLayout(this);
        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp(2));
        grid.setVerticalSpacing(dp(2));
        grid.setPadding(dp(2), dp(2), dp(2), dp(2));
        grid.setClipToPadding(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setColumnWidth(dp(120));
        grid.setOnItemClickListener((parent, view, position, id) -> selectPhoto(position));
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new TextView(this);
        emptyView.setText(R.string.gallery_empty);
        emptyView.setTextColor(NekogramColors.textSecondary(this));
        emptyView.setTextSize(20);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void applySystemBarInsets(int left, int top, int right, int bottom) {
        root.setPadding(left, 0, right, 0);
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) actionBar.getLayoutParams();
        barParams.topMargin = top;
        actionBar.setLayoutParams(barParams);
        LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) content.getLayoutParams();
        contentParams.bottomMargin = bottom;
        content.setLayoutParams(contentParams);
    }

    private boolean hasReadPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || (Build.VERSION.SDK_INT >= 34
                    && checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED);
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"}, REQUEST_READ_MEDIA);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_READ_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_MEDIA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_MEDIA) {
            if (hasReadPermission()) {
                loadPhotos();
            } else {
                emptyView.setText(R.string.gallery_permission_required);
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadPhotos() {
        photos.clear();
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED};
        String order = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, order)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    photos.add(new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), id));
                }
            }
        } catch (SecurityException ignored) {
            emptyView.setText(R.string.gallery_permission_required);
        }
        emptyView.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
        grid.setAdapter(new PhotoAdapter(this, photos));
    }

    private void selectPhoto(int position) {
        if (position < 0 || position >= photos.size()) return;
        Intent result = new Intent();
        result.setData(photos.get(position).uri);
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, result);
        finish();
    }

    private static final class Api33Back {
        private Api33Back() {}

        @android.annotation.SuppressLint({"NewApi", "InlinedApi"})
        static Object register(GalleryPickerActivity activity) {
            android.window.OnBackInvokedCallback callback = activity::handleBack;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        @android.annotation.SuppressLint("NewApi")
        static void unregister(GalleryPickerActivity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) callback);
        }
    }

    private static final class Api34Back {
        private Api34Back() {}

        @android.annotation.SuppressLint({"NewApi", "InlinedApi"})
        static Object register(GalleryPickerActivity activity) {
            android.window.OnBackAnimationCallback callback = new android.window.OnBackAnimationCallback() {
                @Override public void onBackStarted(android.window.BackEvent event) {
                    activity.resetBackPreview(false);
                }

                @Override public void onBackProgressed(android.window.BackEvent event) {
                    activity.updateBackPreview(event.getProgress(), event.getSwipeEdge());
                }

                @Override public void onBackCancelled() {
                    activity.resetBackPreview(true);
                }

                @Override public void onBackInvoked() {
                    activity.resetBackPreview(false);
                    activity.handleBack();
                }
            };
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        @android.annotation.SuppressLint("NewApi")
        static void unregister(GalleryPickerActivity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) callback);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Photo {
        final Uri uri;
        final long id;

        Photo(Uri uri, long id) {
            this.uri = uri;
            this.id = id;
        }
    }

    private static final class PhotoAdapter extends BaseAdapter {
        private final Context context;
        private final List<Photo> items;
        private final int tileSize;
        private final ExecutorService loader = Executors.newFixedThreadPool(2);
        private final Handler main = new Handler(Looper.getMainLooper());
        private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };

        PhotoAdapter(Context context, List<Photo> items) {
            this.context = context;
            this.items = items;
            int width = context.getResources().getDisplayMetrics().widthPixels;
            tileSize = Math.max(1, (width - dp(context, 8)) / 3);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PhotoCell cell = convertView instanceof PhotoCell ? (PhotoCell) convertView : new PhotoCell(context, tileSize);
            ImageView image = cell.image;
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new ColorDrawable(NekogramColors.surfaceContainer(context)));
            Photo photo = items.get(position);
            String key = photo.uri.toString();
            image.setTag(key);
            Bitmap cached = cache.get(key);
            image.setImageBitmap(cached);
            if (cached == null) {
                loader.execute(() -> {
                    Bitmap loaded = loadThumbnail(photo);
                    if (loaded != null) cache.put(key, loaded);
                    main.post(() -> {
                        if (key.equals(image.getTag())) image.setImageBitmap(loaded);
                    });
                });
            }
            return cell;
        }

        void shutdown() {
            loader.shutdownNow();
        }

        private Bitmap loadThumbnail(Photo photo) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    return context.getContentResolver().loadThumbnail(photo.uri, new Size(tileSize, tileSize), null);
                }
                return MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), photo.id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }

    private static final class PhotoCell extends FrameLayout {
        final ImageView image;

        PhotoCell(Context context, int size) {
            super(context);
            setLayoutParams(new AbsListView.LayoutParams(-1, size));
            image = new ImageView(context);
            addView(image, new FrameLayout.LayoutParams(-1, -1));
            View check = new View(context) {
                private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                {
                    fill.setColor(0x66000000);
                    stroke.setColor(Color.WHITE);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(px(context, 2));
                }
                @Override protected void onDraw(Canvas canvas) {
                    float r = Math.min(getWidth(), getHeight()) / 2f - px(context, 2);
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, fill);
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, stroke);
                }
            };
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(px(context, 30), px(context, 30), Gravity.TOP | Gravity.RIGHT);
            checkParams.topMargin = px(context, 4);
            checkParams.rightMargin = px(context, 4);
            addView(check, checkParams);
        }

        private static int px(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
