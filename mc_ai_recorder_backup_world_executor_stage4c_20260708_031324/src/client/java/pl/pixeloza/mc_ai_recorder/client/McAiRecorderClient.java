package pl.pixeloza.mc_ai_recorder.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import pl.pixeloza.mc_ai_recorder.client.control.AiController;
import pl.pixeloza.mc_ai_recorder.client.hud.AiHud;
import pl.pixeloza.mc_ai_recorder.client.inference.AiAction;
import pl.pixeloza.mc_ai_recorder.client.inference.TcpInferenceLoop;
import pl.pixeloza.mc_ai_recorder.client.legacy.ActionReader;
import pl.pixeloza.mc_ai_recorder.client.legacy.LiveFrameExporter;
import pl.pixeloza.mc_ai_recorder.client.recording.GuiInteractionRecorder;
import pl.pixeloza.mc_ai_recorder.client.recording.RecordingManager;

public class McAiRecorderClient
        implements ClientModInitializer {

    private static final RecordingManager RECORDING_MANAGER =
            new RecordingManager();

    private static final LiveFrameExporter LIVE_FRAME_EXPORTER =
            new LiveFrameExporter();

    private static final ActionReader ACTION_READER =
            new ActionReader();

    private static final AiController AI_CONTROLLER =
            new AiController();

    private static final TcpInferenceLoop TCP_INFERENCE_LOOP =
            new TcpInferenceLoop();

    private KeyMapping toggleHudKey;
    private KeyMapping toggleRecordingKey;
    private KeyMapping toggleLiveExportKey;
    private KeyMapping toggleAiControlKey;
    private KeyMapping toggleTcpInferenceKey;

    @Override
    public void onInitializeClient() {
        AiHud.register();

        GuiInteractionRecorder.register(
                RECORDING_MANAGER
        );

        KeyMapping.Category category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath(
                                "mc_ai_recorder",
                                "main"
                        )
                );

        toggleHudKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.mc_ai_recorder.toggle_hud",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_H,
                                category
                        )
                );

        toggleRecordingKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.mc_ai_recorder.toggle_recording",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_F8,
                                category
                        )
                );

        toggleLiveExportKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.mc_ai_recorder.toggle_live_export",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_F9,
                                category
                        )
                );

        toggleAiControlKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.mc_ai_recorder.toggle_ai_control",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_F10,
                                category
                        )
                );

        toggleTcpInferenceKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.mc_ai_recorder.toggle_tcp_inference",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_F12,
                                category
                        )
                );

        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    while (toggleHudKey.consumeClick()) {
                        AiHud.toggle();
                    }

                    while (toggleRecordingKey.consumeClick()) {
                        RECORDING_MANAGER.toggleRecording();
                    }

                    while (toggleLiveExportKey.consumeClick()) {
                        LIVE_FRAME_EXPORTER.toggle();
                    }

                    while (toggleAiControlKey.consumeClick()) {
                        AI_CONTROLLER.toggle();
                    }

                    while (toggleTcpInferenceKey.consumeClick()) {
                        TCP_INFERENCE_LOOP.toggle();
                    }

                    RECORDING_MANAGER.onClientTick(
                            client
                    );

                    LIVE_FRAME_EXPORTER.onClientTick(
                            client
                    );

                    ACTION_READER.onClientTick(
                            client
                    );

                    TCP_INFERENCE_LOOP.onClientTick(
                            client
                    );

                    AiAction selectedAction;

                    if (TCP_INFERENCE_LOOP.isEnabled()) {
                        selectedAction =
                                TCP_INFERENCE_LOOP
                                        .getLastAction();
                    } else {
                        selectedAction =
                                ACTION_READER
                                        .getLastAction();
                    }

                    AI_CONTROLLER.onClientTick(
                            client,
                            selectedAction
                    );
                }
        );

        System.out.println(
                "[MC AI Recorder] Client initialized"
        );
    }
}
