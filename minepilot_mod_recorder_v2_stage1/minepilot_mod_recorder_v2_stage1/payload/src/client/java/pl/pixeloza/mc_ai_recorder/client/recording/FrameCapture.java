package pl.pixeloza.mc_ai_recorder.client.recording;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FrameCapture implements AutoCloseable {
    private static final int OUTPUT_WIDTH =
            RecordingSchema.FRAME_WIDTH;

    private static final int OUTPUT_HEIGHT =
            RecordingSchema.FRAME_HEIGHT;

    private final Path framesDir;
    private final BlockingQueue<FrameJob> queue = new LinkedBlockingQueue<>();
    private final Thread writerThread;

    private volatile boolean running = true;
    private volatile IOException lastError = null;

    public FrameCapture(Path framesDir) throws IOException {
        this.framesDir = framesDir;
        Files.createDirectories(framesDir);

        this.writerThread = new Thread(this::writerLoop, "MC-AI-Frame-Writer");
        this.writerThread.start();
    }

    public void capture(Minecraft client, String frameName) throws IOException {
        if (lastError != null) {
            throw lastError;
        }

        Path output = framesDir.resolve(frameName);

        Screenshot.takeScreenshot(client.getMainRenderTarget(), (NativeImage original) -> {
            try {
                NativeImage resized = resizeNearest(original, OUTPUT_WIDTH, OUTPUT_HEIGHT);
                queue.put(new FrameJob(output, resized));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                original.close();
            }
        });
    }

    private NativeImage resizeNearest(NativeImage source, int targetWidth, int targetHeight) {
        NativeImage result = new NativeImage(targetWidth, targetHeight, false);

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        for (int y = 0; y < targetHeight; y++) {
            int srcY = y * sourceHeight / targetHeight;

            for (int x = 0; x < targetWidth; x++) {
                int srcX = x * sourceWidth / targetWidth;
                int color = source.getPixel(srcX, srcY);
                result.setPixel(x, y, color);
            }
        }

        return result;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public IOException getLastError() {
        return lastError;
    }

    private void writerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                FrameJob job = queue.poll(100, TimeUnit.MILLISECONDS);

                if (job == null) {
                    continue;
                }

                try {
                    job.image.writeToFile(job.output);
                } catch (IOException e) {
                    lastError = e;
                } finally {
                    job.image.close();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;

        try {
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (lastError != null) {
            throw lastError;
        }
    }

    private record FrameJob(Path output, NativeImage image) {
    }
}