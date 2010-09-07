package stomptest;

import junit.framework.TestCase;
import stomp.Connection;
import stomp.Consumer;
import stomp.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UdpConnectionTest extends TestCase {

    public void testUdp () throws Exception {
        String UDP_ADDRESS = "239.1.2.3:61616";
        Connection c1 = Connection.openConnection("stomp://" + UDP_ADDRESS);
        Connection c2 = Connection.openConnection("stomp://" + UDP_ADDRESS);
        final List<Message> m1 = new ArrayList<Message>();
        final List<Message> m2 = new ArrayList<Message>();

        c1.subscribe(UDP_ADDRESS, new Consumer() {
            public void onMessage(Message message) throws IOException {
                System.out.printf("c1 received message.\n");
                m1.add(message);
            }

            public void onError(Connection connection, String error, Exception ex) {
                System.out.printf("c1 error: %s\n", error);
            }
        }, -1);

        c2.subscribe(UDP_ADDRESS, new Consumer() {
            public void onMessage(Message message) throws IOException {
                System.out.printf("c2 received message.\n");
                m2.add(message);
            }

            public void onError(Connection connection, String error, Exception ex) {
                System.out.printf("c2 error: %s\n", error);
            }
        }, -1);

        int COUNT = 10;
        for (int i = 0; i < COUNT; i++) {
            Message msg = new Message();
            msg.setContentUtf8("Hello there!");
            c1.send(UDP_ADDRESS, msg, -1);
            Thread.sleep (50);
        }
        c2.close();
        c1.close();

        assertEquals("C1 did the sending, it should not have received any messages.", 0, m1.size());
        assertEquals("C2 should have received messages.", COUNT, m2.size());
    }
}
