package net.mikaelzero.mojito.view.sketch;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

import net.mikaelzero.mojito.interfaces.ImageViewLoadFactory;
import net.mikaelzero.mojito.loader.ContentLoader;
import net.mikaelzero.mojito.view.sketch.core.Sketch;
import net.mikaelzero.mojito.view.sketch.core.SketchImageView;
import net.mikaelzero.mojito.view.sketch.core.drawable.GlideSketchGifDrawable;

import org.jetbrains.annotations.NotNull;


public class SketchImageLoadFactory implements ImageViewLoadFactory {
    @Override
    public void loadSillContent(@NotNull View view, @NotNull Uri uri) {
        if (view instanceof SketchImageView) {
            Sketch.with(view.getContext()).display(uri.getPath(), (SketchImageView) view).loadingImage((context, sketchView, displayOptions) -> {
                Drawable current = ((SketchImageView) view).getDrawable();
                // Still images can be shared with the loading wrapper. GIF frames are pooled by
                // Glide and the old animation will be recycled as soon as Sketch replaces it.
                return current instanceof GlideSketchGifDrawable
                        ? ((GlideSketchGifDrawable) current).snapshot(view.getResources())
                        : current;
            }).commit();
        }
    }

    @Override
    public void loadContentFail(@NotNull View view, int drawableResId) {
        if (view instanceof SketchImageView) {
            ((SketchImageView) view).displayResourceImage(drawableResId);
        }
    }

    @NotNull
    @Override
    public ContentLoader newContentLoader() {
        return new SketchContentLoaderImpl();
    }
}
