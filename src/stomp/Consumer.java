package stomp;

import java.io.IOException;

public interface Consumer {
    void onMessage (Message message) throws IOException;
    void onError (Connection connection, String error, Exception ex);
}
