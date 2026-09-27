package dev.realtvop.tgimageeditor.engine;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.IOException;

import dev.realtvop.tgimageeditor.model.EditDocument;
import dev.realtvop.tgimageeditor.model.SourceImage;

/** Decodes a bounded preview and normalizes all eight EXIF orientation variants. */
public final class ImageDecoder {
    private ImageDecoder() {}

    public static DecodedImage decode(ContentResolver resolver, Uri uri, int maxDimension) throws IOException {
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("maxDimension must be positive");
        }

        BitmapFactory.Options bounds = readBounds(resolver, uri);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or corrupt image");
        }
        int orientation = readOrientation(resolver, uri);
        Bitmap normalized = decodeBitmap(resolver, uri, maxDimension, bounds, orientation);
        boolean swapsSides = orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_270;
        SourceImage source = new SourceImage(uri.toString(), swapsSides ? bounds.outHeight : bounds.outWidth,
                swapsSides ? bounds.outWidth : bounds.outHeight);
        return new DecodedImage(normalized, EditDocument.create(source));
    }

    public static Bitmap decodeBitmap(ContentResolver resolver, Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = readBounds(resolver, uri);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported or corrupt image");
        return decodeBitmap(resolver, uri, maxDimension, bounds, readOrientation(resolver, uri));
    }

    private static Bitmap decodeBitmap(ContentResolver resolver, Uri uri, int maxDimension,
                                       BitmapFactory.Options bounds, int orientation) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        Bitmap decoded = decodeFileDescriptor(resolver, uri, options);
        if (decoded == null) throw new IOException("Unable to decode image");
        Bitmap normalized = normalizeOrientation(decoded, orientation);
        if (normalized != decoded) decoded.recycle();
        return normalized;
    }

    private static BitmapFactory.Options readBounds(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        decodeFileDescriptor(resolver, uri, bounds);
        return bounds;
    }

    private static Bitmap decodeFileDescriptor(ContentResolver resolver, Uri uri,
                                               BitmapFactory.Options options) throws IOException {
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                throw new IOException("Unable to open image");
            }
            return BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, options);
        }
    }

    private static int readOrientation(ContentResolver resolver, Uri uri) {
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) return ExifInterface.ORIENTATION_NORMAL;
            FileDescriptor fd = descriptor.getFileDescriptor();
            ExifInterface exif = new ExifInterface(fd);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    static int sampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        while (Math.max(width / sample, height / sample) > maxDimension) {
            sample *= 2;
        }
        return sample;
    }

    static Bitmap normalizeOrientation(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return source;
        }
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
