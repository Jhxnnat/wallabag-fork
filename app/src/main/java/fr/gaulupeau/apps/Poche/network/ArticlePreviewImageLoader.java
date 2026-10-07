package fr.gaulupeau.apps.Poche.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.gaulupeau.apps.Poche.App;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Small asynchronous loader for article preview pictures, used by the article list.
 * <p>
 * Loads from the local image cache when available, otherwise from the network,
 * downsampling to roughly the requested size. Results are kept in a memory cache
 * and delivered on the main thread.
 */
public class ArticlePreviewImageLoader {

    private static final String TAG = ArticlePreviewImageLoader.class.getSimpleName();

    public interface Callback {
        /** Called on the main thread; {@code bitmap} is null if the image could not be loaded. */
        void onImageLoaded(Bitmap bitmap);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int CACHE_SIZE_KB = 16 * 1024; // 16 MB

    private static final LruCache<String, Bitmap> memoryCache =
            new LruCache<String, Bitmap>(CACHE_SIZE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private static OkHttpClient client;

    private ArticlePreviewImageLoader() {}

    public static void load(String imageUrl, int articleId, int targetSizePx, Callback callback) {
        if (TextUtils.isEmpty(imageUrl)) {
            callback.onImageLoaded(null);
            return;
        }

        String key = imageUrl + "@" + targetSizePx;

        Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            callback.onImageLoaded(cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = loadBitmap(imageUrl, articleId, targetSizePx);

            if (bitmap != null) {
                memoryCache.put(key, bitmap);
            }

            final Bitmap result = bitmap;
            mainHandler.post(() -> callback.onImageLoaded(result));
        });
    }

    private static Bitmap loadBitmap(String imageUrl, int articleId, int targetSizePx) {
        try {
            File cachedFile = null;
            if (App.getSettings().isImageCacheEnabled()) {
                cachedFile = ImageCacheUtils.getCachedImageFile(imageUrl, articleId);
            }

            if (cachedFile != null) {
                return decodeSampled(cachedFile.getAbsolutePath(), targetSizePx);
            }

            if (imageUrl.startsWith(ImageCacheUtils.WALLABAG_RELATIVE_URL_PATH)) {
                imageUrl = App.getSettings().getUrl() + imageUrl;
            }

            if (client == null) {
                client = WallabagConnection.createClient(false);
            }

            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }

                return decodeSampled(response.body().bytes(), targetSizePx);
            }
        } catch (Exception e) {
            Log.w(TAG, "loadBitmap() failed for " + imageUrl, e);
            return null;
        }
    }

    private static Bitmap decodeSampled(String path, int targetSizePx) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, targetSizePx);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(path, options);
    }

    private static Bitmap decodeSampled(byte[] data, int targetSizePx) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = calculateInSampleSize(options, targetSizePx);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int targetSizePx) {
        if (targetSizePx <= 0) return 1;

        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((width / inSampleSize) > targetSizePx * 2
                || (height / inSampleSize) > targetSizePx * 2) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

}
