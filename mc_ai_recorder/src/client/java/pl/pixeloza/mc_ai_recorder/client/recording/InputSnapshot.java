package pl.pixeloza.mc_ai_recorder.client.recording;

public record InputSnapshot(
        long tick,
        long timestamp,
        String frame,

        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean jump,
        boolean sneak,
        boolean sprintKey,
        boolean sprinting,
        boolean attack,
        boolean use,

        float yaw,
        float pitch,
        float yawDelta,
        float pitchDelta,

        double x,
        double y,
        double z,

        float health,
        int food,
        int selectedSlot,

        String selectedItem,
        int selectedItemCount,

        String dimension,
        String biome,

        boolean onGround,
        boolean inWater,
        boolean underWater,
        boolean fallFlying,

        int experienceLevel
) {
}