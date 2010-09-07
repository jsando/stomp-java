package stomp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.URI;
import java.util.Properties;
import java.util.UUID;

/**
 * Send/receive stomp frames over UDP multicast.
 * <p/>
 * A couple of things to note:
 * - We don't set loopback to disable, so we have to filter out receiving our own transmission.  This is because
 * testing on linux has shown that if we enable this setting, then we get NO packets on the local machine, even
 * to other processes.
 * - We use a separate transmit socket, to avoid a "Invalid argument" exception on send when Ipv6 is enabled
 */
public final class UdpConnection extends Connection implements Runnable {

    private final MulticastSocket rxSocket;
    private final MulticastSocket txSocket;
    private final InetSocketAddress destination;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    private final DataOutputStream dataOut = new DataOutputStream(out);

    // If we transmit, we'll generate a UUID and use it to filter out our own traffic.
    private String clientId;

    public UdpConnection(URI uri, MulticastSocket rxSocket, Properties properties) throws IOException {
        super(uri);

        this.rxSocket = rxSocket;

        // All packets sent by this connection will go to the multicast group
        destination = new InetSocketAddress(uri.getHost(), uri.getPort());

        // Use a separate transmit socket bound to a random port, if this connection receives traffic from this remote
        // host/port it will filter it out.
        txSocket = new MulticastSocket();

        if (properties.containsKey("soTimeout")) {
            int timeout = Integer.parseInt(properties.getProperty("soTimeout"));
            rxSocket.setSoTimeout(timeout);
            txSocket.setSoTimeout(timeout);
        }

        int ttl = 32;
        if (properties.containsKey("ttl")) {
            ttl = Integer.parseInt(properties.getProperty("ttl"));
        }
        txSocket.setTimeToLive(ttl);

        if (properties.containsKey("trafficClass")) {
            txSocket.setTrafficClass(Integer.parseInt(properties.getProperty("trafficClass")));
        }

        // Disable receiving our own outbound packets.
//        txSocket.setLoopbackMode(true);

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
            if (clientId == null) {
                clientId = UUID.randomUUID().toString();
//                System.out.printf("Generated client-id: %s\n", clientId);
            }
            out.reset();
            frame.getHeaders().put(Message.CLIENT_ID, clientId);
            frame.write(dataOut);

            // NOTE: This stupid jdk method creates A NEW COPY OF THE BYTE ARRAY!!! Future project,
            //  make a OutputStream that writes to our same byte[].
            final byte[] data = out.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, 0, data.length, destination);
            txSocket.send(packet);
        }
    }

    @Override
    protected void disconnect() {
        rxSocket.close();
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
                rxSocket.receive(packet);
                final int dataLength = packet.getLength();
                final DataInputStream is = new DataInputStream(new ByteArrayInputStream(buffer, 0, dataLength));
                Frame frame = Frame.read(is);
                String rxClientId = frame.getHeaders().get(Message.CLIENT_ID);
                String txClientId = clientId;
                if (rxClientId != null && txClientId != null && rxClientId.equals(txClientId)) {
                    //System.out.printf("Local loopback ignored.\n");
                } else {
                    frame.getHeaders().put(Message.HOST_ADDRESS, packet.getAddress().getHostAddress());
                    frameReceived(frame);
                }
            }
        } catch (Exception e) {
            if (!closed)
                publishError(e.getMessage(), e);
        }
        closed = true;
        rxSocket.close();
    }
}
