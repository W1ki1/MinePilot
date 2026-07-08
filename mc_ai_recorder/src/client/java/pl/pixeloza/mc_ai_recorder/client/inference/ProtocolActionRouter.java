package pl.pixeloza.mc_ai_recorder.client.inference;

import net.minecraft.client.Minecraft;

public final class ProtocolActionRouter {
    public RoutedAction resolve(
            Minecraft client,
            ProtocolAction protocolAction,
            boolean controlEnabled
    ) {
        if (protocolAction == null) {
            return new RoutedAction(
                    Route.WAITING_SAFE_IDLE,
                    null,
                    false
            );
        }

        try {
            protocolAction.validate();
        } catch (RuntimeException e) {
            return new RoutedAction(
                    Route.INVALID_SAFE_IDLE,
                    null,
                    false
            );
        }

        if (protocolAction.isSafeIdle()) {
            return new RoutedAction(
                    Route.SYSTEM_SAFE_IDLE,
                    null,
                    true
            );
        }

        if (protocolAction.isGui()) {
            return new RoutedAction(
                    Route.GUI_NOT_IMPLEMENTED_SAFE_IDLE,
                    null,
                    false
            );
        }

        if (!protocolAction.isWorld()) {
            return new RoutedAction(
                    Route.INVALID_SAFE_IDLE,
                    null,
                    false
            );
        }

        if (!controlEnabled) {
            return new RoutedAction(
                    Route.CONTROL_OFF_SAFE_IDLE,
                    null,
                    false
            );
        }

        if (client.player == null
                || client.level == null
                || client.options == null) {
            return new RoutedAction(
                    Route.NO_PLAYER_SAFE_IDLE,
                    null,
                    false
            );
        }

        if (client.screen != null) {
            return new RoutedAction(
                    Route.SCREEN_OPEN_SAFE_IDLE,
                    null,
                    false
            );
        }

        return new RoutedAction(
                Route.WORLD,
                protocolAction.toAiAction(
                        System.currentTimeMillis()
                ),
                true
        );
    }

    public enum Route {
        WAITING_SAFE_IDLE,
        SYSTEM_SAFE_IDLE,
        WORLD,
        CONTROL_OFF_SAFE_IDLE,
        SCREEN_OPEN_SAFE_IDLE,
        GUI_NOT_IMPLEMENTED_SAFE_IDLE,
        NO_PLAYER_SAFE_IDLE,
        INVALID_SAFE_IDLE
    }

    public record RoutedAction(
            Route route,
            AiAction action,
            boolean applied
    ) {
    }
}
