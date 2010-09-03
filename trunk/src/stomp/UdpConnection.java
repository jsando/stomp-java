package stomp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Properties;

public final class UdpConnection extends Connection implements Runnable {

    private final MulticastSocket socket;

    public UdpConnection(URI uri, MulticastSocket socket, Properties properties) throws IOException {
        super (uri);
        
        this.socket = socket;

        if (properties.containsKey("soTimeout")) {
            socket.setSoTimeout(Integer.parseInt (properties.getProperty("soTimeout")));
        }

        if (properties.containsKey("ttl")) {
            socket.setTimeToLive(Integer.parseInt (properties.getProperty("ttl")));
        } else {
            socket.setTimeToLive(32);
        }

        if (properties.containsKey("trafficClass")) {
            socket.setTrafficClass(Integer.parseInt (properties.getProperty("trafficClass")));
        }

        // Disable receiving our own outbound packets.
        socket.setLoopbackMode(true);

        // Allow binding multiple sockets to same port
        socket.setReuseAddress(true);

        super.start();
    }

    @Override
    protected void transmit(Frame frame) throws IOException {
        if (frame.getType().equals(Frame.TYPE_SEND)) {
            frame.setType(Frame.TYPE_MESSAGE);
        }

        // Don't send subscribe frames, or any other frames for that matter.
        if (!frame.getType().equals(Frame.TYPE_MESSAGE)) {
            return;
        }

        synchronized (this) {
            SocketAddress destination = null;
            String destAddress = frame.getHeaders().get("destination");
            if (destAddress != null) {
                int pos = destAddress.indexOf(':');
                if (pos > 0) {
                    int port = -1;
                    try {
                        port = Integer.parseInt(destAddress.substring(pos + 1));
                    } catch (NumberFormatException e) {
                        // ignore, port will be invalid and we'll fail it.
                    }
                    if (port != -1) {
                        destAddress = destAddress.substring(0, pos);
                        destination = new InetSocketAddress(destAddress, port);
                    }
                }
            }

            if (destination == null) {
                throw new IOException("Bad multicast address: " + destAddress);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(32768);
            DataOutputStream dataOut = new DataOutputStream(out);
            frame.write(dataOut);
            final byte[] data = out.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, 0, data.length, destination);
            socket.send(packet);
        }
    }

    @Override
    protected void disconnect() {
        socket.close();
    }

    @Override
    protected String createSubscriptionId(String destination) {
        return destination;
    }

    public void run() {
        final byte[] buffer = new byte[0x10000]; // theoretical max for udp
        final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            while (!isClosed()) {
                socket.receive(packet);
                final int dataLength = packet.getLength();
                final DataInputStream is = new DataInputStream(new ByteArrayInputStream(buffer, 0, dataLength));
                Frame frame = Frame.read (is);
                frameReceived(frame);
            }
        } catch (Exception e) {
            if (!closed)
                publishError(e.getMessage(), e);
        }
        closed = true;
        socket.close();
    }
}
