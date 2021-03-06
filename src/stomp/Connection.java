package stomp;

import javax.net.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Connection {

    // Constant for Utf8 conversion
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    // Active subscriptions.  Updated / queried by separate threads so must be thread-safe.
    private final Map<String, Consumer> subscriptions = new ConcurrentHashMap<String, Consumer>();

    // URI the connection was created against.
    private final URI uri;

    // Pending receipts - all access is manually synchronized. (TODO: purge periodically?)
    private final Set<String> receipts = new HashSet<String>();

    // Set to 'true' as soon as CONNECTED frame is received from server.
    protected boolean connected;

    // If an error occurs during connection, tcp connection needs the error so it can throw the exception.
    protected String lastError;
    protected Exception lastException;

    private Thread thread;
    protected boolean closed = false;
    private volatile boolean paused;

    public static final String SUBSCRIBE_SELECTOR = "selector";
    public static final String SUBSCRIBE_ACKMODE = "ack";
    public static final String ACKMODE_AUTO = "auto";
    public static final String ACKMODE_CLIENT = "client";

    //----------------------------------------------------------------------------------------------------------- Public

    /**
     * Open a connection to the given url.
     * <p/>
     * Supports url's such as:
     * <pre>
     *  stomp://localhost:61613
     *  stomp://user:password@hostname:61613?soTimeout=3000
     *  stomp://224.1.2.3:61613?ttl=12&soTimeout=3000
     *  stomp:buffer
     * </pre>
     * <p/>
     * The examples above are, respectively: (1) a simple local connection with no authentication, (2) a connection to a
     * host with a username/password (sent in plain text ... but far better than nothing!), (3) a multicast group, and
     * (4) an output buffer.
     *
     * @param url             The url.
     * @return A connection instance of the proper type, already connected.
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public static Connection openConnection(String url) throws IOException {
        return openConnection(url, null, null);
    }

    public static Connection openConnection(String url, String login, String passcode) throws IOException {

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException (e);
        }

        if (url.startsWith("stomp:buffer")) {
            return new BufferConnection(uri);
        }

        boolean ssl = false;
        if (uri.getScheme().equals ("stomp+ssl")) {
            ssl = true;
        } else if (!"stomp".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid protocol: " + url);
        }

        int port = uri.getPort();
        if (port == -1) {
            throw new IllegalArgumentException("Missing port: " + url);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Missing host: " + url);
        }

        Properties properties = new Properties();
        String info = uri.getUserInfo();
        if (info != null && info.trim().length() > 0) {
            int dot = info.indexOf(':');
            if (dot == -1) {
                properties.setProperty("login", info);
            } else {
                properties.setProperty("login", info.substring(0, dot));
                properties.setProperty("passcode", info.substring(dot + 1));
            }
        }

        if (login != null)
            properties.setProperty("login", login);
        if (passcode != null)
            properties.setProperty("passcode", passcode);

        String s = uri.getQuery();
        if (s != null) {
            StringReader reader = new StringReader(s.replace('&', '\n'));
            properties.load(reader);
            reader.close();
        }

        Connection connection;
        InetAddress hostAddy = InetAddress.getByName(host);
        if (hostAddy.isMulticastAddress()) {
            // Binding the multicast socket varies from Windows to other platforms.
            // On Linux we must bind to the actual multicast address, otherwise it binds to "0.0.0.0:port" and will
            // then receive all multicast traffic received by the local host for the same port number.
            // Whereas Windows apparently will filter based on our group membership.
            MulticastSocket socket;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                String bindAddress = System.getProperty ("stomp.udp.bind");
                if (bindAddress != null) {
                    socket = new MulticastSocket(new InetSocketAddress(bindAddress, port));
                } else {
                    socket = new MulticastSocket(port);
                }
            } else {
                // Linux, Solaris, OSX ...
                InetSocketAddress mcastBindAddress = new InetSocketAddress(hostAddy, port);
                socket = new MulticastSocket(mcastBindAddress);
            }
            socket.joinGroup(hostAddy);
            connection = new UdpConnection(uri, socket, properties);
        } else {
            SocketFactory factory;
            if (ssl) {
                // Use reflection so as not to initialize SSL subsystem when not in use.
                try {
                    Class clazz = Class.forName("javax.net.ssl.SSLSocketFactory");
                    Method method = clazz.getDeclaredMethod("getDefault");
                    factory = (SocketFactory) method.invoke(null);
                } catch (Exception e) {
                    throw new IOException("Error initializing SSL sockets: " + e, e);
                }
            } else {
                factory = SocketFactory.getDefault();
            }
            Socket socket = factory.createSocket();
            try {
                socket.connect(new InetSocketAddress(hostAddy, port), 2000);
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e1) {
                    // ignore
                }
                throw e;
            }
            connection = new TcpConnection(uri, socket, properties);
        }
        return connection;
    }

    protected Connection(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    protected void start () {
        if (thread != null)
            throw new IllegalStateException ("Already started.");
        thread = new Thread((Runnable) this, "STOMP " + getClass().getSimpleName());
        thread.start();
    }

    public void pause (boolean paused) {
        this.paused = paused;

        // Notify the receiver thread if its sleeping
        if (!paused)
            synchronized (this) {
                this.notifyAll();
            }
    }

    public boolean isPaused() {
        return paused;
    }

    public void testConnection() {

    }

    public void close() {
        // Close, if not already in the act of closing.
        if (!closed) {
            closed = true;
            disconnect();
            try {
                if (thread != null) {
                    thread.join(3000); // Try to wait for clean shutdown, but not forever.
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void ack (String messageId, long waitMillis) throws IOException {
        Frame frame = new Frame(Frame.TYPE_ACK);
        frame.getHeaders().put("message-id", messageId);
        transmit1(frame, waitMillis);
    }

    public void subscribe (String destination, Consumer consumer, long waitMillis, String...headers) throws IOException {
        if (subscriptions.containsKey(destination))
            throw new IOException ("Already subscribed to '" + destination + "'.");
        Frame frame = new Frame(Frame.TYPE_SUBSCRIBE, null, null);
        frame.getHeaders().put("destination", destination);
        String id = createSubscriptionId(destination);
        subscriptions.put (id, consumer); // Store subscription -> consumer FIRST, messages start coming in immediately!
        frame.getHeaders().put("id", id);
        if (headers != null) {
            for (int i = 0; i < headers.length; ) {
                String key = headers[i++];
                String val = headers[i++];
                frame.getHeaders().put (key, val);
            }
        }
        transmit1(frame, waitMillis);
    }

    public void unsubscribe (String destination, long waitMillis) throws IOException {
        if (!subscriptions.containsKey(destination))
            throw new IOException ("Not subscribed to '" + destination + "'.");
        Frame frame = new Frame(Frame.TYPE_UNSUBSCRIBE);
        frame.getHeaders().put("destination", destination);
        transmit1(frame, waitMillis);
        subscriptions.remove (destination);
    }

    public void send (String destination, Message message, long waitMillis) throws IOException {
        Frame frame = new Frame(message);
        frame.getHeaders().put("destination", destination);
        transmit1(frame, waitMillis);
    }

    public boolean isConnected() {
        return connected;
    }

    protected abstract void transmit(Frame frame, long waitMillis) throws IOException;
    protected abstract void disconnect();
    protected abstract String createSubscriptionId(String destination);

    //--------------------------------------------------------------------------------------------------------- Internal

    protected void frameReceived(Frame frame) {
        if (frame.getType().equals(Frame.TYPE_MESSAGE)) {
            // If there's a subscription header, then we assigned it.
            String subscriptionId = frame.getHeaders().get("subscription");
            if (subscriptionId == null) {

                // No subscription, just use the destination (from udp and buffer connections)
                subscriptionId = frame.getHeaders().get("destination");
            }

            // Find the consumer and dispatch to the listener.
            Consumer consumer = subscriptions.get (subscriptionId);
            if (consumer != null) {
                try {
                    Message message = new Message(this, frame);
                    consumer.onMessage(message);
                } catch (Exception e) {
                    publishError("Unhandled exception in consumer: " + e, e);
                }
            } else {
                String s;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    frame.write(dos);
                    s = baos.toString();
                } catch (IOException e) {
                    s = "(unable to display frame content)";
                }

                publishError("Message received but no consumers: \n" + s, null);
            }
        } else if (frame.getType().equals(Frame.TYPE_CONNECTED)) {
            setConnected();
        } else if (frame.getType().equals(Frame.TYPE_RECEIPT)) {
            synchronized (receipts) {
                receipts.add(frame.getHeaders().get("receipt-id"));
                receipts.notify();
            }

        } else if (frame.getType().equals(Frame.TYPE_ERROR)) {
            publishError (new String (frame.getContent(), UTF_8), null);
        } else {
            publishError ("Command not implemented: " + frame.getType(), null);
        }

        while (paused) {
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    protected void publishError(String message, Exception ex) {
        lastError = message;
        lastException = ex;
        int notifyCount = 0;
        for (Consumer consumer : subscriptions.values()) {
            consumer.onError(this, message, ex);
            notifyCount++;
        }

        // If nobody was notified, print to stderr so there's at least SOME visibility of a problem.
        if (notifyCount == 0) {
            System.err.printf("Error received (no listeners to notify!): '%s'\n", message);
            if (ex != null) {
                ex.printStackTrace();
            }
        }
    }

    private void setConnected() {
        synchronized (this) {
            connected = true;
            notifyAll();
        }
    }

    protected void awaitStatus (long timeout) throws InterruptedException {
        synchronized (this) {
            this.wait(timeout);
        }
    }

    private void transmit1(Frame frame, long waitMillis) throws IOException {
        if (paused)
            throw new IOException ("Can't send while paused.");
        transmit (frame, waitMillis);
    }

    private int nextReceiptId;

    protected String addReceipt(Frame frame) {
        String receipt;
        synchronized (receipts) {
            nextReceiptId++;
            if (nextReceiptId > 999999)
                nextReceiptId = 0;
            receipt = Integer.toString(nextReceiptId);
        }
        frame.getHeaders().put("receipt", receipt);
        return receipt;
    }

    protected void waitOnReceipt(String receipt, long waitMillis) throws IOException {
        if (Thread.currentThread() == thread)
            throw new IOException ("Attempt to performing blocking operation using connection thread.");

        final long waitOver = System.currentTimeMillis() + waitMillis;
        synchronized (receipts) {
            while (!receipts.contains(receipt)) {
                try {
                    receipts.wait(waitMillis);
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }

                if (!receipts.contains(receipt) && System.currentTimeMillis() > waitOver) {
                    throw new IOException ("Timeout waiting for receipt '" + receipt + "'.");
                }
            }
            receipts.remove(receipt);
        }
    }
}
