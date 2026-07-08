package pl.pixeloza.mc_ai_recorder.client.inference;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ProtocolV2 {
    public static final int PROTOCOL_VERSION = 2;
    public static final int SCHEMA_VERSION = 2;

    public static final int FLAG_JSON = 1;
    public static final int FLAG_BINARY_TAIL = 1 << 1;

    public static final int MAX_PAYLOAD_BYTES =
            16 * 1024 * 1024;

    private static final byte[] MAGIC =
            new byte[]{'M', 'P', 'L', 'T'};

    private static final Gson GSON =
            new Gson();

    private ProtocolV2() {
    }

    public enum MessageType {
        HELLO(1),
        ACK(2),
        OBSERVATION(3),
        ACTION(4),
        HEARTBEAT(5),
        ERROR(6),
        GOODBYE(7);

        private final int code;

        MessageType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static MessageType fromCode(
                int code
        ) throws IOException {
            for (MessageType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }

            throw new IOException(
                    "Unknown protocol message type: "
                            + code
            );
        }
    }

    public record Envelope(
            MessageType messageType,
            long sequenceId,
            int flags,
            byte[] payload
    ) {
        public boolean hasFlag(
                int flag
        ) {
            return (flags & flag) == flag;
        }
    }

    public static void writeJsonEnvelope(
            DataOutputStream output,
            MessageType messageType,
            long sequenceId,
            JsonObject payload
    ) throws IOException {
        byte[] payloadBytes =
                GSON.toJson(payload)
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        writeEnvelope(
                output,
                messageType,
                sequenceId,
                FLAG_JSON,
                payloadBytes
        );
    }

    public static void writeObservationEnvelope(
            DataOutputStream output,
            long sequenceId,
            JsonObject metadata,
            byte[] jpegBytes
    ) throws IOException {
        if (jpegBytes == null
                || jpegBytes.length == 0) {
            throw new IOException(
                    "Observation JPEG is empty"
            );
        }

        JsonObject metadataCopy =
                metadata.deepCopy();

        JsonObject frame;

        if (metadataCopy.has("frame")
                && metadataCopy.get("frame")
                .isJsonObject()) {
            frame = metadataCopy
                    .getAsJsonObject("frame");
        } else {
            frame = new JsonObject();
            metadataCopy.add(
                    "frame",
                    frame
            );
        }

        frame.addProperty(
                "length",
                jpegBytes.length
        );

        byte[] metadataBytes =
                GSON.toJson(metadataCopy)
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        ByteArrayOutputStream payloadBuffer =
                new ByteArrayOutputStream(
                        4
                                + metadataBytes.length
                                + jpegBytes.length
                );

        try (DataOutputStream payloadOutput =
                     new DataOutputStream(
                             payloadBuffer
                     )) {
            payloadOutput.writeInt(
                    metadataBytes.length
            );

            payloadOutput.write(
                    metadataBytes
            );

            payloadOutput.write(
                    jpegBytes
            );
        }

        writeEnvelope(
                output,
                MessageType.OBSERVATION,
                sequenceId,
                FLAG_JSON
                        | FLAG_BINARY_TAIL,
                payloadBuffer.toByteArray()
        );
    }

    public static void writeEnvelope(
            DataOutputStream output,
            MessageType messageType,
            long sequenceId,
            int flags,
            byte[] payload
    ) throws IOException {
        validateSequenceId(
                sequenceId
        );

        if (payload.length
                > MAX_PAYLOAD_BYTES) {
            throw new IOException(
                    "Protocol payload exceeds "
                            + MAX_PAYLOAD_BYTES
                            + " bytes: "
                            + payload.length
            );
        }

        output.write(
                MAGIC
        );

        output.writeByte(
                PROTOCOL_VERSION
        );

        output.writeByte(
                messageType.code()
        );

        output.writeShort(
                flags
        );

        output.writeInt(
                payload.length
        );

        output.writeInt(
                (int) sequenceId
        );

        output.write(
                payload
        );

        output.flush();
    }

    public static Envelope readEnvelope(
            DataInputStream input
    ) throws IOException {
        byte[] magic =
                new byte[4];

        try {
            input.readFully(
                    magic
            );
        } catch (EOFException e) {
            throw new EOFException(
                    "Connection closed before protocol header"
            );
        }

        if (!Arrays.equals(
                MAGIC,
                magic
        )) {
            throw new IOException(
                    "Invalid protocol magic"
            );
        }

        int version =
                input.readUnsignedByte();

        if (version
                != PROTOCOL_VERSION) {
            throw new IOException(
                    "Unsupported protocol version: "
                            + version
                            + ", expected "
                            + PROTOCOL_VERSION
            );
        }

        MessageType messageType =
                MessageType.fromCode(
                        input.readUnsignedByte()
                );

        int flags =
                input.readUnsignedShort();

        long payloadLength =
                Integer.toUnsignedLong(
                        input.readInt()
                );

        long sequenceId =
                Integer.toUnsignedLong(
                        input.readInt()
                );

        if (payloadLength
                > MAX_PAYLOAD_BYTES) {
            throw new IOException(
                    "Incoming protocol payload exceeds limit: "
                            + payloadLength
            );
        }

        byte[] payload =
                new byte[(int) payloadLength];

        input.readFully(
                payload
        );

        return new Envelope(
                messageType,
                sequenceId,
                flags,
                payload
        );
    }

    public static JsonObject decodeJsonPayload(
            Envelope envelope
    ) throws IOException {
        if (!envelope.hasFlag(
                FLAG_JSON
        )) {
            throw new IOException(
                    "Envelope does not contain JSON"
            );
        }

        if (envelope.hasFlag(
                FLAG_BINARY_TAIL
        )) {
            throw new IOException(
                    "Envelope contains a binary tail"
            );
        }

        try {
            return GSON.fromJson(
                    new String(
                            envelope.payload(),
                            StandardCharsets.UTF_8
                    ),
                    JsonObject.class
            );
        } catch (JsonParseException e) {
            throw new IOException(
                    "Invalid JSON protocol payload",
                    e
            );
        }
    }

    private static void validateSequenceId(
            long sequenceId
    ) throws IOException {
        if (sequenceId < 0
                || sequenceId > 0xFFFF_FFFFL) {
            throw new IOException(
                    "sequenceId does not fit uint32: "
                            + sequenceId
            );
        }
    }
}
