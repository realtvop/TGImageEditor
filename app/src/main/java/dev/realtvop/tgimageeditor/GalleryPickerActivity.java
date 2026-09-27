package dev.realtvop.tgimageeditor;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.view.Window;
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
    private GridView grid;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(NekogramColors.surface(this));
        window.setNavigationBarColor(NekogramColors.surface(this));
        setTitle(getString(R.string.gallery_title));
        setContentView(createContent());
        if (hasReadPermission()) {
            loadPhotos();
        } else {
            requestReadPermission();
        }
    }

    @Override
    protected void onDestroy() {
        if (grid != null && grid.getAdapter() instanceof PhotoAdapter) {
            ((PhotoAdapter) grid.getAdapter()).shutdown();
        }
        super.onDestroy();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NekogramColors.surface(this));

        NekogramActionBar bar = new NekogramActionBar(this);
        bar.setTitle(getString(R.string.gallery_title));
        bar.setBackAction(v -> finish());
        bar.setAction("", null);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        FrameLayout content = new FrameLayout(this);
        grid = new GridView(this);
        grid.setNumColumns(4);
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
            tileSize = Math.max(1, (width - dp(context, 10)) / 4);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image = convertView instanceof ImageView ? (ImageView) convertView : new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new ColorDrawable(NekogramColors.surfaceContainer(context)));
            image.setLayoutParams(new AbsListView.LayoutParams(-1, tileSize));
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
            return image;
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
}
