package stomp;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Frame {

    public static final String TYPE_SEND = "SEND";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_SUBSCRIBE = "SUBSCRIBE";
    public static final String TYPE_UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String TYPE_BEGIN = "BEGIN";
    public static final String TYPE_COMMIT = "COMMIT";
    public static final String TYPE_ABORT = "ABORT";
    public static final String TYPE_DISCONNECT = "DISCONNECT";
    public static final String TYPE_CONNECT = "CONNECT";
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_RECEIPT = "RECEIPT";
    public static final String TYPE_CONNECTED = "CONNECTED";
    public static final String TYPE_ERROR = "ERROR";

    private String type;
    private final Map<String, String> headers;
    private byte[] content;

    public Frame(String type) {
        this (type, null, null);
    }

    public Frame(String type, Map<String, String> headers, byte[] content) {
        this.type = type;
        if (headers == null)
            headers = new HashMap<String, String>();
        this.headers = headers;
        this.content = content;
    }

    public Frame (Message message) {
        this.type = TYPE_SEND;
        this.headers = message.headers;
        this.content = message.content;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static Frame read (DataInputStream input) throws IOException {
        Frame frame = null;

        String command;
        while ((command = input.readLine()) != null) {
            // keep looping until EOF or we get a command
            command = command.trim();
            if (command.length() > 0) {
                break;
            }
        }

        if (command != null) {
            frame = new Frame(command);

            // Get headers
            int expectedLength = -1;
            String header;
            while ((header = input.readLine()) != null && header.length() > 0) {
                if (header.length() == 0) {
                    continue;
                }
                int ind = header.indexOf(':');
                String key = header.substring(0, ind).trim();
                String value = header.substring(ind + 1, header.length()).trim();
                frame.headers.put(key, value);

                if ("content-length".equals(key)) {
                    expectedLength = Integer.parseInt(value);
                }
            }

            // Read any content.
            if (expectedLength == -1) {
                // Variable-length read, terminated by null (ie, probably ASCII or UTF8 string).
                // This really sucks, but there's no way around it in STOMP ... its a sentinel byte so we
                // have to read one byte at a time.  Highly recommend your stomp server / client sets
                // the "content-length" header! (See below).
                ByteArrayOutputStream baos = null;
                int b;
                while ((b = input.readByte()) != 0) {
                    if (baos == null) {
                        baos = new ByteArrayOutputStream(2048);
                    }
                    baos.write(b);
                }
                if (baos != null)
                    frame.content = baos.toByteArray();
            } else if (expectedLength > 0) {
                // Read exactly the expected data into a new byte[]
                byte[] body = new byte[expectedLength];
                input.readFully(body);
                frame.content = body;
            }

        }

        return frame;
    }

    public void write (DataOutputStream dataOut) throws IOException {
        dataOut.writeBytes(type);
        dataOut.writeByte('\n');

        if (!headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                dataOut.writeBytes(entry.getKey());
                dataOut.writeBytes(":");
                String value = entry.getValue();
                dataOut.writeBytes(value == null? "": value);
                dataOut.writeByte('\n');
            }
        }
        if (content != null) {
            dataOut.writeBytes("content-length:");
            dataOut.writeBytes(Integer.toString(content.length));
            dataOut.writeByte('\n');
        }
        dataOut.writeByte('\n');

        if (content != null) {
            dataOut.write(content);
        }
        dataOut.write(0x00);
        dataOut.flush();
    }
}
