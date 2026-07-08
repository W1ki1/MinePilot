package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.Map;

public record GameEventSnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        String eventType,
        String source,
        Map<String, Object> data
) {
}

record PendingGameEvent(
        String eventType,
        String source,
        Map<String, Object> data
) {
}
