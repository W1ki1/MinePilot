package pl.pixeloza.mc_ai_recorder.client.inference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class InferenceClient implements Closeable {
    private final Gson gson = new GsonBuilder().create();

    private final String host;
    private final int port;

    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public InferenceClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public synchronized AiAction predict(byte[] imageBytes) throws IOException {
        ensureConnected();

        output.writeInt(imageBytes.length);
        output.write(imageBytes);
        output.flush();

        int responseLength = input.readInt();

        if (responseLength <= 0 || responseLength > 1024 * 1024) {
            throw new IOException("Invalid response length: " + responseLength);
        }

        byte[] responseBytes = input.readNBytes(responseLength);
        String json = new String(responseBytes);

        return gson.fromJson(json, AiAction.class);
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }

        System.out.println("[MC AI] Connecting to " + host + ":" + port);

        socket = new Socket(host, port);

        System.out.println("[MC AI] Connected!");

        socket.setTcpNoDelay(true);

        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public synchronized void close() throws IOException {
        if (socket != null) {
            socket.close();
        }

        socket = null;
        input = null;
        output = null;
    }
}