package stompperf;

import stomp.Connection;
import stomp.Consumer;
import stomp.Message;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

class TcpReceiveThroughput extends DaemonPerformanceTest implements Consumer {

    private Connection client;
    private TcpServer server;
    private boolean clientAck;

    public TcpReceiveThroughput(boolean clientAck) {
        this.clientAck = clientAck;
    }

    @Override
    public String toString() {
        return "TcpSendThroughput{" +
                "clientAck=" + clientAck +
                '}';
    }

    void init() {
        server = new TcpServer(12345);
        server.start();
        try {
            client = Connection.openConnection("stomp://127.0.0.1:12345");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void destroy() {
        if (client != null)
            client.close();
        if (server != null)
            server.stop();

        client = null;
        server = null;
    }

    public Object call() throws Exception {
        final Timer timer = new Timer();
        final Object wait = new Object();
        client.subscribe("/topic/flood", this, -1,
                Connection.SUBSCRIBE_ACKMODE, clientAck? Connection.ACKMODE_CLIENT: Connection.ACKMODE_AUTO);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                destroy();
                timer.cancel();
                synchronized (wait) {
                    wait.notifyAll();
                }
            }
        }, runMillis);

        synchronized (wait) {
            wait.wait();
        }

        return count;
    }

    public void onMessage(Message message) throws IOException {
        count++;
        if (clientAck) {
            message.acknowledge(-1);
        }
    }

    public void onError(Connection connection, String error, Exception ex) {
        System.out.printf("Error in connection: %s\n", error);
    }
}