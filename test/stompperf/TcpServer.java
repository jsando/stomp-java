package stompperf;

import stomp.Frame;
import stomp.Message;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer implements Runnable {

    private boolean running;
    private int port;
    private Thread thread;
    private ServerSocket server;

    public TcpServer(int port) {
        this.port = port;
    }

    public void start () {
        running = true;
        thread = new Thread(this);
        thread.start();
        try {
            Thread.sleep (1000); // give server socket time to start up 
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void stop () {
        running = false;
        try {
            server.close();
        } catch (IOException e) {
            // ignore
        }
        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void run() {
        try {
            run2();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void run2() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(12345));
        while (running) {
            Socket socket = null;
            try {
                socket = server.accept();
            } catch (IOException e) {
                break;
            }
            Handler handler = new Handler(socket);
            handler.start();
        }
    }

    private class Handler extends Thread {

        private Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;

        private Handler(Socket socket) throws IOException {
            this.socket = socket;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            try {
                run2();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        public void run2() throws IOException {
            while (running) {
                Frame frame = Frame.read(dis);
                if (frame == null)
                    break;
                
                if (frame.getType().equals(Frame.TYPE_CONNECT)) {
//                    System.out.printf("Connect [login: '%s', passcode: '%s']\n",
//                            frame.getHeaders().get ("login"), frame.getHeaders().get("passcode"));
                    Frame response = new Frame(Frame.TYPE_CONNECTED);
                    response.write(dos);
                } else if (frame.getType().equals(Frame.TYPE_DISCONNECT)) {
//                    System.out.printf("Disconnect.\n");
                    break;
                } else if (frame.getType().equals(Frame.TYPE_SEND)) {
                    handleReceipt (frame);
                } else if (frame.getType().equals(Frame.TYPE_ACK)) {
                    handleReceipt (frame);
                } else if (frame.getType().equals(Frame.TYPE_SUBSCRIBE)) {
                    handleReceipt (frame);
                    if (frame.getHeaders().get("destination").equals("/topic/flood")) {
                        Thread flooder = new Thread(new Runnable() {
                            public void run() {
                                while (running) {
                                    Message message = new Message();
                                    message.setContentUtf8("A man, a plan, a canal, Panama!");
                                    message.setProperty(Message.PERSISTENT, "true");
                                    message.setProperty(Message.TYPE, "text");
                                    message.setProperty("destination", "/topic/flood");
                                    message.setProperty("message-id", "abc:" + System.identityHashCode(message));
                                    Frame messageFrame = new Frame(message);
                                    messageFrame.setType(Frame.TYPE_MESSAGE);
                                    try {
                                        send(messageFrame);
                                    } catch (IOException e) {
                                        break;
                                    }
                                }
                            }
                        });
                        flooder.start();
                    }
                } else if (frame.getType().equals(Frame.TYPE_UNSUBSCRIBE)) {
                    handleReceipt (frame);
                } else {
                    System.out.printf("Client received unhandled frame %s\n", frame.getType());
                }
            }
        }

        private void handleReceipt(Frame frame) throws IOException {
            String id = frame.getHeaders().get ("receipt");
            if (id != null) {
                Frame response = new Frame (Frame.TYPE_RECEIPT);
                response.getHeaders().put ("receipt-id", id);
                send(response);
            }
        }

        private void send (Frame frame) throws IOException {
            synchronized (dos) {
                frame.write(dos);
            }
        }
    }
}
