package pl.pixeloza.mc_ai_recorder.client.inference;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ProtocolV2Client implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS =
            2_000;

    private static final int READ_TIMEOUT_MS =
            3_500;

    private static final String CLIENT_VERSION =
            "0.10.0";

    private static final String MINECRAFT_VERSION =
            "26.1.2";

    private final String host;
    private final int port;

    private final Gson gson =
            new Gson();

    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    private long nextSequenceId = 1;
    private JsonObject serverAck;

    public ProtocolV2Client(
            String host,
            int port
    ) {
        this.host = host;
        this.port = port;
    }

    public synchronized void connect()
            throws IOException {
        if (isConnected()) {
            return;
        }

        closeTransport();

        Socket newSocket =
                new Socket();

        try {
            newSocket.connect(
                    new InetSocketAddress(
                            host,
                            port
                    ),
                    CONNECT_TIMEOUT_MS
            );

            newSocket.setTcpNoDelay(
                    true
            );

            newSocket.setKeepAlive(
                    true
            );

            newSocket.setSoTimeout(
                    READ_TIMEOUT_MS
            );

            socket = newSocket;

            input = new DataInputStream(
                    socket.getInputStream()
            );

            output = new DataOutputStream(
                    socket.getOutputStream()
            );

            performHandshake();
        } catch (IOException e) {
            try {
                newSocket.close();
            } catch (IOException ignored) {
            }

            closeTransport();
            throw e;
        }
    }

    public synchronized ActionResponse exchangeObservation(
            JsonObject metadata,
            byte[] jpegBytes
    ) throws IOException {
        ensureConnected();

        long observationSequenceId =
                nextSequenceId();

        try {
            ProtocolV2.writeObservationEnvelope(
                    output,
                    observationSequenceId,
                    metadata,
                    jpegBytes
            );

            ProtocolV2.Envelope response =
                    ProtocolV2.readEnvelope(
                            input
                    );

            throwIfError(
                    response
            );

            if (response.messageType()
                    != ProtocolV2.MessageType.ACTION) {
                throw new IOException(
                        "Expected ACTION, received "
                                + response.messageType()
                );
            }

            JsonObject payload =
                    ProtocolV2.decodeJsonPayload(
                            response
                    );

            ProtocolAction action =
                    gson.fromJson(
                            payload,
                            ProtocolAction.class
                    );

            if (action == null) {
                throw new IOException(
                        "Backend returned an empty ACTION"
                );
            }

            if (action.protocolVersion()
                    != ProtocolV2.PROTOCOL_VERSION) {
                throw new IOException(
                        "ACTION protocolVersion mismatch"
                );
            }

            if (action.observationSequenceId()
                    != observationSequenceId) {
                throw new IOException(
                        "ACTION references observation "
                                + action.observationSequenceId()
                                + ", expected "
                                + observationSequenceId
                );
            }

            return new ActionResponse(
                    observationSequenceId,
                    response.sequenceId(),
                    action
            );
        } catch (SocketTimeoutException e) {
            closeTransport();

            throw new IOException(
                    "Backend response timeout",
                    e
            );
        } catch (IOException | RuntimeException e) {
            closeTransport();
            throw e;
        }
    }

    public synchronized long heartbeat()
            throws IOException {
        ensureConnected();

        long sequenceId =
                nextSequenceId();

        JsonObject heartbeat =
                new JsonObject();

        heartbeat.addProperty(
                "protocolVersion",
                ProtocolV2.PROTOCOL_VERSION
        );

        heartbeat.addProperty(
                "timestampMs",
                System.currentTimeMillis()
        );

        try {
            ProtocolV2.writeJsonEnvelope(
                    output,
                    ProtocolV2.MessageType.HEARTBEAT,
                    sequenceId,
                    heartbeat
            );

            ProtocolV2.Envelope response =
                    ProtocolV2.readEnvelope(
                            input
                    );

            throwIfError(
                    response
            );

            if (response.messageType()
                    != ProtocolV2.MessageType.HEARTBEAT) {
                throw new IOException(
                        "Expected HEARTBEAT response, received "
                                + response.messageType()
                );
            }

            ProtocolV2.decodeJsonPayload(
                    response
            );

            return response.sequenceId();
        } catch (SocketTimeoutException e) {
            closeTransport();

            throw new IOException(
                    "Heartbeat response timeout",
                    e
            );
        } catch (IOException | RuntimeException e) {
            closeTransport();
            throw e;
        }
    }

    public synchronized boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && !socket.isClosed()
                && !socket.isInputShutdown()
                && !socket.isOutputShutdown()
                && input != null
                && output != null;
    }

    public synchronized JsonObject getServerAck() {
        return serverAck == null
                ? null
                : serverAck.deepCopy();
    }

    private void performHandshake()
            throws IOException {
        long helloSequenceId =
                nextSequenceId();

        JsonObject hello =
                new JsonObject();

        hello.addProperty(
                "protocolVersion",
                ProtocolV2.PROTOCOL_VERSION
        );

        hello.addProperty(
                "schemaVersion",
                ProtocolV2.SCHEMA_VERSION
        );

        hello.addProperty(
                "client",
                "MinePilot-Mod"
        );

        hello.addProperty(
                "clientVersion",
                CLIENT_VERSION
        );

        hello.addProperty(
                "minecraftVersion",
                MINECRAFT_VERSION
        );

        JsonArray capabilities =
                new JsonArray();

        capabilities.add(
                "OBSERVATION_JPEG"
        );

        capabilities.add(
                "HYBRID_SEMANTIC_STATE"
        );

        capabilities.add(
                "ACTION_SAFE_IDLE"
        );

        capabilities.add(
                "ACTION_WORLD"
        );

        capabilities.add(
                "ACTION_GUI"
        );

        capabilities.add(
                "HEARTBEAT"
        );

        hello.add(
                "capabilities",
                capabilities
        );

        ProtocolV2.writeJsonEnvelope(
                output,
                ProtocolV2.MessageType.HELLO,
                helloSequenceId,
                hello
        );

        ProtocolV2.Envelope response =
                ProtocolV2.readEnvelope(
                        input
                );

        throwIfError(
                response
        );

        if (response.messageType()
                != ProtocolV2.MessageType.ACK) {
            throw new IOException(
                    "Expected ACK, received "
                            + response.messageType()
            );
        }

        if (response.sequenceId()
                != helloSequenceId) {
            throw new IOException(
                    "ACK sequence mismatch"
            );
        }

        JsonObject ack =
                ProtocolV2.decodeJsonPayload(
                        response
                );

        if (!ack.has("protocolVersion")
                || ack.get("protocolVersion")
                .getAsInt()
                != ProtocolV2.PROTOCOL_VERSION) {
            throw new IOException(
                    "ACK protocolVersion mismatch"
            );
        }

        if (!ack.has("schemaVersion")
                || ack.get("schemaVersion")
                .getAsInt()
                != ProtocolV2.SCHEMA_VERSION) {
            throw new IOException(
                    "ACK schemaVersion mismatch"
            );
        }

        serverAck = ack.deepCopy();
    }

    private void ensureConnected()
            throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    private void throwIfError(
            ProtocolV2.Envelope envelope
    ) throws IOException {
        if (envelope.messageType()
                != ProtocolV2.MessageType.ERROR) {
            return;
        }

        JsonObject payload =
                ProtocolV2.decodeJsonPayload(
                        envelope
                );

        String code =
                payload.has("code")
                        ? payload.get("code")
                        .getAsString()
                        : "UNKNOWN";

        String message =
                payload.has("message")
                        ? payload.get("message")
                        .getAsString()
                        : "Backend returned ERROR";

        throw new IOException(
                "Backend error "
                        + code
                        + ": "
                        + message
        );
    }

    private long nextSequenceId() {
        long result =
                nextSequenceId;

        nextSequenceId++;

        if (nextSequenceId
                > 0xFFFF_FFFFL) {
            nextSequenceId = 1;
        }

        return result;
    }

    @Override
    public synchronized void close() {
        if (isConnected()) {
            try {
                JsonObject goodbye =
                        new JsonObject();

                goodbye.addProperty(
                        "protocolVersion",
                        ProtocolV2.PROTOCOL_VERSION
                );

                goodbye.addProperty(
                        "reason",
                        "CLIENT_DISABLED"
                );

                ProtocolV2.writeJsonEnvelope(
                        output,
                        ProtocolV2.MessageType.GOODBYE,
                        nextSequenceId(),
                        goodbye
                );
            } catch (IOException ignored) {
            }
        }

        closeTransport();
    }

    public synchronized void closeSilently() {
        closeTransport();
    }

    private void closeTransport() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        socket = null;
        input = null;
        output = null;
        serverAck = null;
    }

    public record ActionResponse(
            long observationSequenceId,
            long actionEnvelopeSequenceId,
            ProtocolAction action
    ) {
    }
}
