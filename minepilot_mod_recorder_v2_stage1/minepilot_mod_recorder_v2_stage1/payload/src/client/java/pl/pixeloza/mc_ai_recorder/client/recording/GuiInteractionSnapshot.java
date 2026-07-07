package pl.pixeloza.mc_ai_recorder.client.recording;

public record GuiInteractionSnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        String frame,

        String screenType,
        String menuType,
        int slotCount,

        Integer containerRevision,
        Integer inventoryRevision,

        ScreenGeometrySnapshot screen,

        String eventType,
        String actionType,
        long interactionId,

        Integer mouseButton,
        Integer keyCode,

        ModifiersSnapshot modifiers,
        PointerSnapshot pointer,
        GuiSlotSnapshot slot,

        ItemStackSnapshot slotItemBefore,
        ItemStackSnapshot slotItemAfter,
        ItemStackSnapshot carriedItemBefore,
        ItemStackSnapshot carriedItemAfter,

        String screenTitle,
        int rawModifiers,
        double scrollHorizontal,
        double scrollVertical,
        double dragDeltaX,
        double dragDeltaY,
        int rawSlotX,
        int rawSlotY
) {
}

record ScreenGeometrySnapshot(
        int width,
        int height,
        int guiScale
) {
}

record ModifiersSnapshot(
        boolean shift,
        boolean control,
        boolean alt
) {
}

record PointerSnapshot(
        double xPixels,
        double yPixels,
        double xNormalized,
        double yNormalized
) {
}

record GuiSlotSnapshot(
        Integer slotId,
        String role,
        Double centerXNormalized,
        Double centerYNormalized
) {
}
