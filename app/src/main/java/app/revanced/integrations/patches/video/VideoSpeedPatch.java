package app.revanced.integrations.patches.video;

import static app.revanced.integrations.utils.ReVancedUtils.showToastShort;
import static app.revanced.integrations.utils.StringRef.str;

import androidx.annotation.NonNull;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.whitelist.Whitelist;

public class VideoSpeedPatch {
    private static float selectedSpeed = -1.0f;

    private static boolean newVideo = false;

    private static boolean isLiveVideo = false;

    public static void userChangedSpeed(final float speed) {
        selectedSpeed = speed;

        if (SettingsEnum.ENABLE_SAVE_VIDEO_SPEED.getBoolean()) {
            SettingsEnum.DEFAULT_VIDEO_SPEED.saveValue(speed);
            showToastShort(str("revanced_save_video_speed") + speed + "x");
        }
    }

    public static void setDefaultSpeed() {
        float defaultSpeed = selectedSpeed;
        if (isLiveVideo) {
            isLiveVideo = false;
            newVideo = false;
            defaultSpeed = 1.0f;
        }
        if (newVideo) {
            defaultSpeed = SettingsEnum.DEFAULT_VIDEO_SPEED.getFloat();
            selectedSpeed = defaultSpeed;
            newVideo = false;

            if (Whitelist.isChannelSPEEDWhitelisted()) defaultSpeed = -1.0f;
            else if (!isCustomVideoSpeedEnabled() && defaultSpeed >= 2.0f) defaultSpeed = 2.0f;
        }
        overrideSpeed(defaultSpeed);
    }

    public static void liveVideoStarted() {
        isLiveVideo = true;
    }

    public static void newVideoStarted(@NonNull String videoId) {
        newVideo = true;
    }

    public static void overrideSpeed(final float speedValue) {
        if (speedValue != selectedSpeed)
            selectedSpeed = speedValue;
    }

    public static boolean isCustomVideoSpeedEnabled() {
        return SettingsEnum.ENABLE_CUSTOM_VIDEO_SPEED.getBoolean();
    }

}
