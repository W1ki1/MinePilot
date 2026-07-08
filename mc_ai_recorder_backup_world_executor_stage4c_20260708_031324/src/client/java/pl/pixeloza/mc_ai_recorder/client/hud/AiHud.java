package pl.pixeloza.mc_ai_recorder.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import pl.pixeloza.mc_ai_recorder.client.inference.AiAction;

public final class AiHud {
    private AiHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(
                        "mc_ai_recorder",
                        "ai_debug_hud"
                ),
                AiHud::render
        );
    }

    public static void toggle() {
        AiDebugState.hudVisible =
                !AiDebugState.hudVisible;

        Minecraft client =
                Minecraft.getInstance();

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal(
                            "[MC AI Recorder] Debug HUD: "
                                    + (
                                    AiDebugState.hudVisible
                                            ? "ON"
                                            : "OFF"
                            )
                    )
            );
        }
    }

    public static boolean isVisible() {
        return AiDebugState.hudVisible;
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker
    ) {
        if (!AiDebugState.hudVisible) {
            return;
        }

        Minecraft client =
                Minecraft.getInstance();

        if (client.player == null
                || client.font == null) {
            return;
        }

        int x = 8;
        int y = 8;
        int lineHeight = 10;

        int white = 0xFFFFFFFF;
        int cyan = 0xFF55FFFF;
        int green = 0xFF55FF55;
        int red = 0xFFFF5555;
        int yellow = 0xFFFFFF55;

        graphics.fill(
                x - 4,
                y - 4,
                x + 290,
                y + 152,
                0x90000000
        );

        graphics.text(
                client.font,
                "MinePilot AI",
                x,
                y,
                cyan,
                true
        );

        y += lineHeight + 2;

        graphics.text(
                client.font,
                "TCP v2: "
                        + (
                        AiDebugState.tcpEnabled
                                ? "ON"
                                : "OFF"
                ),
                x,
                y,
                AiDebugState.tcpEnabled
                        ? green
                        : red,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "Protocol: "
                        + AiDebugState.protocolState,
                x,
                y,
                AiDebugState.connected
                        ? green
                        : yellow,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "Control: "
                        + (
                        AiDebugState.aiControlEnabled
                                ? "ON"
                                : "OFF"
                ),
                x,
                y,
                AiDebugState.aiControlEnabled
                        ? green
                        : red,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "Connected: "
                        + AiDebugState.connected,
                x,
                y,
                AiDebugState.connected
                        ? green
                        : red,
                true
        );

        y += lineHeight;

        String rttText =
                AiDebugState.lastRoundtripMs >= 0
                        ? AiDebugState.lastRoundtripMs
                        + " ms"
                        : "---";

        graphics.text(
                client.font,
                "RTT: " + rttText,
                x,
                y,
                yellow,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "JPEG: "
                        + AiDebugState.jpegSize
                        + " bytes",
                x,
                y,
                white,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "OBS: "
                        + formatSequence(
                        AiDebugState.lastObservationSequenceId
                )
                        + "  ACT: "
                        + formatSequence(
                        AiDebugState.lastActionSequenceId
                ),
                x,
                y,
                white,
                true
        );

        y += lineHeight + 2;

        if (AiDebugState.tcpEnabled
                && AiDebugState.lastProtocolAction != null) {
            graphics.text(
                    client.font,
                    "Action: "
                            + AiDebugState.lastProtocolAction,
                    x,
                    y,
                    green,
                    true
            );

            return;
        }

        AiAction action =
                AiDebugState.lastAction;

        if (action == null
                || action.buttons() == null
                || action.camera() == null) {
            graphics.text(
                    client.font,
                    "Action: waiting...",
                    x,
                    y,
                    yellow,
                    true
            );

            return;
        }

        graphics.text(
                client.font,
                "F:"
                        + action.buttons().forward()
                        + " B:"
                        + action.buttons().back()
                        + " L:"
                        + action.buttons().left()
                        + " R:"
                        + action.buttons().right(),
                x,
                y,
                white,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "J:"
                        + action.buttons().jump()
                        + " Sneak:"
                        + action.buttons().sneak()
                        + " Sprint:"
                        + action.buttons().sprinting(),
                x,
                y,
                white,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                "Attack:"
                        + action.buttons().attack()
                        + " Use:"
                        + action.buttons().use()
                        + " INV:"
                        + action.buttons().inventory(),
                x,
                y,
                white,
                true
        );

        y += lineHeight;

        graphics.text(
                client.font,
                String.format(
                        "Yaw: %.2f  Pitch: %.2f",
                        action.camera().yawDelta(),
                        action.camera().pitchDelta()
                ),
                x,
                y,
                white,
                true
        );
    }

    private static String formatSequence(
            long value
    ) {
        return value >= 0
                ? Long.toUnsignedString(
                value
        )
                : "---";
    }
}
