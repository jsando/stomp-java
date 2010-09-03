package stompperf;

import stomp.Frame;
import stomp.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class FrameUnmarshalling extends PerformanceTest {

    private byte[] bytes;

    @Override
    void init() {
        Message message = new Message();
        message.setContentUtf8("A man, a plan, a canal, Panama!");
        message.setProperty(Message.PERSISTENT, "true");
        message.setProperty(Message.TYPE, "text");
        message.setProperty(Message.DESTINATION, "/queue/temp");
        message.setProperty(Message.PRIORITY, "1");
        Frame frame = new Frame(message);
        frame.getHeaders().put("destination", "/queue/foo");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            frame.write(new DataOutputStream(baos));
            bytes = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object call() throws Exception {
        return Frame.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }
}
