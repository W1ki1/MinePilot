package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonlWriter implements AutoCloseable {
    private static final int FLUSH_EVERY_RECORDS = 20;

    private final Gson gson =
            new GsonBuilder()
                    .serializeNulls()
                    .create();

    private final BufferedWriter writer;

    private int recordsSinceFlush = 0;

    public JsonlWriter(
            Path file
    ) throws IOException {
        Files.createDirectories(
                file.getParent()
        );

        this.writer =
                Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8
                );
    }

    public synchronized void write(
            Object object
    ) throws IOException {
        writer.write(
                gson.toJson(object)
        );

        writer.newLine();

        recordsSinceFlush++;

        if (recordsSinceFlush
                >= FLUSH_EVERY_RECORDS) {
            writer.flush();
            recordsSinceFlush = 0;
        }
    }

    public synchronized void flush()
            throws IOException {
        writer.flush();
        recordsSinceFlush = 0;
    }

    @Override
    public synchronized void close()
            throws IOException {
        writer.flush();
        writer.close();
    }
}
