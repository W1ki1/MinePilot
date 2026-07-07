package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.List;
import java.util.Map;

public record WorldSnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        int revision,
        BlockPositionSnapshot origin,
        WorldSnapshotBounds bounds,
        List<WorldBlockSnapshot> blocks
) {
}

record WorldSnapshotBounds(
        int minDx,
        int maxDx,
        int minDy,
        int maxDy,
        int minDz,
        int maxDz
) {
}

record WorldBlockSnapshot(
        int dx,
        int dy,
        int dz,
        String blockId,
        Map<String, String> properties
) {
}
