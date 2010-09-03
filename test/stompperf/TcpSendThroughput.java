package stompperf;

import stomp.Connection;
import stomp.Message;

import java.io.IOException;

class TcpSendThroughput extends PerformanceTest {

    private Connection client;
    private TcpServer server;
    private boolean awaitReceipt;

    public TcpSendThroughput(boolean awaitReceipt) {
        this.awaitReceipt = awaitReceipt;
    }

    @Override
    public String toString() {
        return "TcpSendThroughput{" +
                "awaitReceipt=" + awaitReceipt +
                '}';
    }

    @Override
    void init() {
        server = new TcpServer(12345);
        server.start();
        try {
            client = Connection.openConnection("stomp://127.0.0.1:12345");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    void destroy() {
        if (client != null)
            client.close();
        server.stop();
    }

    public Object call() throws Exception {
        Message message = new Message();
        message.setContentUtf8("A man, a plan, a canal, Panama!");
        message.setProperty(Message.PERSISTENT, "true");
        message.setProperty(Message.TYPE, "text");
        client.send ("/queue/temp", message, awaitReceipt? 1000: -1);
        return message;
    }
}
