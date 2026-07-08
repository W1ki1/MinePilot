package pl.pixeloza.mc_ai_recorder.client.inference;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.function.Consumer;

public class TcpFrameCapture {
    private static final int OUTPUT_WIDTH = 224;
    private static final int OUTPUT_HEIGHT = 224;
    private static final float JPEG_QUALITY = 0.80f;

    public void captureJpegAsync(
            Minecraft client,
            Consumer<byte[]> onSuccess,
            Consumer<Exception> onError
    ) {
        Screenshot.takeScreenshot(
                client.getMainRenderTarget(),
                original -> {
                    NativeImage resized = null;

                    try {
                        resized = resizeNearest(
                                original,
                                OUTPUT_WIDTH,
                                OUTPUT_HEIGHT
                        );

                        BufferedImage bufferedImage =
                                toBufferedImage(
                                        resized
                                );

                        byte[] jpeg =
                                encodeJpeg(
                                        bufferedImage
                                );

                        onSuccess.accept(
                                jpeg
                        );
                    } catch (Exception e) {
                        onError.accept(
                                e
                        );
                    } finally {
                        if (resized != null) {
                            resized.close();
                        }

                        original.close();
                    }
                }
        );
    }

    private byte[] encodeJpeg(
            BufferedImage image
    ) throws Exception {
        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName(
                        "jpeg"
                );

        if (!writers.hasNext()) {
            throw new IllegalStateException(
                    "No JPEG ImageWriter is available"
            );
        }

        ImageWriter writer =
                writers.next();

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream imageOutput =
                     new MemoryCacheImageOutputStream(
                             output
                     )) {
            writer.setOutput(
                    imageOutput
            );

            ImageWriteParam parameters =
                    writer.getDefaultWriteParam();

            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(
                        ImageWriteParam.MODE_EXPLICIT
                );

                parameters.setCompressionQuality(
                        JPEG_QUALITY
                );
            }

            writer.write(
                    null,
                    new IIOImage(
                            image,
                            null,
                            null
                    ),
                    parameters
            );

            imageOutput.flush();
        } finally {
            writer.dispose();
        }

        return output.toByteArray();
    }

    private NativeImage resizeNearest(
            NativeImage source,
            int targetWidth,
            int targetHeight
    ) {
        NativeImage result =
                new NativeImage(
                        targetWidth,
                        targetHeight,
                        false
                );

        int sourceWidth =
                source.getWidth();

        int sourceHeight =
                source.getHeight();

        for (int y = 0;
             y < targetHeight;
             y++) {
            int sourceY =
                    y
                            * sourceHeight
                            / targetHeight;

            for (int x = 0;
                 x < targetWidth;
                 x++) {
                int sourceX =
                        x
                                * sourceWidth
                                / targetWidth;

                int color =
                        source.getPixel(
                                sourceX,
                                sourceY
                        );

                result.setPixel(
                        x,
                        y,
                        color
                );
            }
        }

        return result;
    }

    private BufferedImage toBufferedImage(
            NativeImage image
    ) {
        int width =
                image.getWidth();

        int height =
                image.getHeight();

        BufferedImage buffered =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_RGB
                );

        for (int y = 0;
             y < height;
             y++) {
            for (int x = 0;
                 x < width;
                 x++) {
                int abgr =
                        image.getPixel(
                                x,
                                y
                        );

                int blue =
                        (abgr >> 16)
                                & 0xFF;

                int green =
                        (abgr >> 8)
                                & 0xFF;

                int red =
                        abgr
                                & 0xFF;

                int rgb =
                        (red << 16)
                                | (green << 8)
                                | blue;

                buffered.setRGB(
                        x,
                        y,
                        rgb
                );
            }
        }

        return buffered;
    }
}
