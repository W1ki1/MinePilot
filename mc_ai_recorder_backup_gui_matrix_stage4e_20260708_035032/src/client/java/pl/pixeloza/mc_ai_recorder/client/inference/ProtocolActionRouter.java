package pl.pixeloza.mc_ai_recorder.client.inference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import pl.pixeloza.mc_ai_recorder.client.recording.MenuSemantics;

public final class ProtocolActionRouter {
    public RoutedAction resolve(
            Minecraft client,
            ProtocolAction protocolAction,
            boolean controlEnabled
    ) {
        if (protocolAction == null) {
            return safe(
                    Route.WAITING_SAFE_IDLE
            );
        }

        try {
            protocolAction.validate();
        } catch (RuntimeException e) {
            return safe(
                    Route.INVALID_SAFE_IDLE
            );
        }

        if (protocolAction.isSafeIdle()) {
            return new RoutedAction(
                    Route.SYSTEM_SAFE_IDLE,
                    null,
                    null,
                    true
            );
        }

        if (!controlEnabled) {
            return safe(
                    Route.CONTROL_OFF_SAFE_IDLE
            );
        }

        if (client.player == null
                || client.level == null
                || client.options == null) {
            return safe(
                    Route.NO_PLAYER_SAFE_IDLE
            );
        }

        if (protocolAction.isGui()) {
            if (!(client.screen
                    instanceof AbstractContainerScreen<?>)) {
                return safe(
                        Route.NO_CONTAINER_SCREEN_SAFE_IDLE
                );
            }

            ProtocolAction.GuiAction guiAction =
                    protocolAction.gui();

            String currentScreenType =
                    MenuSemantics.screenType(
                            client.screen
                    );

            String currentMenuType =
                    MenuSemantics.menuType(
                            client.screen
                    );

            if ("CreativeModeInventoryScreen"
                    .equals(currentScreenType)
                    || "ItemPickerMenu"
                    .equals(currentMenuType)) {
                return safe(
                        Route.CREATIVE_GUI_SAFE_IDLE
                );
            }

            if (!guiAction.screenType()
                    .equals(currentScreenType)
                    || !guiAction.menuType()
                    .equals(currentMenuType)) {
                return safe(
                        Route.GUI_CONTEXT_MISMATCH_SAFE_IDLE
                );
            }

            return new RoutedAction(
                    Route.GUI,
                    null,
                    guiAction,
                    false
            );
        }

        if (!protocolAction.isWorld()) {
            return safe(
                    Route.INVALID_SAFE_IDLE
            );
        }

        if (client.screen != null) {
            return safe(
                    Route.SCREEN_OPEN_SAFE_IDLE
            );
        }

        return new RoutedAction(
                Route.WORLD,
                protocolAction.toAiAction(
                        System.currentTimeMillis()
                ),
                null,
                true
        );
    }

    private RoutedAction safe(
            Route route
    ) {
        return new RoutedAction(
                route,
                null,
                null,
                false
        );
    }

    public enum Route {
        WAITING_SAFE_IDLE,
        SYSTEM_SAFE_IDLE,
        WORLD,
        GUI,
        CONTROL_OFF_SAFE_IDLE,
        SCREEN_OPEN_SAFE_IDLE,
        NO_CONTAINER_SCREEN_SAFE_IDLE,
        CREATIVE_GUI_SAFE_IDLE,
        GUI_CONTEXT_MISMATCH_SAFE_IDLE,
        NO_PLAYER_SAFE_IDLE,
        INVALID_SAFE_IDLE
    }

    public record RoutedAction(
            Route route,
            AiAction worldAction,
            ProtocolAction.GuiAction guiAction,
            boolean applied
    ) {
    }
}
