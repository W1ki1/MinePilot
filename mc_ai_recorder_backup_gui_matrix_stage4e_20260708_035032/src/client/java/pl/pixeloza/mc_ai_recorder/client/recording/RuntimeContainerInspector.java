package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class RuntimeContainerInspector {
    private RuntimeContainerInspector() {
    }

    public static String fingerprint(
            Minecraft client,
            Screen screen
    ) {
        ContainerSourceSnapshot source =
                SnapshotFactory.containerSource(
                        client,
                        screen
                );

        ContainerCapture capture =
                SnapshotFactory.captureContainer(
                        client,
                        screen,
                        source
                );

        return capture.fingerprint();
    }
}
