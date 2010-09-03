package stompperf;

import stomp.Connection;
import stomp.Consumer;
import stomp.Message;

import java.io.IOException;

public class ConnectionTest {

    private static final String CONNECTION_URL = "tcp://localhost:61616";

    public static void main(String[] args) throws Exception {
        Thread t = new Thread (new Runnable() {
            public void run() {
                try {
                    doReceiver();
                } catch (Exception e) {
                    System.out.println("Receiver died: " + e);
                }
            }
        });
        t.start();

        Connection con = Connection.openConnection(CONNECTION_URL);
        Message message = new Message();
        message.setContentUtf8("This is the message.");
        con.send ("/queue/Stomp.Test", message, -1);
    }

    private static void doReceiver() throws Exception {
        Connection con = Connection.openConnection(CONNECTION_URL);
        Consumer consumer = new Consumer() {
            public void onMessage(Message message) throws IOException {
                System.out.println("Message received: " + message.getProperty(Message.MESSAGE_ID));
                message.acknowledge(-1);
            }

            public void onError(Connection connection, String message, Exception ex) {
                System.out.printf("Error in receiver: %s\n", message);
            }
        };
        con.subscribe("/queue/Stomp.Test", consumer, -1, Connection.SUBSCRIBE_ACKMODE, Connection.ACKMODE_CLIENT);
    }
}
