package pl.pixeloza.mc_ai_recorder.client.recording;

public record GuiInteractionSnapshot(
        long tick,
        long timestamp,
        String frame,

        String screenType,
        String screenTitle,

        String eventType,
        String actionType,

        int mouseButton,
        int keyCode,
        int modifiers,

        double mouseX,
        double mouseY,
        double mouseXNormalized,
        double mouseYNormalized,

        double scrollHorizontal,
        double scrollVertical,

        int slotId,
        int slotX,
        int slotY,

        boolean shiftDown,
        boolean controlDown,
        boolean altDown,

        String slotItemBefore,
        int slotItemCountBefore,

        String slotItemAfter,
        int slotItemCountAfter,

        String carriedItemBefore,
        int carriedItemCountBefore,

        String carriedItemAfter,
        int carriedItemCountAfter
) {
}