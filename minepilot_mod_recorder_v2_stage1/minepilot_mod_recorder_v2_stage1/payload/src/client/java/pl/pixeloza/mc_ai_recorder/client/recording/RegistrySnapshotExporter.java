package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class RegistrySnapshotExporter {
    private static final Gson GSON =
            new GsonBuilder()
                    .serializeNulls()
                    .setPrettyPrinting()
                    .create();

    private RegistrySnapshotExporter() {
    }

    public static String export(
            Path datasetRoot
    ) throws IOException {
        List<String> items =
                sortedKeys(
                        BuiltInRegistries.ITEM
                );

        List<String> blocks =
                sortedKeys(
                        BuiltInRegistries.BLOCK
                );

        List<String> entityTypes =
                sortedKeys(
                        BuiltInRegistries.ENTITY_TYPE
                );

        List<String> menuTypes =
                sortedKeys(
                        BuiltInRegistries.MENU
                );

        String hashHex =
                sha256(
                        canonicalText(
                                items,
                                blocks,
                                entityTypes,
                                menuTypes
                        )
                );

        String registryHash =
                "sha256:" + hashHex;

        RegistrySnapshot snapshot =
                new RegistrySnapshot(
                        RecordingSchema.MINECRAFT_VERSION,
                        registryHash,
                        items,
                        blocks,
                        entityTypes,
                        menuTypes
                );

        Path registriesDir =
                datasetRoot.resolve(
                        "registries"
                );

        Files.createDirectories(
                registriesDir
        );

        Path output =
                registriesDir.resolve(
                        "registry_"
                                + hashHex
                                + ".json"
                );

        if (!Files.exists(output)) {
            Files.writeString(
                    output,
                    GSON.toJson(snapshot),
                    StandardCharsets.UTF_8
            );
        }

        return registryHash;
    }

    private static <T> List<String> sortedKeys(
            Registry<T> registry
    ) {
        List<String> values =
                new ArrayList<>();

        for (Identifier identifier :
                registry.keySet()) {
            values.add(
                    identifier.toString()
            );
        }

        values.sort(
                String::compareTo
        );

        return List.copyOf(values);
    }

    private static String canonicalText(
            List<String> items,
            List<String> blocks,
            List<String> entityTypes,
            List<String> menuTypes
    ) {
        StringBuilder builder =
                new StringBuilder();

        appendCategory(
                builder,
                "items",
                items
        );

        appendCategory(
                builder,
                "blocks",
                blocks
        );

        appendCategory(
                builder,
                "entityTypes",
                entityTypes
        );

        appendCategory(
                builder,
                "menuTypes",
                menuTypes
        );

        return builder.toString();
    }

    private static void appendCategory(
            StringBuilder builder,
            String category,
            List<String> values
    ) {
        builder.append('[')
                .append(category)
                .append(']')
                .append('\n');

        for (String value :
                values) {
            builder.append(value)
                    .append('\n');
        }
    }

    private static String sha256(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] bytes =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    e
            );
        }
    }
}

record RegistrySnapshot(
        String minecraftVersion,
        String registryHash,
        List<String> items,
        List<String> blocks,
        List<String> entityTypes,
        List<String> menuTypes
) {
}
