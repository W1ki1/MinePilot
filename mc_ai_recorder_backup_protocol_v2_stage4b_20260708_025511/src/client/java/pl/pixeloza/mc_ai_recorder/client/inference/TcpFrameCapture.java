package pl.pixeloza.mc_ai_recorder.client.inference;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

public class TcpFrameCapture {
    private static final int OUTPUT_WIDTH = 224;
    private static final int OUTPUT_HEIGHT = 224;

    public void captureJpegAsync(Minecraft client, Consumer<byte[]> onSuccess, Consumer<Exception> onError) {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), (NativeImage original) -> {
            try {
                NativeImage resized = resizeNearest(original, OUTPUT_WIDTH, OUTPUT_HEIGHT);
                BufferedImage bufferedImage = toBufferedImage(resized);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "jpg", baos);

                byte[] jpeg = baos.toByteArray();

                resized.close();
                onSuccess.accept(jpeg);
            } catch (Exception e) {
                onError.accept(e);
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

    private BufferedImage toBufferedImage(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixel(x, y);

                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;

                int rgb = (r << 16) | (g << 8) | b;
                buffered.setRGB(x, y, rgb);
            }
        }

        return buffered;
    }
}