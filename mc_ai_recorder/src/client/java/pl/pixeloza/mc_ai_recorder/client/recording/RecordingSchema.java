package pl.pixeloza.mc_ai_recorder.client.recording;

public final class RecordingSchema {
    public static final int SCHEMA_VERSION = 2;
    public static final int PROTOCOL_VERSION = 2;

    public static final String RECORDER_VERSION = "0.5.0";
    public static final String MINECRAFT_VERSION = "26.1.2";
    public static final String FABRIC_LOADER_VERSION = "0.19.3";

    public static final String FRAME_FORMAT = "PNG";
    public static final int FRAME_WIDTH = 224;
    public static final int FRAME_HEIGHT = 224;
    public static final int CAPTURE_EVERY_TICKS = 1;

    public static final int WORLD_RADIUS_HORIZONTAL = 5;
    public static final int WORLD_RADIUS_VERTICAL = 3;
    public static final int WORLD_SNAPSHOT_MAX_INTERVAL_TICKS = 5;

    public static final int NEARBY_ENTITY_LIMIT = 16;
    public static final double NEARBY_ENTITY_RADIUS = 16.0;

    private RecordingSchema() {
    }
}
