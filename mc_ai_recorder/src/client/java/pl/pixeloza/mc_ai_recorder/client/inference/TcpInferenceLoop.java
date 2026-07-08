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

    private final ProtocolActionRouter actionRouter =
            new ProtocolActionRouter();

    private volatile boolean enabled = false;
    private volatile boolean requestInFlight = false;

    private volatile ProtocolAction lastProtocolAction =
            null;

    private volatile long pendingActionEnvelopeSequenceId =
            -1L;

    private volatile Long lastAppliedActionSequenceId =
            null;

    private volatile long actionExpiryTick =
            -1L;

    private volatile long lastSuccessfulMessageMs = 0L;
    private volatile long lastActionReceivedMs = 0L;
    private volatile long lastReconnectAttemptMs = 0L;

    private volatile long tick = 0L;
    private volatile boolean controlEnabled = false;

    public void toggle() {
        enabled = !enabled;

        AiDebugState.tcpEnabled =
                enabled;

        Minecraft client =
                Minecraft.getInstance();

        if (enabled) {
            resetRuntimeState();

            AiDebugState.connected = false;
            AiDebugState.protocolState =
                    "CONNECTING";

            AiDebugState.lastProtocolAction =
                    "SAFE_IDLE";

            AiDebugState.lastRoute =
                    "CONNECTING_SAFE_IDLE";

            startConnect();
        } else {
            resetRuntimeState();

            AiDebugState.connected = false;
            AiDebugState.protocolState =
                    "OFF";

            AiDebugState.lastProtocolAction =
                    null;

            AiDebugState.lastRoute =
                    "OFF";

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
            Minecraft client,
            boolean aiControlEnabled
    ) {
        controlEnabled =
                aiControlEnabled;

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

    public AiAction resolveAction(
            Minecraft client,
            boolean aiControlEnabled
    ) {
        if (!enabled) {
            return null;
        }

        enforceActionTimeout(
                System.currentTimeMillis()
        );

        ProtocolAction action =
                lastProtocolAction;

        ProtocolActionRouter.RoutedAction routed =
                actionRouter.resolve(
                        client,
                        action,
                        aiControlEnabled
                );

        AiDebugState.lastRoute =
                routed.route().name();

        AiDebugState.lastAction =
                routed.action();

        if (action != null) {
            AiDebugState.lastProtocolAction =
                    action.summary();
        }

        if (routed.applied()
                && pendingActionEnvelopeSequenceId >= 0L) {
            lastAppliedActionSequenceId =
                    pendingActionEnvelopeSequenceId;
        }

        return routed.action();
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
                            lastAppliedActionSequenceId,
                            controlEnabled
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

            action.validate();

            long roundtripMs =
                    (System.nanoTime()
                            - startNanos)
                            / 1_000_000L;

            long now =
                    System.currentTimeMillis();

            lastProtocolAction =
                    action;

            pendingActionEnvelopeSequenceId =
                    response.actionEnvelopeSequenceId();

            actionExpiryTick =
                    tick
                            + action.effectiveValidForTicks();

            lastActionReceivedMs = now;
            lastSuccessfulMessageMs = now;

            AiDebugState.connected = true;
            AiDebugState.protocolState =
                    "READY";

            AiDebugState.lastProtocolAction =
                    action.summary();

            AiDebugState.lastObservationSequenceId =
                    response.observationSequenceId();

            AiDebugState.lastActionSequenceId =
                    response.actionEnvelopeSequenceId();

            AiDebugState.lastRoundtripMs =
                    roundtripMs;

            AiDebugState.lastInferenceMs =
                    roundtripMs;

            System.out.println(
                    "[MC AI Recorder] Protocol v2 "
                            + action.summary()
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
        if (lastProtocolAction == null) {
            return;
        }

        boolean expiredByTime =
                lastActionReceivedMs > 0L
                        && now - lastActionReceivedMs
                        > ACTION_TIMEOUT_MS;

        boolean expiredByTick =
                actionExpiryTick >= 0L
                        && tick > actionExpiryTick;

        if (!expiredByTime
                && !expiredByTick) {
            return;
        }

        lastProtocolAction = null;
        pendingActionEnvelopeSequenceId = -1L;
        actionExpiryTick = -1L;

        AiDebugState.lastAction =
                null;

        AiDebugState.lastProtocolAction =
                "TIMEOUT_SAFE_IDLE";

        AiDebugState.lastRoute =
                "TIMEOUT_SAFE_IDLE";
    }

    private void handleFailure(
            String context,
            Throwable error
    ) {
        lastProtocolAction = null;
        pendingActionEnvelopeSequenceId = -1L;
        lastAppliedActionSequenceId = null;
        actionExpiryTick = -1L;

        AiDebugState.connected = false;
        AiDebugState.protocolState =
                "RECONNECTING";

        AiDebugState.lastProtocolAction =
                "ERROR_SAFE_IDLE";

        AiDebugState.lastRoute =
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

    private void resetRuntimeState() {
        tick = 0L;
        lastProtocolAction = null;
        pendingActionEnvelopeSequenceId = -1L;
        lastAppliedActionSequenceId = null;
        actionExpiryTick = -1L;
        lastSuccessfulMessageMs = 0L;
        lastActionReceivedMs = 0L;
        lastReconnectAttemptMs = 0L;
        controlEnabled = false;
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
