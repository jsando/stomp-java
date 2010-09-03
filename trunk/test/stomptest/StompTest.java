package stomptest;

import junit.framework.TestCase;
import stomp.BufferConnection;
import stomp.Connection;
import stomp.Message;

public class StompTest extends TestCase {

    public void testOutputBuffer() throws Exception {

        Connection stomp = Connection.openConnection("stomp:buffer");
        assertNotNull(stomp);
        assertTrue(stomp.isConnected());
        assertTrue(stomp instanceof BufferConnection);
        BufferConnection buffer = (BufferConnection) stomp;

        assertEquals(0, buffer.getCount("/queue/abc"));
        assertEquals(0, buffer.getCount());
        assertEquals(0, buffer.getDestinationCount());
        Message message = new Message();
        message.setContentUtf8("Hello, stomp!");
        buffer.send("/queue/abc", message, -1);
        assertEquals(1, buffer.getCount("/queue/abc"));
        assertEquals(1, buffer.getCount());
        assertEquals(1, buffer.getDestinationCount());

        message = new Message();
        message.setContentUtf8("Hello, stomp!");
        buffer.send("/queue/123", message, -1);
        assertEquals(1, buffer.getCount("/queue/abc"));
        assertEquals(1, buffer.getCount("/queue/123"));
        assertEquals(2, buffer.getCount());
        assertEquals(2, buffer.getDestinationCount());

        // Note that after a purge, the destination still exists but with zero messages.
        buffer.purge("/queue/abc");
        message = new Message();
        message.setContentUtf8("Hello, stomp!");
        buffer.send("/queue/123", message, -1);
        assertEquals(0, buffer.getCount("/queue/abc"));
        assertEquals(2, buffer.getCount("/queue/123"));
        assertEquals(2, buffer.getCount());
        assertEquals(2, buffer.getDestinationCount());

        // A 'delete' will remove the destination.
        buffer.delete("/queue/abc");
        assertEquals(0, buffer.getCount("/queue/abc"));
        assertEquals(2, buffer.getCount("/queue/123"));
        assertEquals(2, buffer.getCount());
        assertEquals(1, buffer.getDestinationCount());
    }
}
