/*
 * Copyright (C) 2019 Peng fei Pan <panpfpanpf@outlook.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.mikaelzero.mojito.view.sketch.core.decode;

import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawableResource;
import com.bumptech.glide.util.ByteBufferUtil;

import net.mikaelzero.mojito.view.sketch.core.ErrorTracker;
import net.mikaelzero.mojito.view.sketch.core.datasource.DataSource;
import net.mikaelzero.mojito.view.sketch.core.drawable.GlideSketchGifDrawable;
import net.mikaelzero.mojito.view.sketch.core.request.ErrorCause;
import net.mikaelzero.mojito.view.sketch.core.request.LoadRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class GifDecodeHelper extends DecodeHelper {
    // Glide allocates a logical-canvas byte array even when it downsamples decoded frames.
    private static final long MAX_CANVAS_PIXELS = 32L * 1024 * 1024;
    private static final long MAX_FRAME_PIXELS = 4L * 1024 * 1024;
    private static final int MAX_GIF_BYTES = 32 * 1024 * 1024;

    @Override
    public boolean match(@NonNull LoadRequest request, @NonNull DataSource dataSource,
                         @Nullable ImageType imageType, @NonNull BitmapFactory.Options boundOptions) {
        return imageType == ImageType.GIF && request.getOptions().isDecodeGifImage();
    }

    @NonNull
    @Override
    public DecodeResult decode(@NonNull LoadRequest request, @NonNull DataSource dataSource,
                               @Nullable ImageType imageType, @NonNull BitmapFactory.Options boundOptions,
                               @NonNull BitmapFactory.Options decodeOptions, int exifOrientation) throws DecodeException {
        GifDrawableResource resource = null;
        try {
            ImageAttrs attrs = new ImageAttrs(boundOptions.outMimeType, boundOptions.outWidth,
                    boundOptions.outHeight, exifOrientation);
            long pixels = (long) attrs.getWidth() * attrs.getHeight();
            if (pixels > MAX_CANVAS_PIXELS) {
                throw new DecodeException("GIF canvas exceeds decode limit", ErrorCause.DECODE_UNABLE_CREATE_GIF_DRAWABLE);
            }
            int sample = 1;
            while (pixels / sample / sample > MAX_FRAME_PIXELS) sample *= 2;
            ByteBuffer bytes = readGif(dataSource);
            resource = new ByteBufferGifDecoder(request.getContext()).decode(bytes,
                    Math.max(1, attrs.getWidth() / sample), Math.max(1, attrs.getHeight() / sample), new Options());
            if (resource == null) {
                throw new DecodeException("Unable to decode GIF", ErrorCause.DECODE_UNABLE_CREATE_GIF_DRAWABLE);
            }

            GlideSketchGifDrawable drawable = new GlideSketchGifDrawable(resource, request.getKey(),
                    request.getUri(), attrs, dataSource.getImageFrom());
            DecodeResult result = new GifDecodeResult(attrs, drawable).setBanProcess(true);
            resource = null; // The request/view now owns this drawable and its decoder.
            return result;
        } catch (DecodeException e) {
            throw e;
        } catch (IOException e) {
            throw new DecodeException(e, ErrorCause.DECODE_FILE_IO_EXCEPTION);
        } catch (RuntimeException | OutOfMemoryError e) {
            ErrorTracker errorTracker = request.getConfiguration().getErrorTracker();
            errorTracker.onDecodeGifImageError(e, request, boundOptions.outWidth, boundOptions.outHeight, boundOptions.outMimeType);
            throw new DecodeException(e, ErrorCause.DECODE_UNABLE_CREATE_GIF_DRAWABLE);
        } finally {
            if (resource != null) resource.recycle();
        }
    }

    private static ByteBuffer readGif(DataSource dataSource) throws IOException {
        File file = dataSource.getFile(null, null);
        if (file != null) {
            if (file.length() > MAX_GIF_BYTES) throw new IOException("GIF exceeds input size limit");
            // The full-screen viewer already has a local file; map it rather than copying it to the heap.
            return ByteBufferUtil.fromFile(file);
        }
        try (InputStream stream = dataSource.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                if (read > MAX_GIF_BYTES - out.size()) throw new IOException("GIF exceeds input size limit");
                out.write(chunk, 0, read);
            }
            return ByteBuffer.wrap(out.toByteArray());
        }
    }
}
