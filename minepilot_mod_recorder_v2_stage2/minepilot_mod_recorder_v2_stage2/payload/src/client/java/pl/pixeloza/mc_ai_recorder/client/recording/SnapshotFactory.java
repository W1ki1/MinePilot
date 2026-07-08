package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotFactory {
    private SnapshotFactory() {
    }

    public static InventoryCapture captureInventory(
            Player player
    ) {
        Inventory inventory =
                player.getInventory();

        List<InventorySlotSnapshot> slots =
                new ArrayList<>(36);

        StringBuilder fingerprint =
                new StringBuilder();

        fingerprint.append("selected=")
                .append(inventory.getSelectedSlot());

        for (int slot = 0; slot < 9; slot++) {
            ItemStackSnapshot item =
                    ItemStackSnapshot.from(
                            inventory.getItem(slot)
                    );

            slots.add(
                    new InventorySlotSnapshot(
                            "HOTBAR",
                            slot,
                            item
                    )
            );

            appendItemFingerprint(
                    fingerprint,
                    "H" + slot,
                    item
            );
        }

        for (int slot = 0; slot < 27; slot++) {
            ItemStackSnapshot item =
                    ItemStackSnapshot.from(
                            inventory.getItem(slot + 9)
                    );

            slots.add(
                    new InventorySlotSnapshot(
                            "MAIN",
                            slot,
                            item
                    )
            );

            appendItemFingerprint(
                    fingerprint,
                    "M" + slot,
                    item
            );
        }

        ItemStackSnapshot head =
                ItemStackSnapshot.from(
                        player.getItemBySlot(
                                EquipmentSlot.HEAD
                        )
                );

        ItemStackSnapshot chest =
                ItemStackSnapshot.from(
                        player.getItemBySlot(
                                EquipmentSlot.CHEST
                        )
                );

        ItemStackSnapshot legs =
                ItemStackSnapshot.from(
                        player.getItemBySlot(
                                EquipmentSlot.LEGS
                        )
                );

        ItemStackSnapshot feet =
                ItemStackSnapshot.from(
                        player.getItemBySlot(
                                EquipmentSlot.FEET
                        )
                );

        ItemStackSnapshot offhand =
                ItemStackSnapshot.from(
                        player.getItemBySlot(
                                EquipmentSlot.OFFHAND
                        )
                );

        appendItemFingerprint(
                fingerprint,
                "HEAD",
                head
        );

        appendItemFingerprint(
                fingerprint,
                "CHEST",
                chest
        );

        appendItemFingerprint(
                fingerprint,
                "LEGS",
                legs
        );

        appendItemFingerprint(
                fingerprint,
                "FEET",
                feet
        );

        appendItemFingerprint(
                fingerprint,
                "OFFHAND",
                offhand
        );

        return new InventoryCapture(
                inventory.getSelectedSlot(),
                List.copyOf(slots),
                new ArmorSnapshot(
                        head,
                        chest,
                        legs,
                        feet
                ),
                offhand,
                fingerprint.toString()
        );
    }

    public static WorldCapture captureLocalWorld(
            Minecraft client
    ) {
        Player player =
                client.player;

        BlockPos origin =
                player.blockPosition();

        WorldSnapshotBounds bounds =
                new WorldSnapshotBounds(
                        -RecordingSchema.WORLD_RADIUS_HORIZONTAL,
                        RecordingSchema.WORLD_RADIUS_HORIZONTAL,
                        -RecordingSchema.WORLD_RADIUS_VERTICAL,
                        RecordingSchema.WORLD_RADIUS_VERTICAL,
                        -RecordingSchema.WORLD_RADIUS_HORIZONTAL,
                        RecordingSchema.WORLD_RADIUS_HORIZONTAL
                );

        List<WorldBlockSnapshot> blocks =
                new ArrayList<>();

        StringBuilder fingerprint =
                new StringBuilder();

        fingerprint.append(origin.getX())
                .append(',')
                .append(origin.getY())
                .append(',')
                .append(origin.getZ())
                .append(';');

        for (int dy = bounds.minDy();
             dy <= bounds.maxDy();
             dy++) {

            for (int dz = bounds.minDz();
                 dz <= bounds.maxDz();
                 dz++) {

                for (int dx = bounds.minDx();
                     dx <= bounds.maxDx();
                     dx++) {

                    BlockPos position =
                            origin.offset(
                                    dx,
                                    dy,
                                    dz
                            );

                    BlockState state =
                            client.level
                                    .getBlockState(
                                            position
                                    );

                    if (state.isAir()) {
                        continue;
                    }

                    String blockId =
                            BuiltInRegistries.BLOCK
                                    .getKey(
                                            state.getBlock()
                                    )
                                    .toString();

                    Map<String, String> properties =
                            blockProperties(
                                    state
                            );

                    blocks.add(
                            new WorldBlockSnapshot(
                                    dx,
                                    dy,
                                    dz,
                                    blockId,
                                    properties
                            )
                    );

                    fingerprint.append(dx)
                            .append(',')
                            .append(dy)
                            .append(',')
                            .append(dz)
                            .append('=')
                            .append(blockId);

                    for (Map.Entry<String, String> entry :
                            properties.entrySet()) {
                        fingerprint.append('|')
                                .append(entry.getKey())
                                .append('=')
                                .append(entry.getValue());
                    }

                    fingerprint.append(';');
                }
            }
        }

        return new WorldCapture(
                new BlockPositionSnapshot(
                        origin.getX(),
                        origin.getY(),
                        origin.getZ()
                ),
                bounds,
                List.copyOf(blocks),
                fingerprint.toString()
        );
    }

    public static ContainerSourceSnapshot containerSource(
            Minecraft client,
            Screen screen
    ) {
        String menuType =
                MenuSemantics.menuType(screen);

        if ("InventoryMenu".equals(menuType)) {
            return new ContainerSourceSnapshot(
                    "PLAYER",
                    null,
                    null,
                    null
            );
        }

        HitResult hitResult =
                client.hitResult;

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos position =
                    blockHitResult.getBlockPos();

            BlockState state =
                    client.level.getBlockState(
                            position
                    );

            return new ContainerSourceSnapshot(
                    "BLOCK",
                    BuiltInRegistries.BLOCK
                            .getKey(
                                    state.getBlock()
                            )
                            .toString(),
                    new BlockPositionSnapshot(
                            position.getX(),
                            position.getY(),
                            position.getZ()
                    ),
                    null
            );
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity =
                    entityHitResult.getEntity();

            return new ContainerSourceSnapshot(
                    "ENTITY",
                    null,
                    null,
                    "session:" + entity.getId()
            );
        }

        return new ContainerSourceSnapshot(
                "UNKNOWN",
                null,
                null,
                null
        );
    }

    public static ContainerCapture captureContainer(
            Minecraft client,
            Screen screen,
            ContainerSourceSnapshot source
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            throw new IllegalArgumentException(
                    "Screen is not a container screen"
            );
        }

        AbstractContainerMenu menu =
                containerScreen.getMenu();

        List<ContainerSlotSnapshot> slots =
                new ArrayList<>(
                        menu.slots.size()
                );

        String screenType =
                MenuSemantics.screenType(screen);

        String menuType =
                MenuSemantics.menuType(screen);

        StringBuilder fingerprint =
                new StringBuilder();

        fingerprint.append(screenType)
                .append('|')
                .append(menuType)
                .append('|')
                .append(source.fingerprint());

        for (int slotId = 0;
             slotId < menu.slots.size();
             slotId++) {

            Slot slot =
                    menu.slots.get(slotId);

            ItemStackSnapshot item =
                    ItemStackSnapshot.from(
                            slot.getItem()
                    );

            String role =
                    MenuSemantics.slotRole(
                            screen,
                            slotId
                    );

            slots.add(
                    new ContainerSlotSnapshot(
                            slotId,
                            role,
                            item
                    )
            );

            fingerprint.append(';')
                    .append(slotId)
                    .append(':')
                    .append(role)
                    .append('=');

            if (item == null) {
                fingerprint.append("air");
            } else {
                fingerprint.append(
                        item.fingerprint()
                );
            }
        }

        Map<String, Object> properties =
                new LinkedHashMap<>();

        if (menu instanceof AbstractFurnaceMenu furnaceMenu) {
            properties.put(
                    "cookProgress",
                    furnaceMenu.getBurnProgress()
            );

            properties.put(
                    "litProgress",
                    furnaceMenu.getLitProgress()
            );

            properties.put(
                    "lit",
                    furnaceMenu.isLit()
            );
        }

        for (Map.Entry<String, Object> entry :
                properties.entrySet()) {
            fingerprint.append(";property:")
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }

        return new ContainerCapture(
                screenType,
                menuType,
                menu.slots.size(),
                source,
                List.copyOf(slots),
                Collections.unmodifiableMap(properties),
                fingerprint.toString()
        );
    }

    public static WorldStepSnapshot createWorldStep(
            Minecraft client,
            long sequenceId,
            long tick,
            String framePath,
            Integer inventoryRevision,
            Integer containerRevision,
            Integer worldSnapshotRevision,
            float yawDelta,
            float pitchDelta,
            Integer hotbarTarget
    ) {
        Player player =
                client.player;

        Screen screen =
                client.screen;

        boolean dropKey =
                client.options
                        .keyDrop
                        .isDown();

        boolean shiftKey =
                client.options
                        .keyShift
                        .isDown();

        WorldActionSnapshot action =
                new WorldActionSnapshot(
                        client.options.keyUp.isDown(),
                        client.options.keyDown.isDown(),
                        client.options.keyLeft.isDown(),
                        client.options.keyRight.isDown(),
                        client.options.keyJump.isDown(),
                        shiftKey,
                        client.options.keySprint.isDown(),
                        client.options.keyAttack.isDown(),
                        client.options.keyUse.isDown(),
                        client.options.keyInventory.isDown(),
                        dropKey && !shiftKey,
                        dropKey && shiftKey,
                        client.options
                                .keySwapOffhand
                                .isDown(),
                        hotbarTarget,
                        yawDelta,
                        pitchDelta
                );

        return new WorldStepSnapshot(
                "WORLD_STEP",
                sequenceId,
                tick,
                System.currentTimeMillis(),
                framePath,
                playerState(player),
                worldState(client),
                targetState(client),
                nearbyEntities(client),
                inventoryRevision,
                containerRevision,
                worldSnapshotRevision,
                new GuiStateSnapshot(
                        screen != null,
                        MenuSemantics.screenType(screen),
                        MenuSemantics.menuType(screen)
                ),
                action
        );
    }

    private static PlayerStateSnapshot playerState(
            Player player
    ) {
        List<StatusEffectSnapshot> effects =
                new ArrayList<>();

        for (MobEffectInstance effect :
                player.getActiveEffects()) {

            String effectId =
                    effect.getEffect()
                            .unwrapKey()
                            .map(key ->
                                    key.identifier()
                                            .toString()
                            )
                            .orElse(
                                    effect.getDescriptionId()
                            );

            effects.add(
                    new StatusEffectSnapshot(
                            effectId,
                            effect.getAmplifier(),
                            effect.getDuration()
                    )
            );
        }

        String activeHand =
                player.isUsingItem()
                        ? player.getUsedItemHand()
                        .name()
                        : "NONE";

        Vec3 velocity =
                player.getDeltaMovement();

        return new PlayerStateSnapshot(
                player.getHealth(),
                player.getMaxHealth(),
                player.getFoodData()
                        .getFoodLevel(),
                player.getFoodData()
                        .getSaturationLevel(),
                player.getArmorValue(),
                player.getAirSupply(),
                player.experienceLevel,
                player.experienceProgress,
                new Vec3Snapshot(
                        player.getX(),
                        player.getY(),
                        player.getZ()
                ),
                new Vec3Snapshot(
                        velocity.x(),
                        velocity.y(),
                        velocity.z()
                ),
                player.getYRot(),
                player.getXRot(),
                player.onGround(),
                player.isInWater(),
                player.isInLava(),
                player.fallDistance,
                player.isSprinting(),
                player.isShiftKeyDown(),
                player.isUsingItem(),
                activeHand,
                player.getUseItemRemainingTicks(),
                player.getAttackStrengthScale(
                        0.0f
                ),
                player.hurtTime,
                player.isBlocking(),
                List.copyOf(effects)
        );
    }

    private static WorldStateSnapshot worldState(
            Minecraft client
    ) {
        BlockPos position =
                client.player
                        .blockPosition();

        String dimension =
                client.level
                        .dimension()
                        .identifier()
                        .toString();

        String biome =
                client.level
                        .getBiome(position)
                        .unwrapKey()
                        .map(key ->
                                key.identifier()
                                        .toString()
                        )
                        .orElse("unknown");

        return new WorldStateSnapshot(
                dimension,
                biome,
                client.level.getGameTime(),
                client.level.getOverworldClockTime(),
                client.level.isRaining(),
                client.level.isThundering(),
                client.level.getBrightness(
                        LightLayer.BLOCK,
                        position
                ),
                client.level.getBrightness(
                        LightLayer.SKY,
                        position
                )
        );
    }

    private static TargetSnapshot targetState(
            Minecraft client
    ) {
        HitResult hitResult =
                client.hitResult;

        if (hitResult == null
                || hitResult.getType()
                == HitResult.Type.MISS) {

            return new TargetSnapshot(
                    "MISS",
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    null,
                    null,
                    null
            );
        }

        double distance =
                Math.sqrt(
                        hitResult.distanceTo(
                                client.player
                        )
                );

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos position =
                    blockHitResult.getBlockPos();

            BlockState state =
                    client.level
                            .getBlockState(
                                    position
                            );

            return new TargetSnapshot(
                    "BLOCK",
                    distance,
                    BuiltInRegistries.BLOCK
                            .getKey(
                                    state.getBlock()
                            )
                            .toString(),
                    blockHitResult.getDirection()
                            .name(),
                    new BlockPositionSnapshot(
                            position.getX(),
                            position.getY(),
                            position.getZ()
                    ),
                    blockProperties(state),
                    null,
                    null,
                    null
            );
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity =
                    entityHitResult.getEntity();

            Vec3 relative =
                    entity.position()
                            .subtract(
                                    client.player
                                            .position()
                            );

            return new TargetSnapshot(
                    "ENTITY",
                    distance,
                    null,
                    null,
                    null,
                    Map.of(),
                    "session:" + entity.getId(),
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(
                                    entity.getType()
                            )
                            .toString(),
                    new Vec3Snapshot(
                            relative.x(),
                            relative.y(),
                            relative.z()
                    )
            );
        }

        return new TargetSnapshot(
                "MISS",
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                null
        );
    }

    private static List<NearbyEntitySnapshot> nearbyEntities(
            Minecraft client
    ) {
        Player player =
                client.player;

        List<Entity> entities =
                client.level
                        .getEntities(
                                player,
                                player.getBoundingBox()
                                        .inflate(
                                                RecordingSchema
                                                        .NEARBY_ENTITY_RADIUS
                                        ),
                                entity ->
                                        entity != player
                                                && entity.isAlive()
                        );

        entities.sort(
                Comparator.comparingDouble(
                        player::distanceTo
                )
        );

        List<NearbyEntitySnapshot> snapshots =
                new ArrayList<>();

        for (Entity entity :
                entities) {

            if (snapshots.size()
                    >= RecordingSchema
                    .NEARBY_ENTITY_LIMIT) {
                break;
            }

            Vec3 relative =
                    entity.position()
                            .subtract(
                                    player.position()
                            );

            Vec3 velocity =
                    entity.getDeltaMovement();

            double horizontalDistance =
                    Math.sqrt(
                            relative.x()
                                    * relative.x()
                                    + relative.z()
                                    * relative.z()
                    );

            double targetYaw =
                    Math.toDegrees(
                            Math.atan2(
                                    -relative.x(),
                                    relative.z()
                            )
                    );

            double targetPitch =
                    -Math.toDegrees(
                            Math.atan2(
                                    relative.y(),
                                    horizontalDistance
                            )
                    );

            Float health =
                    entity instanceof LivingEntity livingEntity
                            ? livingEntity.getHealth()
                            : null;

            snapshots.add(
                    new NearbyEntitySnapshot(
                            "session:" + entity.getId(),
                            BuiltInRegistries.ENTITY_TYPE
                                    .getKey(
                                            entity.getType()
                                    )
                                    .toString(),
                            new Vec3Snapshot(
                                    relative.x(),
                                    relative.y(),
                                    relative.z()
                            ),
                            player.distanceTo(entity),
                            new Vec3Snapshot(
                                    velocity.x(),
                                    velocity.y(),
                                    velocity.z()
                            ),
                            Mth.wrapDegrees(
                                    targetYaw
                                            - player.getYRot()
                            ),
                            Mth.wrapDegrees(
                                    targetPitch
                                            - player.getXRot()
                            ),
                            entity.onGround(),
                            health,
                            entity instanceof Enemy,
                            entity.isAlive()
                    )
            );
        }

        return List.copyOf(snapshots);
    }

    static Map<String, String> blockProperties(
            BlockState state
    ) {
        Map<String, String> properties =
                new LinkedHashMap<>();

        for (Property<?> property :
                state.getProperties()) {

            properties.put(
                    property.getName(),
                    propertyValue(
                            state,
                            property
                    )
            );
        }

        return Collections.unmodifiableMap(properties);
    }

    private static <T extends Comparable<T>> String propertyValue(
            BlockState state,
            Property<T> property
    ) {
        return property.getName(
                state.getValue(
                        property
                )
        );
    }

    private static void appendItemFingerprint(
            StringBuilder builder,
            String slot,
            ItemStackSnapshot item
    ) {
        builder.append(';')
                .append(slot)
                .append('=');

        if (item == null) {
            builder.append("air");
        } else {
            builder.append(
                    item.fingerprint()
            );
        }
    }
}

record InventoryCapture(
        int selectedHotbarSlot,
        List<InventorySlotSnapshot> slots,
        ArmorSnapshot armor,
        ItemStackSnapshot offhand,
        String fingerprint
) {
    InventorySnapshot toSnapshot(
            long sequenceId,
            long tick,
            int revision
    ) {
        return new InventorySnapshot(
                "INVENTORY_SNAPSHOT",
                sequenceId,
                tick,
                System.currentTimeMillis(),
                revision,
                selectedHotbarSlot,
                slots,
                armor,
                offhand
        );
    }
}

record WorldCapture(
        BlockPositionSnapshot origin,
        WorldSnapshotBounds bounds,
        List<WorldBlockSnapshot> blocks,
        String fingerprint
) {
    WorldSnapshot toSnapshot(
            long sequenceId,
            long tick,
            int revision
    ) {
        return new WorldSnapshot(
                "WORLD_SNAPSHOT",
                sequenceId,
                tick,
                System.currentTimeMillis(),
                revision,
                origin,
                bounds,
                blocks
        );
    }
}
