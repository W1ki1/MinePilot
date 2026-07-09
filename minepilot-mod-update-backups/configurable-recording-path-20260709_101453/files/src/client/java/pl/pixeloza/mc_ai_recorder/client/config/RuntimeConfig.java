package pl.pixeloza.mc_ai_recorder.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeConfig {
    private static final String FILE_NAME =
            "minepilot-runtime.json";

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private String host =
            "192.168.0.11";

    private int port =
            5005;

    private int inferEveryTicks =
            2;

    private long heartbeatIntervalMs =
            1_000L;

    private long actionTimeoutMs =
            750L;

    private long reconnectIntervalMs =
            1_000L;

    private int connectTimeoutMs =
            2_000;

    private int readTimeoutMs =
            3_500;

    private boolean requireServerCapabilities =
            true;

    public RuntimeConfig() {
    }

    public static RuntimeConfig load() {
        Path path =
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(FILE_NAME);

        RuntimeConfig config =
                new RuntimeConfig();

        if (Files.isRegularFile(path)) {
            try (Reader reader =
                         Files.newBufferedReader(
                                 path,
                                 StandardCharsets.UTF_8
                         )) {
                RuntimeConfig loaded =
                        GSON.fromJson(
                                reader,
                                RuntimeConfig.class
                        );

                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException e) {
                System.out.println(
                        "[MC AI Recorder] Could not read "
                                + path
                                + ": "
                                + e.getMessage()
                                + ". Using defaults."
                );
            }
        }

        config.validateAndNormalize();

        try {
            Files.createDirectories(
                    path.getParent()
            );

            try (Writer writer =
                         Files.newBufferedWriter(
                                 path,
                                 StandardCharsets.UTF_8
                         )) {
                GSON.toJson(
                        config,
                        writer
                );
            }
        } catch (IOException e) {
            System.out.println(
                    "[MC AI Recorder] Could not write "
                            + path
                            + ": "
                            + e.getMessage()
            );
        }

        System.out.println(
                "[MC AI Recorder] Runtime config: "
                        + path
                        + " endpoint="
                        + config.endpoint()
        );

        return config;
    }

    private void validateAndNormalize() {
        if (host == null
                || host.trim().isEmpty()) {
            host = "127.0.0.1";
        } else {
            host = host.trim();
        }

        port = clamp(
                port,
                1,
                65_535
        );

        inferEveryTicks = clamp(
                inferEveryTicks,
                1,
                20
        );

        heartbeatIntervalMs = clamp(
                heartbeatIntervalMs,
                250L,
                10_000L
        );

        actionTimeoutMs = clamp(
                actionTimeoutMs,
                100L,
                5_000L
        );

        reconnectIntervalMs = clamp(
                reconnectIntervalMs,
                250L,
                30_000L
        );

        connectTimeoutMs = clamp(
                connectTimeoutMs,
                250,
                30_000
        );

        readTimeoutMs = clamp(
                readTimeoutMs,
                1_000,
                30_000
        );
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static long clamp(
            long value,
            long min,
            long max
    ) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int inferEveryTicks() {
        return inferEveryTicks;
    }

    public long heartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public long actionTimeoutMs() {
        return actionTimeoutMs;
    }

    public long reconnectIntervalMs() {
        return reconnectIntervalMs;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean requireServerCapabilities() {
        return requireServerCapabilities;
    }

    public String endpoint() {
        return host
                + ":"
                + port;
    }
}
