package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.List;
import java.util.Map;

public record ContainerSnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        int revision,
        String screenType,
        String menuType,
        int slotCount,
        ContainerSourceSnapshot source,
        List<ContainerSlotSnapshot> slots,
        Map<String, Object> properties
) {
}

record ContainerSourceSnapshot(
        String type,
        String blockId,
        BlockPositionSnapshot position,
        String entityId
) {
    String fingerprint() {
        return type
                + ':' + blockId
                + ':' + position
                + ':' + entityId;
    }
}

record ContainerSlotSnapshot(
        int slotId,
        String role,
        ItemStackSnapshot item
) {
}

record ContainerCapture(
        String screenType,
        String menuType,
        int slotCount,
        ContainerSourceSnapshot source,
        List<ContainerSlotSnapshot> slots,
        Map<String, Object> properties,
        String fingerprint
) {
    ContainerSnapshot toSnapshot(
            long sequenceId,
            long tick,
            int revision
    ) {
        return new ContainerSnapshot(
                "CONTAINER_SNAPSHOT",
                sequenceId,
                tick,
                System.currentTimeMillis(),
                revision,
                screenType,
                menuType,
                slotCount,
                source,
                slots,
                properties
        );
    }
}
