package pl.pixeloza.mc_ai_recorder.client.legacy;

import pl.pixeloza.mc_ai_recorder.client.inference.AiAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ActionReader {
    private final Gson gson = new GsonBuilder().create();
    private final Path actionFile = Path.of("G:/mc-ai-recorder/run/live/action.json");

    private long tick = 0;
    private AiAction lastAction;

    public AiAction getLastAction() {
        return lastAction;
    }

    public void onClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        tick++;

        if (tick % 4 != 0) {
            return;
        }

        if (!Files.exists(actionFile)) {
            return;
        }

        try {
            String json = Files.readString(actionFile);
            lastAction = gson.fromJson(json, AiAction.class);

            if (lastAction != null && lastAction.buttons() != null && lastAction.camera() != null) {
                showAction(client, lastAction);
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[MC AI Recorder] Failed to read action.json: " + e.getMessage());
        }
    }

    private void showAction(Minecraft client, AiAction action) {
        String text = String.format(
                "AI F:%s J:%s S:%s ATK:%s Y:%.2f P:%.2f",
                action.buttons().forward(),
                action.buttons().jump(),
                action.buttons().sprinting(),
                action.buttons().attack(),
                action.camera().yawDelta(),
                action.camera().pitchDelta()
        );

        client.player.sendSystemMessage(Component.literal(text));
    }
}