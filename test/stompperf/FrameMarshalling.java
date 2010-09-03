package stompperf;

import stomp.Frame;
import stomp.Message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

class FrameMarshalling extends PerformanceTest {

    public Object call() throws Exception {
        Message message = new Message();
        message.setContentUtf8("A man, a plan, a canal, Panama!");
        message.setProperty(Message.PERSISTENT, "true");
        message.setProperty(Message.TYPE, "text");
        message.setProperty(Message.DESTINATION, "/queue/temp");
        message.setProperty(Message.PRIORITY, "1");
        Frame frame = new Frame(message);
        frame.getHeaders().put("destination", "/queue/foo");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        frame.write(new DataOutputStream(baos));
        return baos.toByteArray();
    }
}
