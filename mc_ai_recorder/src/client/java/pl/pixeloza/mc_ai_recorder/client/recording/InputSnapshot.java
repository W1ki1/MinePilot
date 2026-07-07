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

        /*
         * Akcja otwarcia/zamknięcia ekwipunku gracza.
         */
        boolean inventory,

        /*
         * Czy otwarty jest zwykły InventoryScreen.
         */
        boolean inventoryOpen,

        /*
         * Czy otwarty jest jakikolwiek ekran:
         * inventory, crafting table, skrzynia, piec,
         * chat, menu itd.
         */
        boolean guiOpen,

        /*
         * Nazwa klasy aktualnego ekranu,
         * np. InventoryScreen, CraftingScreen,
         * ChestScreen albo NONE.
         */
        String screenType,

        /*
         * Akcja zmiany hotbara.
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