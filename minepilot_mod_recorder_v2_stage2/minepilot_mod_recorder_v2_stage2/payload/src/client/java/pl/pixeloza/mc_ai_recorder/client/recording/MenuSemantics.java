package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class MenuSemantics {
    private MenuSemantics() {
    }

    public static String screenType(
            Screen screen
    ) {
        if (screen == null) {
            return "NONE";
        }

        String simpleName =
                screen.getClass()
                        .getSimpleName();

        if ("ContainerScreen".equals(simpleName)) {
            return "GenericContainerScreen";
        }

        return simpleName;
    }

    public static String menuType(
            Screen screen
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return "NONE";
        }

        String simpleName =
                containerScreen.getMenu()
                        .getClass()
                        .getSimpleName();

        if ("ChestMenu".equals(simpleName)) {
            return "GenericContainerMenu";
        }

        return simpleName;
    }

    public static int slotCount(
            Screen screen
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return 0;
        }

        return containerScreen.getMenu()
                .slots
                .size();
    }

    public static String slotRole(
            Screen screen,
            int slotId
    ) {
        int slotCount =
                slotCount(screen);

        if (slotId < 0 || slotId >= slotCount) {
            return "UNKNOWN";
        }

        String menuType =
                menuType(screen);

        if ("InventoryMenu".equals(menuType)) {
            if (slotId == 0) {
                return "CRAFT_OUTPUT";
            }

            if (slotId >= 1 && slotId <= 4) {
                return "CRAFT_INPUT";
            }

            if (slotId >= 5 && slotId <= 8) {
                return "ARMOR";
            }

            if (slotId >= 9 && slotId <= 35) {
                return "PLAYER_INVENTORY";
            }

            if (slotId >= 36 && slotId <= 44) {
                return "HOTBAR";
            }

            if (slotId == 45) {
                return "OFFHAND";
            }
        }

        if ("CraftingMenu".equals(menuType)) {
            if (slotId == 0) {
                return "CRAFT_OUTPUT";
            }

            if (slotId >= 1 && slotId <= 9) {
                return "CRAFT_INPUT";
            }

            return playerInventoryRole(
                    slotId,
                    slotCount
            );
        }

        if ("FurnaceMenu".equals(menuType)) {
            if (slotId == 0) {
                return "FURNACE_INPUT";
            }

            if (slotId == 1) {
                return "FURNACE_FUEL";
            }

            if (slotId == 2) {
                return "FURNACE_OUTPUT";
            }

            return playerInventoryRole(
                    slotId,
                    slotCount
            );
        }

        if ("BlastFurnaceMenu".equals(menuType)) {
            if (slotId == 0) {
                return "BLAST_FURNACE_INPUT";
            }

            if (slotId == 1) {
                return "BLAST_FURNACE_FUEL";
            }

            if (slotId == 2) {
                return "BLAST_FURNACE_OUTPUT";
            }

            return playerInventoryRole(
                    slotId,
                    slotCount
            );
        }

        if ("SmokerMenu".equals(menuType)) {
            if (slotId == 0) {
                return "SMOKER_INPUT";
            }

            if (slotId == 1) {
                return "SMOKER_FUEL";
            }

            if (slotId == 2) {
                return "SMOKER_OUTPUT";
            }

            return playerInventoryRole(
                    slotId,
                    slotCount
            );
        }

        String playerRole =
                playerInventoryRole(
                        slotId,
                        slotCount
                );

        if (!"UNKNOWN".equals(playerRole)) {
            return playerRole;
        }

        if (slotCount >= 36
                && slotId < slotCount - 36) {
            return "CONTAINER";
        }

        return "UNKNOWN";
    }

    private static String playerInventoryRole(
            int slotId,
            int slotCount
    ) {
        if (slotCount < 36) {
            return "UNKNOWN";
        }

        int playerStart =
                slotCount - 36;

        int hotbarStart =
                slotCount - 9;

        if (slotId >= hotbarStart) {
            return "HOTBAR";
        }

        if (slotId >= playerStart) {
            return "PLAYER_INVENTORY";
        }

        return "UNKNOWN";
    }
}
