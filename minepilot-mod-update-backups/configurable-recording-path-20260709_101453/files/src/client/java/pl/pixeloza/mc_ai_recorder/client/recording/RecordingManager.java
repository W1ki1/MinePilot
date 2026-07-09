package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecordingManager {
    private static final Path DATASET_ROOT =
            Path.of(
                    "G:/MinecraftAI/Recordings/minepilot_v2"
            );

    private final Gson gson =
            new GsonBuilder()
                    .serializeNulls()
                    .setPrettyPrinting()
                    .create();

    private final GameEventTracker gameEventTracker =
            new GameEventTracker();

    private boolean recording = false;

    private long tick = 0;
    private long nextSequenceId = 1;

    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private int lastSelectedSlot = -1;

    private int inventoryRevision = 0;
    private String lastInventoryFingerprint = null;

    private int worldSnapshotRevision = 0;
    private String lastWorldFingerprint = null;
    private long lastWorldSnapshotTick = Long.MIN_VALUE;

    private int containerRevision = 0;
    private Screen activeContainerScreen = null;
    private ContainerSourceSnapshot activeContainerSource = null;
    private String lastContainerFingerprint = null;

    private boolean pendingInitialScreenOpen = false;

    private JsonlWriter actionsWriter;
    private JsonlWriter guiActionsWriter;
    private JsonlWriter inventorySnapshotsWriter;
    private JsonlWriter worldSnapshotsWriter;
    private JsonlWriter containerSnapshotsWriter;
    private JsonlWriter gameEventsWriter;

    private FrameCapture frameCapture;

    private Path episodeDir;
    private Path framesDir;

    private String episodeName;
    private String startedAt;
    private String registryHash;

    public boolean isRecording() {
        return recording;
    }

    public long getCurrentTick() {
        return tick;
    }

    public synchronized long nextSequenceId() {
        return nextSequenceId++;
    }

    public String getCurrentFrameFileName() {
        if (tick <= 0) {
            return "";
        }

        return String.format(
                "%08d.png",
                tick
        );
    }

    public String getCurrentFramePath() {
        String fileName =
                getCurrentFrameFileName();

        if (fileName.isEmpty()) {
            return "";
        }

        return "frames/" + fileName;
    }

    public Integer getCurrentInventoryRevision() {
        return inventoryRevision > 0
                ? inventoryRevision
                : null;
    }

    public Integer getCurrentContainerRevision() {
        return activeContainerScreen != null
                && containerRevision > 0
                ? containerRevision
                : null;
    }

    public void toggleRecording() {
        if (recording) {
            stopRecording(
                    "COMPLETE",
                    "USER_STOP"
            );
        } else {
            startRecording();
        }
    }

    public void prepareGuiScreenEvent(
            Screen screen,
            String eventType
    ) {
        if (!recording
                || !(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }

        try {
            Minecraft client =
                    Minecraft.getInstance();

            if ("SCREEN_OPEN".equals(eventType)) {
                openContainer(
                        client,
                        screen
                );
            } else if ("SCREEN_CLOSE".equals(eventType)
                    && activeContainerScreen == screen) {
                refreshContainer(
                        client,
                        screen,
                        false
                );
            }
        } catch (Exception e) {
            failRecording(
                    "ERROR preparing GUI screen event",
                    e
            );
        }
    }

    public void finishGuiScreenEvent(
            Screen screen,
            String eventType
    ) {
        if (!recording
                || !"SCREEN_CLOSE".equals(eventType)
                || activeContainerScreen != screen) {
            return;
        }

        try {
            closeActiveContainer(
                    "SCREEN_CLOSE"
            );
        } catch (IOException e) {
            failRecording(
                    "ERROR closing container recording state",
                    e
            );
        }
    }

    public void recordGuiInteraction(
            GuiInteractionSnapshot snapshot
    ) {
        if (!recording
                || guiActionsWriter == null
                || snapshot == null) {
            return;
        }

        try {
            guiActionsWriter.write(
                    snapshot
            );

            if (!"SCREEN_OPEN".equals(snapshot.eventType())
                    && !"SCREEN_CLOSE".equals(snapshot.eventType())) {
                Minecraft client =
                        Minecraft.getInstance();

                if (client.screen == activeContainerScreen
                        && activeContainerScreen != null) {
                    refreshContainer(
                            client,
                            activeContainerScreen,
                            false
                    );
                }
            }

            System.out.println(
                    "[MC AI Recorder] GUI action: "
                            + snapshot.actionType()
                            + " screen="
                            + snapshot.screenType()
                            + " slot="
                            + snapshot.slot().slotId()
                            + " interaction="
                            + snapshot.interactionId()
                            + " containerRevision="
                            + snapshot.containerRevision()
            );
        } catch (IOException e) {
            failRecording(
                    "ERROR writing GUI action",
                    e
            );
        }
    }

    public void onClientTick(
            Minecraft client
    ) {
        if (!recording
                || client.player == null
                || client.level == null
                || client.options == null) {
            return;
        }

        tick++;

        gameEventTracker.observeInput(
                tick,
                client.options.keyAttack.isDown(),
                client.options.keyUse.isDown()
        );

        String frameFileName =
                getCurrentFrameFileName();

        String framePath =
                getCurrentFramePath();

        float yaw =
                client.player.getYRot();

        float pitch =
                client.player.getXRot();

        float yawDelta =
                Mth.wrapDegrees(
                        yaw - lastYaw
                );

        float pitchDelta =
                Mth.wrapDegrees(
                        pitch - lastPitch
                );

        int selectedSlot =
                client.player
                        .getInventory()
                        .getSelectedSlot();

        Integer hotbarTarget =
                selectedSlot != lastSelectedSlot
                        ? selectedSlot
                        : null;

        try {
            frameCapture.capture(
                    client,
                    frameFileName
            );

            updateInventorySnapshot(
                    client
            );

            updateWorldSnapshot(
                    client
            );

            updateContainerSnapshot(
                    client
            );

            if (pendingInitialScreenOpen) {
                GuiInteractionRecorder
                        .recordCurrentScreenOpen(
                                this,
                                client.screen
                        );

                pendingInitialScreenOpen =
                        false;
            }

            WorldStepSnapshot snapshot =
                    SnapshotFactory.createWorldStep(
                            client,
                            nextSequenceId(),
                            tick,
                            framePath,
                            getCurrentInventoryRevision(),
                            getCurrentContainerRevision(),
                            worldSnapshotRevision > 0
                                    ? worldSnapshotRevision
                                    : null,
                            yawDelta,
                            pitchDelta,
                            hotbarTarget
                    );

            actionsWriter.write(
                    snapshot
            );

            emitPendingEvents(
                    gameEventTracker
                            .updatePlayerAndCombat(
                                    client,
                                    snapshot
                            )
            );

            lastYaw = yaw;
            lastPitch = pitch;
            lastSelectedSlot = selectedSlot;

            if (tick % 100 == 0) {
                System.out.println(
                        "[MC AI Recorder] Tick: "
                                + tick
                                + ", frame queue: "
                                + frameCapture.getQueueSize()
                                + ", inventoryRevision: "
                                + inventoryRevision
                                + ", worldSnapshotRevision: "
                                + worldSnapshotRevision
                                + ", containerRevision: "
                                + getCurrentContainerRevision()
                                + ", guiOpen: "
                                + snapshot.gui().open()
                                + ", screen: "
                                + snapshot.gui().screenType()
                );
            }
        } catch (Exception e) {
            failRecording(
                    "ERROR writing schema-v2 recording data",
                    e
            );
        }
    }

    private void updateInventorySnapshot(
            Minecraft client
    ) throws IOException {
        InventoryCapture capture =
                SnapshotFactory.captureInventory(
                        client.player
                );

        if (lastInventoryFingerprint != null
                && lastInventoryFingerprint.equals(
                capture.fingerprint()
        )) {
            return;
        }

        inventoryRevision++;

        inventorySnapshotsWriter.write(
                capture.toSnapshot(
                        nextSequenceId(),
                        tick,
                        inventoryRevision
                )
        );

        lastInventoryFingerprint =
                capture.fingerprint();

        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put(
                "revision",
                inventoryRevision
        );

        emitGameEvent(
                "INVENTORY_CHANGED",
                "INFERRED",
                data
        );
    }

    private void updateWorldSnapshot(
            Minecraft client
    ) throws IOException {
        WorldCapture capture =
                SnapshotFactory.captureLocalWorld(
                        client
                );

        boolean changed =
                lastWorldFingerprint == null
                        || !lastWorldFingerprint.equals(
                        capture.fingerprint()
                );

        boolean intervalElapsed =
                lastWorldSnapshotTick
                        == Long.MIN_VALUE
                        || tick
                        - lastWorldSnapshotTick
                        >= RecordingSchema
                        .WORLD_SNAPSHOT_MAX_INTERVAL_TICKS;

        if (!changed && !intervalElapsed) {
            return;
        }

        worldSnapshotRevision++;

        worldSnapshotsWriter.write(
                capture.toSnapshot(
                        nextSequenceId(),
                        tick,
                        worldSnapshotRevision
                )
        );

        if (changed) {
            emitPendingEvents(
                    gameEventTracker.updateWorld(
                            capture,
                            tick
                    )
            );
        }

        lastWorldFingerprint =
                capture.fingerprint();

        lastWorldSnapshotTick =
                tick;
    }

    private void updateContainerSnapshot(
            Minecraft client
    ) throws IOException {
        Screen currentScreen =
                client.screen;

        if (currentScreen
                instanceof AbstractContainerScreen<?>) {
            openContainer(
                    client,
                    currentScreen
            );

            refreshContainer(
                    client,
                    currentScreen,
                    false
            );

            return;
        }

        if (activeContainerScreen != null) {
            closeActiveContainer(
                    "SCREEN_DISAPPEARED"
            );
        }
    }

    private void openContainer(
            Minecraft client,
            Screen screen
    ) throws IOException {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (activeContainerScreen == screen) {
            return;
        }

        if (activeContainerScreen != null) {
            closeActiveContainer(
                    "SCREEN_REPLACED"
            );
        }

        activeContainerScreen = screen;
        activeContainerSource =
                SnapshotFactory.containerSource(
                        client,
                        screen
                );

        lastContainerFingerprint = null;

        refreshContainer(
                client,
                screen,
                true
        );

        emitGameEvent(
                "CONTAINER_OPENED",
                "INFERRED",
                containerEventData(
                        screen,
                        "SCREEN_OPEN"
                )
        );
    }

    private void refreshContainer(
            Minecraft client,
            Screen screen,
            boolean force
    ) throws IOException {
        if (screen != activeContainerScreen
                || !(screen
                instanceof AbstractContainerScreen<?>)) {
            return;
        }

        ContainerCapture capture =
                SnapshotFactory.captureContainer(
                        client,
                        screen,
                        activeContainerSource
                );

        if (!force
                && lastContainerFingerprint != null
                && lastContainerFingerprint.equals(
                capture.fingerprint()
        )) {
            return;
        }

        containerRevision++;

        containerSnapshotsWriter.write(
                capture.toSnapshot(
                        nextSequenceId(),
                        tick,
                        containerRevision
                )
        );

        lastContainerFingerprint =
                capture.fingerprint();
    }

    private void closeActiveContainer(
            String reason
    ) throws IOException {
        if (activeContainerScreen == null) {
            return;
        }

        emitGameEvent(
                "CONTAINER_CLOSED",
                "INFERRED",
                containerEventData(
                        activeContainerScreen,
                        reason
                )
        );

        activeContainerScreen = null;
        activeContainerSource = null;
        lastContainerFingerprint = null;
    }

    private Map<String, Object> containerEventData(
            Screen screen,
            String reason
    ) {
        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put(
                "screenType",
                MenuSemantics.screenType(screen)
        );

        data.put(
                "menuType",
                MenuSemantics.menuType(screen)
        );

        data.put(
                "revision",
                containerRevision
        );

        data.put(
                "reason",
                reason
        );

        if (activeContainerSource != null) {
            data.put(
                    "sourceType",
                    activeContainerSource.type()
            );

            if (activeContainerSource.blockId() != null) {
                data.put(
                        "blockId",
                        activeContainerSource.blockId()
                );
            }

            if (activeContainerSource.position() != null) {
                data.put(
                        "position",
                        activeContainerSource.position()
                );
            }

            if (activeContainerSource.entityId() != null) {
                data.put(
                        "entityId",
                        activeContainerSource.entityId()
                );
            }
        }

        return data;
    }

    private void emitPendingEvents(
            List<PendingGameEvent> events
    ) throws IOException {
        for (PendingGameEvent event : events) {
            emitGameEvent(
                    event.eventType(),
                    event.source(),
                    event.data()
            );
        }
    }

    private void emitGameEvent(
            String eventType,
            String source,
            Map<String, Object> data
    ) throws IOException {
        if (!recording
                || gameEventsWriter == null) {
            return;
        }

        gameEventsWriter.write(
                new GameEventSnapshot(
                        "GAME_EVENT",
                        nextSequenceId(),
                        tick,
                        System.currentTimeMillis(),
                        eventType,
                        source,
                        Map.copyOf(data)
                )
        );
    }

    private void startRecording() {
        Minecraft client =
                Minecraft.getInstance();

        if (client.player == null
                || client.level == null) {
            sendMessage(
                    "Join a world before starting recording"
            );

            return;
        }

        try {
            resetEpisodeState(
                    client
            );

            Files.createDirectories(
                    DATASET_ROOT
            );

            registryHash =
                    RegistrySnapshotExporter.export(
                            DATASET_ROOT
                    );

            Path episodesRoot =
                    DATASET_ROOT.resolve(
                            "episodes"
                    );

            Files.createDirectories(
                    episodesRoot
            );

            episodeDir =
                    episodesRoot.resolve(
                            episodeName
                    );

            framesDir =
                    episodeDir.resolve(
                            "frames"
                    );

            Files.createDirectories(
                    framesDir
            );

            frameCapture =
                    new FrameCapture(
                            framesDir
                    );

            actionsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "actions.jsonl"
                            )
                    );

            guiActionsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "gui_actions.jsonl"
                            )
                    );

            inventorySnapshotsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "inventory_snapshots.jsonl"
                            )
                    );

            worldSnapshotsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "world_snapshots.jsonl"
                            )
                    );

            containerSnapshotsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "container_snapshots.jsonl"
                            )
                    );

            gameEventsWriter =
                    new JsonlWriter(
                            episodeDir.resolve(
                                    "game_events.jsonl"
                            )
                    );

            writeMetadata(
                    "RECORDING",
                    null,
                    null
            );

            recording = true;

            pendingInitialScreenOpen =
                    client.screen
                            instanceof AbstractContainerScreen<?>;

            sendMessage(
                    "Recording started: "
                            + episodeName
            );

            System.out.println(
                    "[MC AI Recorder] Schema v2 stage-2 recording started: "
                            + episodeDir
            );

            System.out.println(
                    "[MC AI Recorder] Registry: "
                            + registryHash
            );
        } catch (Exception e) {
            recording = false;

            closeResources();

            sendMessage(
                    "ERROR starting recording: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    private void resetEpisodeState(
            Minecraft client
    ) {
        tick = 0;
        nextSequenceId = 1;

        inventoryRevision = 0;
        lastInventoryFingerprint = null;

        worldSnapshotRevision = 0;
        lastWorldFingerprint = null;
        lastWorldSnapshotTick = Long.MIN_VALUE;

        containerRevision = 0;
        activeContainerScreen = null;
        activeContainerSource = null;
        lastContainerFingerprint = null;

        pendingInitialScreenOpen = false;

        lastYaw =
                client.player.getYRot();

        lastPitch =
                client.player.getXRot();

        lastSelectedSlot =
                client.player
                        .getInventory()
                        .getSelectedSlot();

        gameEventTracker.reset(
                client
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        startedAt =
                now.format(
                        DateTimeFormatter
                                .ISO_OFFSET_DATE_TIME
                );

        episodeName =
                "episode_"
                        + now.format(
                        DateTimeFormatter
                                .ofPattern(
                                        "yyyyMMdd_HHmmss"
                                )
                );
    }

    private void stopRecording(
            String status,
            String terminationReason
    ) {
        if (!recording
                && episodeDir == null) {
            return;
        }

        if (recording
                && activeContainerScreen != null) {
            try {
                closeActiveContainer(
                        "RECORDING_STOPPED"
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        recording = false;

        closeResources();

        writeMetadata(
                status,
                terminationReason,
                OffsetDateTime.now()
                        .format(
                                DateTimeFormatter
                                        .ISO_OFFSET_DATE_TIME
                        )
        );

        sendMessage(
                "Recording stopped: "
                        + status
        );

        System.out.println(
                "[MC AI Recorder] Recording stopped: "
                        + status
                        + " reason="
                        + terminationReason
        );
    }

    private void failRecording(
            String context,
            Exception error
    ) {
        sendMessage(
                context
                        + ": "
                        + error.getMessage()
        );

        error.printStackTrace();

        stopRecording(
                "FAILED",
                "IO_ERROR"
        );
    }

    private void closeResources() {
        closeWriter(actionsWriter);
        actionsWriter = null;

        closeWriter(guiActionsWriter);
        guiActionsWriter = null;

        closeWriter(inventorySnapshotsWriter);
        inventorySnapshotsWriter = null;

        closeWriter(worldSnapshotsWriter);
        worldSnapshotsWriter = null;

        closeWriter(containerSnapshotsWriter);
        containerSnapshotsWriter = null;

        closeWriter(gameEventsWriter);
        gameEventsWriter = null;

        if (frameCapture != null) {
            try {
                frameCapture.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            frameCapture = null;
        }
    }

    private void closeWriter(
            JsonlWriter writer
    ) {
        if (writer == null) {
            return;
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeMetadata(
            String status,
            String terminationReason,
            String endedAt
    ) {
        if (episodeDir == null) {
            return;
        }

        EpisodeMetadata metadata =
                new EpisodeMetadata(
                        RecordingSchema.SCHEMA_VERSION,
                        episodeName,
                        RecordingSchema.RECORDER_VERSION,
                        RecordingSchema.MINECRAFT_VERSION,
                        RecordingSchema.FABRIC_LOADER_VERSION,
                        registryHash,
                        startedAt,
                        endedAt,
                        status,
                        terminationReason,
                        RecordingSchema.FRAME_FORMAT,
                        RecordingSchema.FRAME_WIDTH,
                        RecordingSchema.FRAME_HEIGHT,
                        RecordingSchema.CAPTURE_EVERY_TICKS,
                        false,
                        tick,
                        List.of(
                                "schema-v2",
                                "hybrid",
                                "survival",
                                "container-snapshots",
                                "game-events"
                        ),
                        ""
                );

        try {
            Files.writeString(
                    episodeDir.resolve(
                            "metadata.json"
                    ),
                    gson.toJson(metadata),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(
            String message
    ) {
        Minecraft client =
                Minecraft.getInstance();

        if (client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal(
                            "[MC AI Recorder] "
                                    + message
                    )
            );
        }
    }
}
