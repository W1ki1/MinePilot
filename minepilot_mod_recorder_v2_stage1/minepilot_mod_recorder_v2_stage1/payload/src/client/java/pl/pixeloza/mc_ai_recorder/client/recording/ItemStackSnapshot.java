package pl.pixeloza.mc_ai_recorder.client.recording;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ItemStackSnapshot(
        String itemId,
        int count,
        Integer damage,
        Integer maxDamage,
        String customName,
        List<EnchantmentSnapshot> enchantments
) {
    public static ItemStackSnapshot from(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Integer damage = null;
        Integer maxDamage = null;

        if (stack.isDamageableItem()) {
            damage = stack.getDamageValue();
            maxDamage = stack.getMaxDamage();
        }

        Component customNameComponent =
                stack.getCustomName();

        String customName =
                customNameComponent != null
                        ? customNameComponent.getString()
                        : null;

        List<EnchantmentSnapshot> enchantments =
                new ArrayList<>();

        for (var entry :
                stack.getEnchantments().entrySet()) {

            String enchantmentId =
                    entry.getKey()
                            .unwrapKey()
                            .map(key ->
                                    key.identifier()
                                            .toString()
                            )
                            .orElse("unknown");

            enchantments.add(
                    new EnchantmentSnapshot(
                            enchantmentId,
                            entry.getIntValue()
                    )
            );
        }

        return new ItemStackSnapshot(
                BuiltInRegistries.ITEM
                        .getKey(stack.getItem())
                        .toString(),
                stack.getCount(),
                damage,
                maxDamage,
                customName,
                List.copyOf(enchantments)
        );
    }

    public String fingerprint() {
        StringBuilder builder =
                new StringBuilder();

        builder.append(itemId)
                .append(':')
                .append(count)
                .append(':')
                .append(damage)
                .append(':')
                .append(maxDamage)
                .append(':')
                .append(customName);

        for (EnchantmentSnapshot enchantment :
                enchantments) {
            builder.append('|')
                    .append(enchantment.enchantmentId())
                    .append('=')
                    .append(enchantment.level());
        }

        return builder.toString();
    }
}

record EnchantmentSnapshot(
        String enchantmentId,
        int level
) {
}
