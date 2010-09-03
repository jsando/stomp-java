package stomp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.Properties;

public final class TcpConnection extends Connection implements Runnable {

    private final DataOutputStream output;
    private final DataInputStream input;
    private final Socket socket;
    private int nextSubscriptionId = 1;

    public TcpConnection(URI uri, Socket socket, Properties properties) throws IOException {
        super(uri);
        
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.socket = socket;

        if (properties.containsKey("soTimeout")) {
            socket.setSoTimeout(Integer.parseInt (properties.getProperty("soTimeout")));
        }

        super.start();

        // Connect to the server
        Frame connect = new Frame(Frame.TYPE_CONNECT);
        connect.getHeaders().put("login", properties.getProperty("login", ""));
        connect.getHeaders().put("passcode", properties.getProperty("passcode", ""));
        transmit(connect);

        try {
            long totalWait = 0;
            while (!isConnected() && !isClosed() && lastError == null && lastException == null) {
                awaitStatus(2000);
                totalWait += 2000;
                if (totalWait > 30000) {
                    close();
                    throw new IOException ("Failed to connect before timeout.");
                }
            }
            if (isClosed()) throw new IOException(lastError, lastException);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    protected void transmit(Frame frame) throws IOException {
        synchronized (output) {
            frame.write(output);
        }
    }

    @Override
    protected void disconnect() {
        try {
            socket.shutdownInput();
        } catch (IOException e) {
            publishError("Error in shutdown: " + e, e);
        }
    }

    @Override
    protected String createSubscriptionId(String destination) {
        String id = Integer.toString(nextSubscriptionId);
        nextSubscriptionId++;
        if (nextSubscriptionId > 1000000)
            nextSubscriptionId = 1;
        return id;
    }

    public void run() {
        while (!isClosed()) {
            try {
                Frame frame = Frame.read(input);
                if (frame == null)
                    break;
                frameReceived(frame);
            } catch (IOException e) {
                publishError(e.getMessage(), e);
                break;
            }
        }
        try {
            transmit(new Frame(Frame.TYPE_DISCONNECT, null, null));
        } catch (IOException e) {
            publishError("Disconnect failed (ignored): " + e, e);
        }
        try {
            socket.close();
        } catch (IOException e) {
            // ignore?
        }
        closed = true;
    }
}
