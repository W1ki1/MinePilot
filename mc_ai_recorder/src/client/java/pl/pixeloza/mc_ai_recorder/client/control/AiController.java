package pl.pixeloza.mc_ai_recorder.client.control;

import pl.pixeloza.mc_ai_recorder.client.hud.AiDebugState;

import pl.pixeloza.mc_ai_recorder.client.inference.AiAction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class AiController {
    private static final float CAMERA_SCALE = 1.0f;
    private static final float MAX_CAMERA_DELTA = 5.0f;

    private boolean enabled = false;

    public void toggle() {
        enabled = !enabled;

        // Aktualizacja danych wyświetlanych w HUD.
        AiDebugState.aiControlEnabled = enabled;

        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal(
                            "[MC AI Recorder] AI control: "
                                    + (enabled ? "ON" : "OFF")
                    )
            );
        }

        if (!enabled) {
            releaseKeys(client);
        }
    }

    public void onClientTick(Minecraft client, AiAction action) {
        if (!enabled
                || client.player == null
                || action == null
                || action.buttons() == null) {
            return;
        }

        AiAction.Buttons buttons = action.buttons();

        client.options.keyUp.setDown(buttons.forward());
        client.options.keyDown.setDown(buttons.back());
        client.options.keyLeft.setDown(buttons.left());
        client.options.keyRight.setDown(buttons.right());

        client.options.keyJump.setDown(buttons.jump());
        client.options.keyShift.setDown(buttons.sneak());
        client.options.keySprint.setDown(buttons.sprinting());

        client.options.keyAttack.setDown(buttons.attack());
        client.options.keyUse.setDown(buttons.use());

        if (action.camera() != null) {
            applyCamera(client, action.camera());
        }
    }

    private void applyCamera(Minecraft client, AiAction.Camera camera) {
        float yawDelta = clamp(
                camera.yawDelta() * CAMERA_SCALE,
                -MAX_CAMERA_DELTA,
                MAX_CAMERA_DELTA
        );

        float pitchDelta = clamp(
                camera.pitchDelta() * CAMERA_SCALE,
                -MAX_CAMERA_DELTA,
                MAX_CAMERA_DELTA
        );

        float newYaw = client.player.getYRot() + yawDelta;

        float newPitch = clamp(
                client.player.getXRot() + pitchDelta,
                -90.0f,
                90.0f
        );

        client.player.setYRot(newYaw);
        client.player.setXRot(newPitch);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void releaseKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);

        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);

        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
    }
}