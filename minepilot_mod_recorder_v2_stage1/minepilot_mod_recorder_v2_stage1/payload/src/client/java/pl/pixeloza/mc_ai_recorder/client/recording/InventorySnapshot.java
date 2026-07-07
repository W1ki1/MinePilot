package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.List;

public record InventorySnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        int revision,
        int selectedHotbarSlot,
        List<InventorySlotSnapshot> slots,
        ArmorSnapshot armor,
        ItemStackSnapshot offhand
) {
}

record InventorySlotSnapshot(
        String role,
        int index,
        ItemStackSnapshot item
) {
}

record ArmorSnapshot(
        ItemStackSnapshot head,
        ItemStackSnapshot chest,
        ItemStackSnapshot legs,
        ItemStackSnapshot feet
) {
}
