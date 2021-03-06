package org.bottiger.podcast.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.BitmapRequestBuilder;
import com.bumptech.glide.DrawableTypeRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;

import org.bottiger.podcast.R;
import org.bottiger.podcast.service.Downloader.SoundWavesDownloadManager;
import org.bottiger.podcast.utils.image.NetworkDisablingLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by aplb on 30-09-2015.
 */
public class ImageLoaderUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NETWORK, NO_NETWORK, DEFAULT})
    public @interface NetworkPolicy {}
    public static final int NETWORK = 1;
    public static final int NO_NETWORK = 2;
    public static final int DEFAULT = 3;

    public static final DiskCacheStrategy SW_DiskCacheStrategy = DiskCacheStrategy.SOURCE;

    public static BitmapRequestBuilder getGlide(@NonNull Context argContext, @NonNull String argUrl) {
        return getGlide(argContext, argUrl, DEFAULT);
    }

    public static BitmapRequestBuilder getGlide(@NonNull Context argContext, @NonNull String argUrl, @NetworkPolicy int argAllowNetwork) {
        RequestManager requestManager = Glide.with(argContext);

        DrawableTypeRequest drawableTypeRequest;
        BitmapRequestBuilder bitmapRequestBuilder;
        boolean noNetwork = argAllowNetwork == NO_NETWORK || (argAllowNetwork != NETWORK && NetworkUtils.getNetworkStatus(argContext, false) != SoundWavesDownloadManager.NETWORK_OK);

        if (noNetwork) {
            drawableTypeRequest = requestManager.using(new NetworkDisablingLoader()).load(argUrl);
        } else {
            drawableTypeRequest = requestManager.load(argUrl);
        }

         bitmapRequestBuilder = drawableTypeRequest
                 .asBitmap()
                 .diskCacheStrategy( SW_DiskCacheStrategy );


        return bitmapRequestBuilder;
    }

    @Deprecated
    public static void loadImageInto(@NonNull View argImageView,
                                     @Nullable String argUrl,
                                     boolean argUsePlaceholder,
                                     boolean argRoundedCorners,
                                     @NetworkPolicy int argNetworkPolicy) {
        loadImageUsingGlide(argImageView, argUrl, null, true, argUsePlaceholder, argRoundedCorners, argNetworkPolicy);
    }

    public static void loadImageInto(@NonNull View argImageView,
                                     @Nullable String argUrl,
                                     @Nullable Transformation argTransformation,
                                     boolean argDoCrop,
                                     boolean argUsePlaceholder,
                                     boolean argRoundedCorners,
                                     @NetworkPolicy int argNetworkPolicy) {
        if (argUrl == null)
            return;

        loadImageUsingGlide(argImageView, argUrl, argTransformation, argDoCrop, argUsePlaceholder, argRoundedCorners, argNetworkPolicy);
    }

    private static void loadImageUsingGlide(final @NonNull View argTargetView,
                                            @Nullable String argUrl,
                                            @Nullable Transformation argTransformation,
                                            boolean argDoCrop,
                                            boolean argUsePlaceholder,
                                            final boolean argRounddedCorners,
                                            @NetworkPolicy int argNetworkPolicy) {
        Context context = argTargetView.getContext();

        Target target;

        if (argTargetView instanceof ImageView) {
            target = getImageViewTarget((ImageView)argTargetView, argRounddedCorners);
        } else {
            target = getViewTarget(argTargetView, argRounddedCorners);
        }

        BitmapRequestBuilder builder = ImageLoaderUtils.getGlide(context, argUrl, argNetworkPolicy);

        if (argDoCrop)
            builder.centerCrop();
        else
            builder.override(512, 512).fitCenter();

        if (argUsePlaceholder) {
            builder.placeholder(R.drawable.generic_podcast);
        }

        builder.into(target);
    }

    private static SimpleTarget<Bitmap> getViewTarget(@NonNull final View argImageView, final boolean argRounddedCorners) {
        return new SimpleTarget<Bitmap>(512, 512) {
            @Override
            public void onResourceReady(Bitmap bitmap, GlideAnimation anim) {
                BitmapDrawable bd = new BitmapDrawable(argImageView.getResources(), bitmap);
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    argImageView.setBackgroundDrawable(bd);
                } else {
                    argImageView.setBackground(bd);
                }
            }
        };
    }

    private static BitmapImageViewTarget getImageViewTarget(@NonNull final ImageView argImageView, final boolean argRounddedCorners) {
        return new BitmapImageViewTarget(argImageView) {
            @Override
            protected void setResource(Bitmap resource) {
                RoundedBitmapDrawable circularBitmapDrawable =
                        RoundedBitmapDrawableFactory.create(argImageView.getResources(), resource);
                float radius = argImageView.getResources().getDimension(R.dimen.playlist_image_radius_small);
                radius = argRounddedCorners ? radius : 0;
                circularBitmapDrawable.setCornerRadius(radius);

                argImageView.setImageDrawable(circularBitmapDrawable);
            }
        };
    }

}
