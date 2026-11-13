package com.newtube.mobile.ui.playback;

/** Release selection calibrated against short swipes in YouTube on Pixel 10. */
final class MiniPlayerFling {
    private static final float MINIMUM_TRAVEL_DP = 40f;
    private static final float MINIMUM_VELOCITY_DP_PER_SECOND = 600f;

    private MiniPlayerFling() {}

    static float releaseCoordinate(float current, float travel, float velocity,
                                   float minimum, float maximum, float density) {
        if (Math.abs(travel) >= MINIMUM_TRAVEL_DP * density
                && Math.abs(velocity) >= MINIMUM_VELOCITY_DP_PER_SECOND * density) {
            return velocity < 0f ? minimum : maximum;
        }
        return current;
    }
}
