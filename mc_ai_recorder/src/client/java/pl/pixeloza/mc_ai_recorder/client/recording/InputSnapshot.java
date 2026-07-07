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

        boolean inventory,
        boolean inventoryOpen,

        /*
         * hotbarChanged = czy w tym ticku wybrano inny slot
         * hotbarTarget  = 0-8 albo -1, gdy nie było zmiany
         */
        boolean hotbarChanged,
        int hotbarTarget,

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