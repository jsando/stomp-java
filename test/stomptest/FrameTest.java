package stomptest;

import org.junit.Test;
import stomp.Frame;
import stomp.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO:
 * - unmarshall a message without content-length
 * - unmarshall at EOF see if we get a null Frame
 */
public class FrameTest {

    @Test
    public void testCreateFrame() {
        Frame frame = new Frame(Frame.TYPE_CONNECT);
        assertNotNull(frame.getHeaders());
        assertEquals(Frame.TYPE_CONNECT, frame.getType());
        assertNull(frame.getContent());
    }

    @Test
    public void testConnectFrame() throws IOException {
        Frame frame = new Frame(Frame.TYPE_CONNECT);
        frame.getHeaders().put("login", "admin");
        frame.getHeaders().put("passcode", "admin");
        validateFrame(frame);
    }

    @Test
    public void testDisconnectFrame() throws IOException {
        Frame frame = new Frame(Frame.TYPE_CONNECT);
        validateFrame(frame);
    }

    @Test
    public void testMessageFrame() throws IOException {
        Message message = new Message();
        message.setContentUtf8("A man, a plan, a canal, Panama!");
        message.setProperty(Message.PERSISTENT, "true");
        message.setProperty(Message.TYPE, "text");
        message.setProperty(Message.DESTINATION, "/queue/temp");
        message.setProperty(Message.PRIORITY, "1");
        Frame frame = new Frame(message);
        frame.getHeaders().put("destination", "/queue/foo");
        validateFrame(frame);
    }

    private void validateFrame(Frame frame) throws IOException {
        Frame dup = remarshall(frame);
        assertEquals(frame.getType(), dup.getType());
        assertArrayEquals(frame.getContent(), dup.getContent());
        compareMaps(frame.getHeaders(), dup.getHeaders());
    }

    private static void compareMaps(Map<String, String> map1, Map<String, String> map2) {
        if (map2.containsKey("content-length"))
            map2.remove("content-length");
        if (map1.keySet().size() != map2.keySet().size() ||
                !map1.keySet().containsAll(map2.keySet()) ||
                !map1.values().containsAll(map2.values())) {
            System.out.printf("Headers don't match\nOriginal:\n");
            for (Map.Entry<String, String> entry : map1.entrySet()) {
                System.out.printf("  '%s': '%s'\n", entry.getKey(), entry.getValue());
            }
            System.out.printf("\nRemarshalled:\n");
            for (Map.Entry<String, String> entry : map2.entrySet()) {
                System.out.printf("  '%s': '%s'\n", entry.getKey(), entry.getValue());
            }

            assertTrue("See prior output - headers don't match.", false);
        }
    }

    private static Frame remarshall(Frame source) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        source.write(dos);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        return Frame.read(dis);
    }

}
