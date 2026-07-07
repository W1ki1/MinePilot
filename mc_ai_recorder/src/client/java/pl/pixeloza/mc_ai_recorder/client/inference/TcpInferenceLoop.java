package pl.pixeloza.mc_ai_recorder.client.inference;

import pl.pixeloza.mc_ai_recorder.client.hud.AiDebugState;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class TcpInferenceLoop {
    private static final String SERVER_HOST = "192.168.0.11";
    private static final int SERVER_PORT = 5005;

    // Jedna predykcja co 4 ticki, czyli około 5 predykcji/s.
    private static final int INFER_EVERY_TICKS = 4;

    private final TcpFrameCapture frameCapture = new TcpFrameCapture();

    private final InferenceClient inferenceClient =
            new InferenceClient(SERVER_HOST, SERVER_PORT);

    private boolean enabled = false;
    private long tick = 0;

    private volatile boolean requestInFlight = false;
    private volatile AiAction lastAction;

    public void toggle() {
        enabled = !enabled;

        AiDebugState.tcpEnabled = enabled;

        Minecraft client = Minecraft.getInstance();

        if (!enabled) {
            requestInFlight = false;

            AiDebugState.connected = false;
            AiDebugState.lastAction = null;
            AiDebugState.lastRoundtripMs = -1;
            AiDebugState.jpegSize = 0;

            try {
                inferenceClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Zerujemy licznik po ponownym włączeniu.
            tick = 0;
        }

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal(
                            "[MC AI Recorder] TCP inference: "
                                    + (enabled ? "ON" : "OFF")
                    )
            );
        }
    }

    public void onClientTick(Minecraft client) {
        if (!enabled || client.player == null) {
            return;
        }

        tick++;

        if (tick % INFER_EVERY_TICKS != 0) {
            return;
        }

        // Nie wysyłamy następnej klatki, dopóki poprzednia
        // predykcja nie wróciła z serwera.
        if (requestInFlight) {
            return;
        }

        requestInFlight = true;

        frameCapture.captureJpegAsync(
                client,

                // Callback po poprawnym przechwyceniu JPEG.
                jpeg -> {
                    AiDebugState.jpegSize = jpeg.length;

                    Thread inferenceThread = new Thread(() -> {
                        long startNanos = System.nanoTime();

                        try {
                            lastAction = inferenceClient.predict(jpeg);

                            long elapsedNanos = System.nanoTime() - startNanos;
                            long roundtripMs = elapsedNanos / 1_000_000L;

                            AiDebugState.lastRoundtripMs = roundtripMs;
                            AiDebugState.lastInferenceMs = roundtripMs;
                            AiDebugState.lastAction = lastAction;
                            AiDebugState.connected = true;

                            if (lastAction != null
                                    && lastAction.buttons() != null
                                    && lastAction.camera() != null) {

                                System.out.println(
                                        "[MC AI Recorder] TCP action"
                                                + " F=" + lastAction.buttons().forward()
                                                + " J=" + lastAction.buttons().jump()
                                                + " ATK=" + lastAction.buttons().attack()
                                                + " yaw=" + lastAction.camera().yawDelta()
                                                + " pitch=" + lastAction.camera().pitchDelta()
                                                + " RTT=" + roundtripMs + "ms"
                                );
                            }
                        } catch (IOException | RuntimeException e) {
                            AiDebugState.connected = false;

                            System.out.println(
                                    "[MC AI Recorder] TCP inference error: "
                                            + e.getMessage()
                            );

                            e.printStackTrace();
                        } finally {
                            requestInFlight = false;
                        }
                    }, "MC-AI-TCP-Inference");

                    inferenceThread.setDaemon(true);
                    inferenceThread.start();
                },

                // Callback, gdy przechwycenie obrazu się nie uda.
                error -> {
                    AiDebugState.connected = false;
                    requestInFlight = false;

                    System.out.println(
                            "[MC AI Recorder] Frame capture error: "
                                    + error.getMessage()
                    );

                    error.printStackTrace();
                }
        );
    }

    public AiAction getLastAction() {
        return lastAction;
    }
}