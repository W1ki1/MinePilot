package pl.pixeloza.mc_ai_recorder.client.inference;

public record ProtocolAction(
        int protocolVersion,
        long observationSequenceId,
        String actionType,
        SystemAction system,
        Integer validForTicks,
        long createdAtMs
) {
    public boolean isSafeIdle() {
        return "SYSTEM".equals(
                actionType
        )
                && system != null
                && "SAFE_IDLE".equals(
                system.action()
        );
    }

    public record SystemAction(
            String action
    ) {
    }
}
