package pl.pixeloza.mc_ai_recorder.client.hud;

import pl.pixeloza.mc_ai_recorder.client.inference.AiAction;

public final class AiDebugState {
    private AiDebugState() {
    }

    // HUD jest domyślnie widoczny.
    public static volatile boolean hudVisible = true;

    public static volatile boolean tcpEnabled = false;
    public static volatile boolean aiControlEnabled = false;
    public static volatile boolean connected = false;

    public static volatile AiAction lastAction = null;

    public static volatile long lastInferenceMs = -1;
    public static volatile long lastRoundtripMs = -1;

    public static volatile int jpegSize = 0;
}