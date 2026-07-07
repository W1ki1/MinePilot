package pl.pixeloza.mc_ai_recorder.client.legacy;

import pl.pixeloza.mc_ai_recorder.client.recording.FrameCapture;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LiveFrameExporter {
    private static final int EXPORT_EVERY_TICKS = 5;

    private boolean enabled = false;
    private long tick = 0;
    private FrameCapture frameCapture;

    public void toggle() {
        if (enabled) {
            stop();
        } else {
            start();
        }
    }

    public void onClientTick(Minecraft client) {
        if (!enabled || client.player == null) {
            return;
        }

        tick++;

        if (tick % EXPORT_EVERY_TICKS != 0) {
            return;
        }

        try {
            frameCapture.capture(client, "live.png");
        } catch (IOException e) {
            sendMessage("ERROR exporting live frame: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    private void start() {
        try {
            tick = 0;

            Path liveDir = Path.of("G:/mc-ai-recorder/run/live");
            Files.createDirectories(liveDir);

            frameCapture = new FrameCapture(liveDir);
            enabled = true;

            sendMessage("Live export started");
            System.out.println("[MC AI Recorder] Live export started: " + liveDir);
        } catch (IOException e) {
            sendMessage("ERROR starting live export: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stop() {
        enabled = false;

        if (frameCapture != null) {
            try {
                frameCapture.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            frameCapture = null;
        }

        sendMessage("Live export stopped");
        System.out.println("[MC AI Recorder] Live export stopped");
    }

    private void sendMessage(String message) {
        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal("[MC AI Recorder] " + message)
            );
        }
    }
}