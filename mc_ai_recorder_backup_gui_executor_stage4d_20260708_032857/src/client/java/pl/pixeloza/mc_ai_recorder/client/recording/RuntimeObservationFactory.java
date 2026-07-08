package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

public final class RuntimeObservationFactory {
    private static final int FRAME_WIDTH =
            224;

    private static final int FRAME_HEIGHT =
            224;

    private static final int JPEG_QUALITY =
            80;

    private final Gson gson =
            new Gson();

    public JsonObject create(
            Minecraft client,
            long tick,
            Long lastAppliedActionSequenceId,
            boolean controlEnabled
    ) {
        WorldStepSnapshot worldStep =
                SnapshotFactory.createWorldStep(
                        client,
                        0,
                        tick,
                        "",
                        null,
                        null,
                        null,
                        0.0f,
                        0.0f,
                        null
                );

        JsonObject observation =
                gson.toJsonTree(
                        worldStep
                ).getAsJsonObject();

        observation.remove(
                "recordType"
        );

        observation.remove(
                "sequenceId"
        );

        observation.remove(
                "frame"
        );

        observation.remove(
                "action"
        );

        observation.addProperty(
                "protocolVersion",
                2
        );

        observation.addProperty(
                "schemaVersion",
                2
        );

        observation.addProperty(
                "controlEnabled",
                controlEnabled
        );

        if (lastAppliedActionSequenceId == null) {
            observation.add(
                    "lastAppliedActionSequenceId",
                    JsonNull.INSTANCE
            );
        } else {
            observation.addProperty(
                    "lastAppliedActionSequenceId",
                    lastAppliedActionSequenceId
            );
        }

        JsonObject frame =
                new JsonObject();

        frame.addProperty(
                "format",
                "JPEG"
        );

        frame.addProperty(
                "width",
                FRAME_WIDTH
        );

        frame.addProperty(
                "height",
                FRAME_HEIGHT
        );

        frame.addProperty(
                "quality",
                JPEG_QUALITY
        );

        observation.add(
                "frame",
                frame
        );

        return observation;
    }
}
