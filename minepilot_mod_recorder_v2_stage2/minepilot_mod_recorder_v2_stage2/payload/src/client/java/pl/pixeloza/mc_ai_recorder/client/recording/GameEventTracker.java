package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class GameEventTracker {
    private static final float EPSILON = 0.0001f;
    private static final long INTERACTION_EVENT_WINDOW_TICKS = 8;

    private float previousHealth;
    private int previousHunger;
    private boolean previousDead;
    private boolean previousUsingItem;
    private String previousUsedHand;
    private ItemStackSnapshot previousMainHand;
    private ItemStackSnapshot previousOffhand;

    private long lastAttackTick = Long.MIN_VALUE;
    private long lastUseTick = Long.MIN_VALUE;

    private Map<String, EntityHealthState> previousEntityHealth =
            Map.of();

    private AbsoluteWorld previousWorld;

    void reset(
            Minecraft client
    ) {
        Player player = client.player;

        previousHealth = player.getHealth();
        previousHunger = player.getFoodData().getFoodLevel();
        previousDead = player.isDeadOrDying();
        previousUsingItem = player.isUsingItem();
        previousUsedHand = previousUsingItem
                ? player.getUsedItemHand().name()
                : "NONE";
        previousMainHand = ItemStackSnapshot.from(
                player.getMainHandItem()
        );
        previousOffhand = ItemStackSnapshot.from(
                player.getOffhandItem()
        );

        lastAttackTick = Long.MIN_VALUE;
        lastUseTick = Long.MIN_VALUE;
        previousEntityHealth = Map.of();
        previousWorld = null;
    }

    void observeInput(
            long tick,
            boolean attack,
            boolean use
    ) {
        if (attack) {
            lastAttackTick = tick;
        }

        if (use) {
            lastUseTick = tick;
        }
    }

    List<PendingGameEvent> updatePlayerAndCombat(
            Minecraft client,
            WorldStepSnapshot step
    ) {
        List<PendingGameEvent> events =
                new ArrayList<>();

        Player player = client.player;
        float currentHealth = player.getHealth();
        int currentHunger =
                player.getFoodData().getFoodLevel();
        boolean currentDead =
                player.isDeadOrDying();

        if (Math.abs(currentHealth - previousHealth)
                > EPSILON) {
            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put("previous", previousHealth);
            data.put("current", currentHealth);
            data.put(
                    "delta",
                    currentHealth - previousHealth
            );

            events.add(
                    inferred(
                            "HEALTH_CHANGED",
                            data
                    )
            );

            if (currentHealth < previousHealth) {
                Map<String, Object> damageData =
                        new LinkedHashMap<>();

                damageData.put(
                        "amount",
                        previousHealth - currentHealth
                );

                damageData.put(
                        "healthAfter",
                        currentHealth
                );

                events.add(
                        inferred(
                                "DAMAGE_RECEIVED",
                                damageData
                        )
                );
            }
        }

        if (currentHunger != previousHunger) {
            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put("previous", previousHunger);
            data.put("current", currentHunger);
            data.put(
                    "delta",
                    currentHunger - previousHunger
            );

            events.add(
                    inferred(
                            "HUNGER_CHANGED",
                            data
                    )
            );
        }

        if (!previousDead && currentDead) {
            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "position",
                    step.player().position()
            );

            events.add(
                    inferred(
                            "PLAYER_DIED",
                            data
                    )
            );
        }

        if (previousDead && !currentDead) {
            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "position",
                    step.player().position()
            );

            events.add(
                    inferred(
                            "PLAYER_RESPAWNED",
                            data
                    )
            );
        }

        ItemStackSnapshot currentMainHand =
                ItemStackSnapshot.from(
                        player.getMainHandItem()
                );

        ItemStackSnapshot currentOffhand =
                ItemStackSnapshot.from(
                        player.getOffhandItem()
                );

        boolean currentUsingItem =
                player.isUsingItem();

        if (previousUsingItem) {
            PendingGameEvent consumed =
                    consumedEvent(
                            previousUsedHand,
                            previousMainHand,
                            currentMainHand,
                            previousOffhand,
                            currentOffhand
                    );

            if (consumed != null) {
                events.add(consumed);
            }
        }

        Map<String, EntityHealthState> currentEntities =
                new HashMap<>();

        for (NearbyEntitySnapshot entity :
                step.nearbyEntities()) {

            if (entity.health() == null) {
                continue;
            }

            EntityHealthState current =
                    new EntityHealthState(
                            entity.entityType(),
                            entity.health()
                    );

            currentEntities.put(
                    entity.entityId(),
                    current
            );

            EntityHealthState previous =
                    previousEntityHealth.get(
                            entity.entityId()
                    );

            if (previous == null
                    || entity.health()
                    >= previous.health() - EPSILON
                    || !recent(
                    step.tick(),
                    lastAttackTick
            )) {
                continue;
            }

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "entityId",
                    entity.entityId()
            );

            data.put(
                    "entityType",
                    entity.entityType()
            );

            data.put(
                    "amount",
                    previous.health()
                            - entity.health()
            );

            data.put(
                    "healthAfter",
                    entity.health()
            );

            events.add(
                    inferred(
                            "DAMAGE_DEALT",
                            data
                    )
            );
        }

        previousHealth = currentHealth;
        previousHunger = currentHunger;
        previousDead = currentDead;
        previousUsingItem = currentUsingItem;
        previousUsedHand = currentUsingItem
                ? player.getUsedItemHand().name()
                : "NONE";
        previousMainHand = currentMainHand;
        previousOffhand = currentOffhand;
        previousEntityHealth =
                Map.copyOf(currentEntities);

        return List.copyOf(events);
    }

    List<PendingGameEvent> updateWorld(
            WorldCapture capture,
            long tick
    ) {
        AbsoluteWorld current =
                AbsoluteWorld.from(capture);

        if (previousWorld == null) {
            previousWorld = current;
            return List.of();
        }

        List<PendingGameEvent> events =
                new ArrayList<>();

        int minX = Math.max(
                previousWorld.minX(),
                current.minX()
        );

        int maxX = Math.min(
                previousWorld.maxX(),
                current.maxX()
        );

        int minY = Math.max(
                previousWorld.minY(),
                current.minY()
        );

        int maxY = Math.min(
                previousWorld.maxY(),
                current.maxY()
        );

        int minZ = Math.max(
                previousWorld.minZ(),
                current.minZ()
        );

        int maxZ = Math.min(
                previousWorld.maxZ(),
                current.maxZ()
        );

        if (minX <= maxX
                && minY <= maxY
                && minZ <= maxZ) {

            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        AbsoluteBlockKey key =
                                new AbsoluteBlockKey(
                                        x,
                                        y,
                                        z
                                );

                        WorldBlockSnapshot before =
                                previousWorld.blocks()
                                        .get(key);

                        WorldBlockSnapshot after =
                                current.blocks()
                                        .get(key);

                        String beforeId = before != null
                                ? before.blockId()
                                : "minecraft:air";

                        String afterId = after != null
                                ? after.blockId()
                                : "minecraft:air";

                        if (Objects.equals(
                                beforeId,
                                afterId
                        )) {
                            continue;
                        }

                        BlockPositionSnapshot position =
                                new BlockPositionSnapshot(
                                        x,
                                        y,
                                        z
                                );

                        if (!"minecraft:air".equals(beforeId)
                                && "minecraft:air".equals(afterId)
                                && recent(
                                tick,
                                lastAttackTick
                        )) {
                            Map<String, Object> data =
                                    new LinkedHashMap<>();

                            data.put("blockId", beforeId);
                            data.put("position", position);

                            events.add(
                                    inferred(
                                            "BLOCK_BROKEN",
                                            data
                                    )
                            );
                        }

                        if ("minecraft:air".equals(beforeId)
                                && !"minecraft:air".equals(afterId)
                                && recent(
                                tick,
                                lastUseTick
                        )) {
                            Map<String, Object> data =
                                    new LinkedHashMap<>();

                            data.put("blockId", afterId);
                            data.put("position", position);

                            events.add(
                                    inferred(
                                            "BLOCK_PLACED",
                                            data
                                    )
                            );
                        }
                    }
                }
            }
        }

        previousWorld = current;
        return List.copyOf(events);
    }

    private PendingGameEvent consumedEvent(
            String usedHand,
            ItemStackSnapshot previousMain,
            ItemStackSnapshot currentMain,
            ItemStackSnapshot previousOff,
            ItemStackSnapshot currentOff
    ) {
        if ("OFF_HAND".equals(usedHand)) {
            return itemDecreaseEvent(
                    "OFF_HAND",
                    previousOff,
                    currentOff
            );
        }

        if ("MAIN_HAND".equals(usedHand)) {
            return itemDecreaseEvent(
                    "MAIN_HAND",
                    previousMain,
                    currentMain
            );
        }

        PendingGameEvent main =
                itemDecreaseEvent(
                        "MAIN_HAND",
                        previousMain,
                        currentMain
                );

        if (main != null) {
            return main;
        }

        return itemDecreaseEvent(
                "OFF_HAND",
                previousOff,
                currentOff
        );
    }

    private PendingGameEvent itemDecreaseEvent(
            String hand,
            ItemStackSnapshot before,
            ItemStackSnapshot after
    ) {
        if (before == null) {
            return null;
        }

        int afterCount = 0;

        if (after != null
                && before.itemId()
                .equals(after.itemId())) {
            afterCount = after.count();
        }

        if (afterCount >= before.count()) {
            return null;
        }

        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put("hand", hand);
        data.put("itemId", before.itemId());
        data.put("countBefore", before.count());
        data.put("countAfter", afterCount);

        return inferred(
                "ITEM_CONSUMED",
                data
        );
    }

    private static PendingGameEvent inferred(
            String eventType,
            Map<String, Object> data
    ) {
        return new PendingGameEvent(
                eventType,
                "INFERRED",
                Map.copyOf(data)
        );
    }

    private static boolean recent(
            long currentTick,
            long interactionTick
    ) {
        return interactionTick != Long.MIN_VALUE
                && currentTick - interactionTick
                <= INTERACTION_EVENT_WINDOW_TICKS;
    }

    private record EntityHealthState(
            String entityType,
            float health
    ) {
    }

    private record AbsoluteBlockKey(
            int x,
            int y,
            int z
    ) {
    }

    private record AbsoluteWorld(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Map<AbsoluteBlockKey, WorldBlockSnapshot> blocks
    ) {
        static AbsoluteWorld from(
                WorldCapture capture
        ) {
            int originX = capture.origin().x();
            int originY = capture.origin().y();
            int originZ = capture.origin().z();

            WorldSnapshotBounds bounds =
                    capture.bounds();

            Map<AbsoluteBlockKey, WorldBlockSnapshot> blocks =
                    new HashMap<>();

            for (WorldBlockSnapshot block :
                    capture.blocks()) {

                blocks.put(
                        new AbsoluteBlockKey(
                                originX + block.dx(),
                                originY + block.dy(),
                                originZ + block.dz()
                        ),
                        block
                );
            }

            return new AbsoluteWorld(
                    originX + bounds.minDx(),
                    originX + bounds.maxDx(),
                    originY + bounds.minDy(),
                    originY + bounds.maxDy(),
                    originZ + bounds.minDz(),
                    originZ + bounds.maxDz(),
                    Map.copyOf(blocks)
            );
        }
    }
}
