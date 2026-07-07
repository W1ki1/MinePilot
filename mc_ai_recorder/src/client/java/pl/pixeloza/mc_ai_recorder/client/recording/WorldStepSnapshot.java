package pl.pixeloza.mc_ai_recorder.client.recording;

import java.util.List;
import java.util.Map;

public record WorldStepSnapshot(
        String recordType,
        long sequenceId,
        long tick,
        long timestampMs,
        String frame,
        PlayerStateSnapshot player,
        WorldStateSnapshot world,
        TargetSnapshot target,
        List<NearbyEntitySnapshot> nearbyEntities,
        Integer inventoryRevision,
        Integer containerRevision,
        Integer worldSnapshotRevision,
        GuiStateSnapshot gui,
        WorldActionSnapshot action
) {
}

record StatusEffectSnapshot(
        String effectId,
        int amplifier,
        int durationTicks
) {
}

record PlayerStateSnapshot(
        float health,
        float maxHealth,
        int hunger,
        float saturation,
        int armor,
        int air,
        int experienceLevel,
        float experienceProgress,
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        float yawDegrees,
        float pitchDegrees,
        boolean onGround,
        boolean inWater,
        boolean inLava,
        double fallDistance,
        boolean sprinting,
        boolean sneaking,
        boolean usingItem,
        String activeHand,
        int useItemRemainingTicks,
        float attackCooldown,
        int hurtTime,
        boolean blocking,
        List<StatusEffectSnapshot> statusEffects
) {
}

record WorldStateSnapshot(
        String dimension,
        String biome,
        long gameTime,
        long dayTime,
        boolean raining,
        boolean thundering,
        int blockLight,
        int skyLight
) {
}

record TargetSnapshot(
        String type,
        Double distance,
        String blockId,
        String blockFace,
        BlockPositionSnapshot blockPosition,
        Map<String, String> blockProperties,
        String entityId,
        String entityType,
        Vec3Snapshot entityRelativePosition
) {
}

record NearbyEntitySnapshot(
        String entityId,
        String entityType,
        Vec3Snapshot relativePosition,
        double distance,
        Vec3Snapshot velocity,
        double relativeYawDegrees,
        double relativePitchDegrees,
        boolean onGround,
        Float health,
        boolean hostile,
        boolean alive
) {
}

record GuiStateSnapshot(
        boolean open,
        String screenType,
        String menuType
) {
}

record WorldActionSnapshot(
        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean jump,
        boolean sneak,
        boolean sprint,
        boolean attack,
        boolean use,
        boolean inventoryToggle,
        boolean dropOne,
        boolean dropStack,
        boolean swapHands,
        Integer hotbarTarget,
        float yawDeltaDegrees,
        float pitchDeltaDegrees
) {
}
