package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.List;

public record EpisodeMetadata(
        int schemaVersion,
        String episodeId,
        String recorderVersion,
        String minecraftVersion,
        String fabricLoaderVersion,
        String registryHash,
        String startedAt,
        String endedAt,
        String status,
        String terminationReason,
        String frameFormat,
        int frameWidth,
        int frameHeight,
        int captureEveryTicks,
        boolean worldSeedRecorded,
        long totalTicks,
        List<String> tags,
        String notes
) {
}
