package pl.pixeloza.mc_ai_recorder.client.recording;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonlWriter implements AutoCloseable {
    private final Gson gson =
            new GsonBuilder().create();

    private final BufferedWriter writer;

    public JsonlWriter(Path file) throws IOException {
        Files.createDirectories(
                file.getParent()
        );

        this.writer =
                Files.newBufferedWriter(file);
    }

    public synchronized void write(
            Object object
    ) throws IOException {
        writer.write(
                gson.toJson(object)
        );

        writer.newLine();
    }

    @Override
    public synchronized void close()
            throws IOException {

        writer.flush();
        writer.close();
    }
}