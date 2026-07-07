package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RecordingManager {
    private static final String MOD_VERSION = "0.4.0";
    private static final String MINECRAFT_VERSION = "26.1.2";

    private final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private boolean recording = false;
    private long tick = 0;

    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;

    private boolean lastInventoryOpen = false;
    private int lastSelectedSlot = -1;

    private JsonlWriter actionsWriter;
    private JsonlWriter guiActionsWriter;

    private FrameCapture frameCapture;

    private Path episodeDir;
    private Path framesDir;

    private String episodeName;
    private String startedAt;

    public boolean isRecording() {
        return recording;
    }

    public long getCurrentTick() {
        return tick;
    }

    public String getCurrentFrameName() {
        if (tick <= 0) {
            return "";
        }

        return String.format(
                "%06d.png",
                tick
        );
    }

    public void toggleRecording() {
        if (recording) {
            stopRecording("normal");
        } else {
            startRecording();
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
            guiActionsWriter.write(snapshot);

            System.out.println(
                    "[MC AI Recorder] GUI action: "
                            + snapshot.actionType()
                            + " screen="
                            + snapshot.screenType()
                            + " slot="
                            + snapshot.slotId()
                            + " interaction="
                            + snapshot.interactionId()
            );
        } catch (IOException e) {
            sendMessage(
                    "ERROR writing GUI action: "
                            + e.getMessage()
            );

            e.printStackTrace();
            stopRecording("error");
        }
    }

    public void onClientTick(
            Minecraft client
    ) {
        if (!recording
                || client.player == null
                || client.options == null) {
            return;
        }

        tick++;

        String frameName =
                getCurrentFrameName();

        float yaw =
                client.player.getYRot();

        float pitch =
                client.player.getXRot();

        float yawDelta =
                yaw - lastYaw;

        float pitchDelta =
                pitch - lastPitch;

        lastYaw = yaw;
        lastPitch = pitch;

        Screen currentScreen =
                client.screen;

        boolean inventoryOpen =
                currentScreen instanceof InventoryScreen;

        boolean inventoryToggled =
                inventoryOpen != lastInventoryOpen;

        lastInventoryOpen =
                inventoryOpen;

        /*
         * guiOpen obejmuje wszystkie ekrany.
         *
         * Dzięki temu później world dataset może odrzucić:
         * CraftingScreen, InventoryScreen, ChestScreen itd.
         */
        boolean guiOpen =
                currentScreen != null;

        String screenType =
                currentScreen != null
                        ? currentScreen
                        .getClass()
                        .getSimpleName()
                        : "NONE";

        int selectedSlot =
                client.player
                        .getInventory()
                        .getSelectedSlot();

        boolean hotbarChanged =
                selectedSlot != lastSelectedSlot;

        int hotbarTarget =
                hotbarChanged
                        ? selectedSlot
                        : -1;

        lastSelectedSlot =
                selectedSlot;

        String selectedItem =
                client.player
                        .getInventory()
                        .getSelectedItem()
                        .getItem()
                        .toString();

        int selectedItemCount =
                client.player
                        .getInventory()
                        .getSelectedItem()
                        .getCount();

        String dimension =
                "unknown";

        String biome =
                "unknown";

        if (client.level != null) {
            dimension =
                    client.level
                            .dimension()
                            .toString();

            try {
                biome =
                        client.level
                                .getBiome(
                                        client.player
                                                .blockPosition()
                                )
                                .unwrapKey()
                                .map(Object::toString)
                                .orElse("unknown");
            } catch (Exception ignored) {
                biome = "unknown";
            }
        }

        InputSnapshot snapshot =
                new InputSnapshot(
                        tick,
                        System.currentTimeMillis(),
                        frameName,

                        client.options.keyUp.isDown(),
                        client.options.keyDown.isDown(),
                        client.options.keyLeft.isDown(),
                        client.options.keyRight.isDown(),

                        client.options.keyJump.isDown(),
                        client.options.keyShift.isDown(),
                        client.options.keySprint.isDown(),
                        client.player.isSprinting(),

                        client.options.keyAttack.isDown(),
                        client.options.keyUse.isDown(),

                        inventoryToggled,
                        inventoryOpen,

                        guiOpen,
                        screenType,

                        hotbarChanged,
                        hotbarTarget,

                        yaw,
                        pitch,
                        yawDelta,
                        pitchDelta,

                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),

                        client.player.getHealth(),

                        client.player
                                .getFoodData()
                                .getFoodLevel(),

                        selectedSlot,

                        selectedItem,
                        selectedItemCount,

                        dimension,
                        biome,

                        client.player.onGround(),
                        client.player.isInWater(),
                        client.player.isUnderWater(),
                        client.player.isFallFlying(),

                        client.player.experienceLevel
                );

        try {
            frameCapture.capture(
                    client,
                    frameName
            );

            actionsWriter.write(snapshot);

            if (tick % 100 == 0) {
                System.out.println(
                        "[MC AI Recorder] Tick: "
                                + tick
                                + ", frame queue: "
                                + frameCapture.getQueueSize()
                                + ", guiOpen: "
                                + guiOpen
                                + ", screen: "
                                + screenType
                );
            }

            if (inventoryToggled) {
                System.out.println(
                        "[MC AI Recorder] Inventory toggle "
                                + "recorded at tick "
                                + tick
                                + " -> "
                                + (
                                inventoryOpen
                                        ? "OPEN"
                                        : "CLOSED"
                        )
                );
            }

            if (hotbarChanged) {
                System.out.println(
                        "[MC AI Recorder] Hotbar changed "
                                + "at tick "
                                + tick
                                + " -> slot "
                                + hotbarTarget
                );
            }
        } catch (IOException e) {
            sendMessage(
                    "ERROR writing recording data: "
                            + e.getMessage()
            );

            e.printStackTrace();
            stopRecording("error");
        }
    }

    private void startRecording() {
        try {
            tick = 0;

            Minecraft client =
                    Minecraft.getInstance();

            if (client.player != null) {
                lastYaw =
                        client.player.getYRot();

                lastPitch =
                        client.player.getXRot();

                lastSelectedSlot =
                        client.player
                                .getInventory()
                                .getSelectedSlot();
            } else {
                lastSelectedSlot = -1;
            }

            lastInventoryOpen =
                    client.screen instanceof InventoryScreen;

            startedAt =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter
                                            .ISO_LOCAL_DATE_TIME
                            );

            episodeName =
                    "episode_"
                            + LocalDateTime.now()
                            .format(
                                    DateTimeFormatter
                                            .ofPattern(
                                                    "yyyyMMdd_HHmmss"
                                            )
                            );

            Path recordingsRoot =
                    Path.of(
                            "G:/MinecraftAI/Recordings"
                    );

            episodeDir =
                    recordingsRoot.resolve(
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

            recording = true;

            sendMessage(
                    "Recording started: "
                            + episodeName
            );

            System.out.println(
                    "[MC AI Recorder] Recording started: "
                            + episodeDir
            );
        } catch (IOException e) {
            sendMessage(
                    "ERROR starting recording: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    private void stopRecording(
            String status
    ) {
        recording = false;

        if (actionsWriter != null) {
            try {
                actionsWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            actionsWriter = null;
        }

        if (guiActionsWriter != null) {
            try {
                guiActionsWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            guiActionsWriter = null;
        }

        if (frameCapture != null) {
            try {
                frameCapture.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            frameCapture = null;
        }

        writeMetadata(status);

        sendMessage(
                "Recording stopped"
        );

        System.out.println(
                "[MC AI Recorder] Recording stopped"
        );
    }

    private void writeMetadata(
            String status
    ) {
        if (episodeDir == null) {
            return;
        }

        EpisodeMetadata metadata =
                new EpisodeMetadata(
                        episodeName,
                        startedAt,

                        LocalDateTime.now()
                                .format(
                                        DateTimeFormatter
                                                .ISO_LOCAL_DATE_TIME
                                ),

                        status,
                        tick,
                        MINECRAFT_VERSION,
                        MOD_VERSION
                );

        try {
            Files.writeString(
                    episodeDir.resolve(
                            "metadata.json"
                    ),
                    gson.toJson(metadata)
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