package app.revanced.integrations.youtube.patches.misc;

import static app.revanced.integrations.shared.utils.Utils.submitOnBackgroundThread;
import static app.revanced.integrations.youtube.patches.misc.requests.LiveStreamRendererRequester.getLiveStreamRenderer;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.revanced.integrations.shared.utils.Logger;
import app.revanced.integrations.youtube.patches.misc.requests.PlayerRoutes.ClientType;
import app.revanced.integrations.youtube.settings.Settings;

/**
 * @noinspection ALL
 */
public class SpoofClientPatch {
    private static final boolean SPOOF_CLIENT_ENABLED = Settings.SPOOF_CLIENT.get();

    /**
     * Clips or Shorts Parameters.
     */
    private static final String[] CLIPS_OR_SHORTS_PARAMETERS = {
            "kAIB", // Clips
            "8AEB"  // Shorts
    };

    /**
     * iOS client is used for Clips or Shorts.
     */
    private static volatile boolean isShortsOrClips;

    /**
     * Any unreachable ip address.  Used to intentionally fail requests.
     */
    private static final String UNREACHABLE_HOST_URI_STRING = "https://127.0.0.0";
    private static final Uri UNREACHABLE_HOST_URI = Uri.parse(UNREACHABLE_HOST_URI_STRING);

    /**
     * Last spoofed client type.
     */
    private static volatile ClientType lastSpoofedClientType;

    /**
     * Last video id loaded. Used to prevent reloading the same spec multiple times.
     */
    @NonNull
    private static volatile String lastPlayerResponseVideoId = "";

    @Nullable
    private static volatile Future<LiveStreamRenderer> rendererFuture;

    /**
     * Injection point.
     * Blocks /get_watch requests by returning a localhost URI.
     *
     * @param playerRequestUri The URI of the player request.
     * @return Localhost URI if the request is a /get_watch request, otherwise the original URI.
     */
    public static Uri blockGetWatchRequest(Uri playerRequestUri) {
        if (SPOOF_CLIENT_ENABLED) {
            try {
                String path = playerRequestUri.getPath();

                if (path != null && path.contains("get_watch")) {
                    Logger.printDebug(() -> "Blocking: " + playerRequestUri + " by returning: " + UNREACHABLE_HOST_URI_STRING);

                    return UNREACHABLE_HOST_URI;
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockGetWatchRequest failure", ex);
            }
        }

        return playerRequestUri;
    }

    /**
     * Injection point.
     *
     * Blocks /initplayback requests.
     */
    public static String blockInitPlaybackRequest(String originalUrlString) {
        if (SPOOF_CLIENT_ENABLED) {
            try {
                Uri originalUri = Uri.parse(originalUrlString);
                String path = originalUri.getPath();

                if (path != null && path.contains("initplayback")) {
                    String replacementUriString = (getSpoofClientType() != ClientType.ANDROID_TESTSUITE)
                            ? UNREACHABLE_HOST_URI_STRING
                            // TODO: Ideally, a local proxy could be setup and block
                            //  the request the same way as Burp Suite is capable of
                            //  because that way the request is never sent to YouTube unnecessarily.
                            //  Just using localhost unfortunately does not work.
                            : originalUri.buildUpon().clearQuery().build().toString();

                    Logger.printDebug(() -> "Blocking: " + originalUrlString + " by returning: " + replacementUriString);

                    return replacementUriString;
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockInitPlaybackRequest failure", ex);
            }
        }

        return originalUrlString;
    }

    private static ClientType getSpoofClientType() {
        if (isShortsOrClips) {
            lastSpoofedClientType = Settings.SPOOF_CLIENT_SHORTS.get();
            return lastSpoofedClientType;
        }
        LiveStreamRenderer renderer = getRenderer(false);
        if (renderer != null) {
            if (renderer.isLive) {
                lastSpoofedClientType = Settings.SPOOF_CLIENT_LIVESTREAM.get();
                return lastSpoofedClientType;
            }
            if (!renderer.playabilityOk) {
                lastSpoofedClientType = Settings.SPOOF_CLIENT_FALLBACK.get();
                return lastSpoofedClientType;
            }
        }
        lastSpoofedClientType = Settings.SPOOF_CLIENT_GENERAL.get();
        return lastSpoofedClientType;
    }

    /**
     * Injection point.
     */
    public static int getClientTypeId(int originalClientTypeId) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().id;
        }

        return originalClientTypeId;
    }

    /**
     * Injection point.
     */
    public static String getClientVersion(String originalClientVersion) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().version;
        }

        return originalClientVersion;
    }

    /**
     * Injection point.
     */
    public static String getClientModel(String originalClientModel) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().model;
        }

        return originalClientModel;
    }

    /**
     * Injection point.
     */
    public static boolean isClientSpoofingEnabled() {
        return SPOOF_CLIENT_ENABLED;
    }

    /**
     * Injection point.
     */
    public static boolean enablePlayerGesture(boolean original) {
        return SPOOF_CLIENT_ENABLED || original;
    }

    /**
     * Injection point.
     * When spoofing the client to iOS or Android Testsuite the playback speed menu is missing from the player response.
     * Return true to force create the playback speed menu.
     */
    public static boolean forceCreatePlaybackSpeedMenu(boolean original) {
        if (SPOOF_CLIENT_ENABLED) {
            return true;
        }

        return original;
    }

    /**
     * Injection point.
     * When spoofing the client to Android TV the playback speed menu is missing from the player response.
     * Return false to force create the playback speed menu.
     */
    public static boolean forceCreatePlaybackSpeedMenuReversed(boolean original) {
        if (SPOOF_CLIENT_ENABLED) {
            return false;
        }

        return original;
    }

    /**
     * Injection point.
     */
    public static String appendSpoofedClient(String videoFormat) {
        try {
            if (SPOOF_CLIENT_ENABLED && Settings.SPOOF_CLIENT_STATS_FOR_NERDS.get()
                    && !TextUtils.isEmpty(videoFormat)) {
                // Force LTR layout, to match the same LTR video time/length layout YouTube uses for all languages
                return "\u202D" + videoFormat + String.format("\u2009(%s)", lastSpoofedClientType.friendlyName); // u202D = left to right override
            }
        } catch (Exception ex) {
            Logger.printException(() -> "appendSpoofedClient failure", ex);
        }

        return videoFormat;
    }

    /**
     * Injection point.
     */
    public static String setPlayerResponseVideoId(@NonNull String videoId, @Nullable String parameters, boolean isShortAndOpeningOrPlaying) {
        if (SPOOF_CLIENT_ENABLED) {
            isShortsOrClips = playerParameterIsClipsOrShorts(parameters, isShortAndOpeningOrPlaying);

            if (!isShortsOrClips) {
                fetchLiveStreamRenderer(videoId, Settings.SPOOF_CLIENT_GENERAL.get());
            }
        }

        return parameters; // Return the original value since we are observing and not modifying.
    }

    /**
     * @return If the player parameters are for a Short or Clips.
     */
    private static boolean playerParameterIsClipsOrShorts(@Nullable String playerParameter, boolean isShortAndOpeningOrPlaying) {
        if (isShortAndOpeningOrPlaying) {
            return true;
        }

        return playerParameter != null && StringUtils.startsWithAny(playerParameter, CLIPS_OR_SHORTS_PARAMETERS);
    }

    private static void fetchLiveStreamRenderer(@NonNull String videoId, @NonNull ClientType clientType) {
        if (!videoId.equals(lastPlayerResponseVideoId)) {
            rendererFuture = submitOnBackgroundThread(() -> getLiveStreamRenderer(videoId, clientType));
            lastPlayerResponseVideoId = videoId;
        }
        // Block until the renderer fetch completes.
        // This is desired because if this returns without finishing the fetch
        // then video will start playback but the storyboard is not ready yet.
        getRenderer(true);
    }

    @Nullable
    private static LiveStreamRenderer getRenderer(boolean waitForCompletion) {
        Future<LiveStreamRenderer> future = rendererFuture;
        if (future != null) {
            try {
                if (waitForCompletion || future.isDone()) {
                    return future.get(20000, TimeUnit.MILLISECONDS); // Any arbitrarily large timeout.
                } // else, return null.
            } catch (TimeoutException ex) {
                Logger.printDebug(() -> "Could not get renderer (get timed out)");
            } catch (ExecutionException | InterruptedException ex) {
                // Should never happen.
                Logger.printException(() -> "Could not get renderer", ex);
            }
        }
        return null;
    }
}