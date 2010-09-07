package stomp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Message {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    // Message properties
    final Map<String, String> headers;

    // If content has been set
    byte[] content;

    // If received on a connection, which connection.
    private Connection connection;

    public static final String MESSAGE_ID = "message-id";
    public static final String DESTINATION = "destination";
    public static final String CORRELATION_ID = "correlation-id";
    public static final String EXPIRES = "expires";
    public static final String PERSISTENT = "persistent";
    public static final String PRIORITY = "priority";
    public static final String REPLY_TO = "reply-to";
    public static final String TYPE = "type";

    /**
     * Remote host address for multicast packets.
     */
    public static final String HOST_ADDRESS = "host-address";

    public static final String CLIENT_ID = "client-id";

    Message(Connection connection, Frame frame) {
        this.connection = connection;
        headers = frame.getHeaders();
        content = frame.getContent();
    }

    public Message() {
        headers = new HashMap<String, String>();
    }

    public void acknowledge(long waitMillis) throws IOException {
        connection.ack(getProperty(MESSAGE_ID), waitMillis);
    }

    public void setProperty(String key, String value) {
        headers.put(key, value);
    }

    public void setProperties (String...args) {
        for (int i = 0; i < args.length; ) {
            String key = args[i++];
            String val = args[i++];
            setProperty(key, val);
        }
    }

    public String getProperty(String key) {
        return headers.get(key);
    }

    public Iterator<String> getPropertyNames() {
        return headers.keySet().iterator();
    }

    public boolean propertyExists(String key) {
        return headers.containsKey(key);
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentUtf8() {
        if (content == null)
            return null;
        else
            return new String (content, UTF_8);
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public void setContentUtf8(String content) {
        if (content != null)
            this.content = content.getBytes(UTF_8);
        else
            this.content = null;
    }
}
