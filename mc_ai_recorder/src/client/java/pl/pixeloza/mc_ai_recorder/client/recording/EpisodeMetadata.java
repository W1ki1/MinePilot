package pl.pixeloza.mc_ai_recorder.client.recording;

public record EpisodeMetadata(
        String episodeName,
        String startedAt,
        String endedAt,
        String status,
        long totalTicks,
        String minecraftVersion,
        String modVersion
) {
}