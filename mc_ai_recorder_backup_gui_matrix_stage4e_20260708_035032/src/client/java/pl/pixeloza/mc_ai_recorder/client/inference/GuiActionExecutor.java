package pl.pixeloza.mc_ai_recorder.client.inference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import pl.pixeloza.mc_ai_recorder.client.recording.MenuSemantics;
import pl.pixeloza.mc_ai_recorder.client.recording.RuntimeContainerInspector;

public final class GuiActionExecutor {
    private long lastHandledActionSequenceId =
            -1L;

    public ExecutionResult execute(
            Minecraft client,
            ProtocolAction.GuiAction action,
            long actionEnvelopeSequenceId
    ) {
        if (actionEnvelopeSequenceId < 0L) {
            return ExecutionResult.INVALID_SEQUENCE;
        }

        if (actionEnvelopeSequenceId
                == lastHandledActionSequenceId) {
            return ExecutionResult.DUPLICATE_IGNORED;
        }

        if (client.player == null
                || client.gameMode == null
                || !(client.screen
                instanceof AbstractContainerScreen<?> containerScreen)) {
            return ExecutionResult.NO_CONTAINER_SCREEN;
        }

        String currentScreenType =
                MenuSemantics.screenType(
                        client.screen
                );

        String currentMenuType =
                MenuSemantics.menuType(
                        client.screen
                );

        if (!action.screenType()
                .equals(currentScreenType)
                || !action.menuType()
                .equals(currentMenuType)) {
            return ExecutionResult.CONTEXT_MISMATCH;
        }

        if ("CreativeModeInventoryScreen"
                .equals(currentScreenType)
                || "ItemPickerMenu"
                .equals(currentMenuType)) {
            return ExecutionResult.CREATIVE_BLOCKED;
        }

        String expectedFingerprint =
                action.expectedContainerFingerprint();

        if (expectedFingerprint != null) {
            String currentFingerprint;

            try {
                currentFingerprint =
                        RuntimeContainerInspector
                                .fingerprint(
                                        client,
                                        client.screen
                                );
            } catch (RuntimeException e) {
                return ExecutionResult.FINGERPRINT_UNAVAILABLE;
            }

            if (!expectedFingerprint.equals(
                    currentFingerprint
            )) {
                return ExecutionResult.STALE_FINGERPRINT;
            }
        }

        try {
            action.validate();

            /*
             * A GUI command is an edge-triggered action. Once the context and
             * fingerprint match, the sequence is consumed before execution so
             * a client-tick retry can never duplicate a click.
             */
            lastHandledActionSequenceId =
                    actionEnvelopeSequenceId;

            switch (action.operation()) {
                case "CLICK_SLOT" ->
                        click(
                                client,
                                containerScreen,
                                action.slotId(),
                                action.button(),
                                ContainerInput.valueOf(
                                        action.clickType()
                                )
                        );

                case "CLICK_OUTSIDE" ->
                        click(
                                client,
                                containerScreen,
                                -999,
                                action.button(),
                                ContainerInput.valueOf(
                                        action.clickType()
                                )
                        );

                case "QUICK_CRAFT_START" ->
                        click(
                                client,
                                containerScreen,
                                -999,
                                action.quickCraftButton(),
                                ContainerInput.QUICK_CRAFT
                        );

                case "QUICK_CRAFT_ADD_SLOT" ->
                        click(
                                client,
                                containerScreen,
                                action.slotId(),
                                action.quickCraftButton(),
                                ContainerInput.QUICK_CRAFT
                        );

                case "QUICK_CRAFT_END" ->
                        click(
                                client,
                                containerScreen,
                                -999,
                                action.quickCraftButton(),
                                ContainerInput.QUICK_CRAFT
                        );

                case "BUTTON_CLICK" ->
                        client.gameMode
                                .handleInventoryButtonClick(
                                        containerScreen
                                                .getMenu()
                                                .containerId,
                                        action.buttonId()
                                );

                case "CLOSE_SCREEN" ->
                        client.player.closeContainer();

                default -> {
                    return ExecutionResult.UNSUPPORTED_OPERATION;
                }
            }

            return ExecutionResult.EXECUTED;
        } catch (IllegalArgumentException e) {
            return ExecutionResult.INVALID_ACTION;
        } catch (RuntimeException e) {
            return ExecutionResult.EXECUTION_ERROR;
        }
    }

    public void reset() {
        lastHandledActionSequenceId =
                -1L;
    }

    private void click(
            Minecraft client,
            AbstractContainerScreen<?> screen,
            int slotId,
            int button,
            ContainerInput clickType
    ) {
        if (slotId >= 0
                && slotId >= screen.getMenu()
                .slots
                .size()) {
            throw new IllegalArgumentException(
                    "slotId is outside current menu"
            );
        }

        client.gameMode
                .handleContainerInput(
                        screen.getMenu()
                                .containerId,
                        slotId,
                        button,
                        clickType,
                        client.player
                );
    }

    public enum ExecutionResult {
        EXECUTED,
        DUPLICATE_IGNORED,
        INVALID_SEQUENCE,
        NO_CONTAINER_SCREEN,
        CONTEXT_MISMATCH,
        CREATIVE_BLOCKED,
        FINGERPRINT_UNAVAILABLE,
        STALE_FINGERPRINT,
        UNSUPPORTED_OPERATION,
        INVALID_ACTION,
        EXECUTION_ERROR
    }
}
