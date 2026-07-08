package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

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

        StringBuilder fingerprint =
                new StringBuilder(
                        capture.fingerprint()
                );

        fingerprint.append(
                ";carried="
        );

        if (screen
                instanceof AbstractContainerScreen<?> containerScreen) {
            ItemStackSnapshot carried =
                    ItemStackSnapshot.from(
                            containerScreen
                                    .getMenu()
                                    .getCarried()
                    );

            if (carried == null) {
                fingerprint.append(
                        "air"
                );
            } else {
                fingerprint.append(
                        carried.fingerprint()
                );
            }
        } else {
            fingerprint.append(
                    "air"
            );
        }

        return fingerprint.toString();
    }
}
