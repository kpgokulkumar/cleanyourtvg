package com.gathin.tvhome;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/**
 * config wallpaper_video: loops wallpaper.mp4 silently behind Home. The picture stays hidden, so Home opens on the
 * plain background and then the video, with no other image in between; it comes back only when the file will not
 * play. The player is released whenever Home is left, so YouTube and the other apps get the hardware decoder to
 * themselves.
 */
final class VideoWallpaper implements SurfaceHolder.Callback {
    private final SurfaceView surface;
    /** wallpaper_dim as a shade over the video; the picture has its dim painted in. */
    private final View shade;
    /** The picture, shown only once the video has failed; null when there is none. */
    private final View picture;
    private final File file;
    private MediaPlayer player;
    private boolean active;
    private boolean created;
    private boolean closed;
    /** Set once the file fails to play: the picture stays for the rest of this process. */
    private boolean failed;

    VideoWallpaper(Context context, FrameLayout root, View picture, int dimColor, File file) {
        this.picture = picture;
        this.file = file;
        surface = new SurfaceView(context);
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.getHolder().addCallback(this);
        if (picture != null) picture.setVisibility(View.INVISIBLE);
        root.addView(surface, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (dimColor != 0) {
            shade = new View(context);
            shade.setBackgroundColor(dimColor);
            shade.setVisibility(View.GONE);
            root.addView(shade, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            shade = null;
        }
    }

    /** True until the file fails to play: the picture stays hidden, so its rotation can rest. */
    boolean playing() {
        return !failed;
    }

    void start() {
        active = true;
        play();
    }

    void stop() {
        active = false;
        release();
    }

    void close() {
        closed = true;
        stop();
        surface.getHolder().removeCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        created = true;
        play();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        created = false;
        release();
    }

    private void play() {
        if (!active || !created || closed || failed || player != null) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setDataSource(file.getPath());
            next.setDisplay(surface.getHolder());
            next.setLooping(true);
            next.setVolume(0f, 0f);
            next.setOnPreparedListener(mp -> {
                if (mp != player) return;
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                mp.start();
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (mp == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) showVideo(true);
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> {
                Log.w("TvHome.Wallpaper", "Video wallpaper failed: " + what + "/" + extra);
                failed = true;
                release();
                return true;
            });
            next.prepareAsync();
        } catch (Exception failure) {
            Log.w("TvHome.Wallpaper", "Video wallpaper unreadable", failure);
            failed = true;
            release();
        }
    }

    private void release() {
        showVideo(false);
        if (player == null) return;
        MediaPlayer old = player;
        player = null;
        old.release();
    }

    private void showVideo(boolean shown) {
        if (shade != null) shade.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (picture != null) picture.setVisibility(failed ? View.VISIBLE : View.INVISIBLE);
    }
}
