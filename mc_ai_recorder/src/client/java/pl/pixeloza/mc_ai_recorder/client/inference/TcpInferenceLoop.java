package pl.pixeloza.mc_ai_recorder.client.inference;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import pl.pixeloza.mc_ai_recorder.client.hud.AiDebugState;
import pl.pixeloza.mc_ai_recorder.client.recording.RuntimeObservationFactory;

import java.io.IOException;

public class TcpInferenceLoop {
    private static final String SERVER_HOST =
            "192.168.0.11";

    private static final int SERVER_PORT =
            5005;

    private static final int INFER_EVERY_TICKS =
            2;

    private static final long HEARTBEAT_INTERVAL_MS =
            1_000L;

    private static final long ACTION_TIMEOUT_MS =
            750L;

    private static final long RECONNECT_INTERVAL_MS =
            1_000L;

    private final TcpFrameCapture frameCapture =
            new TcpFrameCapture();

    private final RuntimeObservationFactory observationFactory =
            new RuntimeObservationFactory();

    private final ProtocolV2Client protocolClient =
            new ProtocolV2Client(
                    SERVER_HOST,
                    SERVER_PORT
            );

    private volatile boolean enabled = false;
    private volatile boolean requestInFlight = false;

    private volatile AiAction lastAction = null;
    private volatile Long lastAppliedActionSequenceId = null;

    private volatile long lastSuccessfulMessageMs = 0L;
    private volatile long lastActionReceivedMs = 0L;
    private volatile long lastReconnectAttemptMs = 0L;

    private long tick = 0L;

    public void toggle() {
        enabled = !enabled;

        AiDebugState.tcpEnabled =
                enabled;

        Minecraft client =
                Minecraft.getInstance();

        if (enabled) {
            tick = 0L;
            lastAction = null;
            lastAppliedActionSequenceId = null;
            lastSuccessfulMessageMs = 0L;
            lastActionReceivedMs = 0L;

            AiDebugState.connected = false;
            AiDebugState.protocolState =
                    "CONNECTING";

            AiDebugState.lastProtocolAction =
                    "SAFE_IDLE";

            startConnect();
        } else {
            lastAction = null;
            lastAppliedActionSequenceId = null;

            AiDebugState.connected = false;
            AiDebugState.protocolState =
                    "OFF";

            AiDebugState.lastProtocolAction =
                    null;

            AiDebugState.lastAction =
                    null;

            AiDebugState.lastRoundtripMs =
                    -1L;

            AiDebugState.lastInferenceMs =
                    -1L;

            AiDebugState.jpegSize =
                    0;

            AiDebugState.lastObservationSequenceId =
                    -1L;

            AiDebugState.lastActionSequenceId =
                    -1L;

            closeInBackground();
        }

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal(
                            "[MC AI Recorder] TCP protocol v2: "
                                    + (
                                    enabled
                                            ? "ON"
                                            : "OFF"
                            )
                    )
            );
        }
    }

    public void onClientTick(
            Minecraft client
    ) {
        if (!enabled) {
            return;
        }

        tick++;

        long now =
                System.currentTimeMillis();

        enforceActionTimeout(
                now
        );

        if (requestInFlight) {
            return;
        }

        boolean canObserve =
                client.player != null
                        && client.level != null
                        && client.options != null;

        if (canObserve
                && tick % INFER_EVERY_TICKS == 0) {
            sendObservation(
                    client
            );
            return;
        }

        if (protocolClient.isConnected()
                && now - lastSuccessfulMessageMs
                >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat();
            return;
        }

        if (!protocolClient.isConnected()
                && now - lastReconnectAttemptMs
                >= RECONNECT_INTERVAL_MS) {
            startConnect();
        }
    }

    public AiAction getLastAction() {
        return lastAction;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void sendObservation(
            Minecraft client
    ) {
        JsonObject metadata;

        try {
            metadata =
                    observationFactory.create(
                            client,
                            tick,
                            lastAppliedActionSequenceId
                    );
        } catch (RuntimeException e) {
            handleFailure(
                    "Observation metadata error",
                    e
            );
            return;
        }

        requestInFlight = true;

        frameCapture.captureJpegAsync(
                client,
                jpeg -> {
                    AiDebugState.jpegSize =
                            jpeg.length;

                    Thread worker =
                            new Thread(
                                    () -> exchangeObservation(
                                            metadata,
                                            jpeg
                                    ),
                                    "MinePilot-ProtocolV2-Observation"
                            );

                    worker.setDaemon(
                            true
                    );

                    worker.start();
                },
                error -> {
                    requestInFlight = false;

                    handleFailure(
                            "Frame capture error",
                            error
                    );
                }
        );
    }

    private void exchangeObservation(
            JsonObject metadata,
            byte[] jpeg
    ) {
        long startNanos =
                System.nanoTime();

        try {
            ProtocolV2Client.ActionResponse response =
                    protocolClient.exchangeObservation(
                            metadata,
                            jpeg
                    );

            if (!enabled) {
                protocolClient.close();
                return;
            }

            ProtocolAction action =
                    response.action();

            if (!action.isSafeIdle()) {
                throw new IOException(
                        "Stage 4B accepts only SYSTEM/SAFE_IDLE, got "
                                + action.actionType()
                );
            }

            long roundtripMs =
                    (System.nanoTime()
                            - startNanos)
                            / 1_000_000L;

            long now =
                    System.currentTimeMillis();

            lastAction = null;
            lastActionReceivedMs = now;
            lastSuccessfulMessageMs = now;
            lastAppliedActionSequenceId =
                    response.actionEnvelopeSequenceId();

            AiDebugState.connected = true;
            AiDebugState.protocolState =
                    "READY";

            AiDebugState.lastProtocolAction =
                    "SAFE_IDLE";

            AiDebugState.lastObservationSequenceId =
                    response.observationSequenceId();

            AiDebugState.lastActionSequenceId =
                    response.actionEnvelopeSequenceId();

            AiDebugState.lastRoundtripMs =
                    roundtripMs;

            AiDebugState.lastInferenceMs =
                    roundtripMs;

            AiDebugState.lastAction =
                    null;

            System.out.println(
                    "[MC AI Recorder] Protocol v2 SAFE_IDLE"
                            + " observation="
                            + response.observationSequenceId()
                            + " action="
                            + response.actionEnvelopeSequenceId()
                            + " jpeg="
                            + jpeg.length
                            + "B RTT="
                            + roundtripMs
                            + "ms"
            );
        } catch (IOException | RuntimeException e) {
            handleFailure(
                    "Protocol v2 observation error",
                    e
            );
        } finally {
            requestInFlight = false;
        }
    }

    private void sendHeartbeat() {
        requestInFlight = true;

        Thread worker =
                new Thread(
                        () -> {
                            try {
                                protocolClient.heartbeat();

                                if (!enabled) {
                                    protocolClient.close();
                                    return;
                                }

                                lastSuccessfulMessageMs =
                                        System.currentTimeMillis();

                                AiDebugState.connected =
                                        true;

                                AiDebugState.protocolState =
                                        "READY";
                            } catch (IOException | RuntimeException e) {
                                handleFailure(
                                        "Protocol v2 heartbeat error",
                                        e
                                );
                            } finally {
                                requestInFlight = false;
                            }
                        },
                        "MinePilot-ProtocolV2-Heartbeat"
                );

        worker.setDaemon(
                true
        );

        worker.start();
    }

    private void startConnect() {
        if (!enabled
                || requestInFlight) {
            return;
        }

        requestInFlight = true;
        lastReconnectAttemptMs =
                System.currentTimeMillis();

        AiDebugState.protocolState =
                "CONNECTING";

        Thread worker =
                new Thread(
                        () -> {
                            try {
                                protocolClient.connect();

                                if (!enabled) {
                                    protocolClient.close();
                                    return;
                                }

                                lastSuccessfulMessageMs =
                                        System.currentTimeMillis();

                                AiDebugState.connected =
                                        true;

                                AiDebugState.protocolState =
                                        "READY";

                                System.out.println(
                                        "[MC AI Recorder] Protocol v2 connected to "
                                                + SERVER_HOST
                                                + ':'
                                                + SERVER_PORT
                                );
                            } catch (IOException | RuntimeException e) {
                                handleFailure(
                                        "Protocol v2 connection error",
                                        e
                                );
                            } finally {
                                requestInFlight = false;
                            }
                        },
                        "MinePilot-ProtocolV2-Connect"
                );

        worker.setDaemon(
                true
        );

        worker.start();
    }

    private void enforceActionTimeout(
            long now
    ) {
        if (lastActionReceivedMs <= 0L) {
            return;
        }

        if (now - lastActionReceivedMs
                <= ACTION_TIMEOUT_MS) {
            return;
        }

        lastAction = null;

        AiDebugState.lastAction =
                null;

        AiDebugState.lastProtocolAction =
                "TIMEOUT_SAFE_IDLE";
    }

    private void handleFailure(
            String context,
            Throwable error
    ) {
        lastAction = null;
        lastAppliedActionSequenceId = null;

        AiDebugState.connected = false;
        AiDebugState.protocolState =
                "RECONNECTING";

        AiDebugState.lastProtocolAction =
                "ERROR_SAFE_IDLE";

        AiDebugState.lastAction =
                null;

        protocolClient.closeSilently();

        System.out.println(
                "[MC AI Recorder] "
                        + context
                        + ": "
                        + error.getMessage()
        );
    }

    private void closeInBackground() {
        Thread worker =
                new Thread(
                        protocolClient::close,
                        "MinePilot-ProtocolV2-Close"
                );

        worker.setDaemon(
                true
        );

        worker.start();
    }
}
