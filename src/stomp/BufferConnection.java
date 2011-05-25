package stomp;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An out-only buffer for stomp messages, to allow automated unit testing.
 */
public final class BufferConnection extends Connection {

    /*
     * List of Messages by destination name.
     *
     * Map<String, List>
     */
    private final Map<String,List<Frame>> destinations = Collections.synchronizedMap(new HashMap<String, List<Frame>>());

    public BufferConnection(URI uri) {
        super(uri);
        connected = true;
    }

    //--------------------------------------------------------- StompConnection

    @Override
    protected void transmit(Frame frame, long waitMillis) throws IOException {
        if (frame.getType().equals (Frame.TYPE_SEND)) {
            storeMessage (frame);
        }
    }

    @Override
    protected void disconnect() {
        // do nothing
    }

    @Override
    protected String createSubscriptionId(String destination) {
        return destination;
    }

    //------------------------------------------------------------------ Public

    /**
     * Return a non-null, but possibly empty List of messages for the given destination.
     * <p>
     * The returned list is a copy of the internal list, but the messages are the
     * original messages, so care should be taken not to modify the messages themselves.
     *
     * @param destination The destination name used in transmission, such as "/queue/foo".
     *
     * @return A List of Message objects, not null but possibly empty.
     */
    public List<Frame> getMessages (String destination) {
        List<Frame> messages = destinations.get (destination);
        if (messages == null) {
            messages = new ArrayList<Frame>();
        } else {
            messages = new ArrayList<Frame>(messages);
        }
        return messages;
    }

    /**
     *
     */
    public Set<String> getDestinations () {
        return destinations.keySet();
    }

    /**
     * Return the number of messages sent to the given destination.
     * <p>
     * Equivalent to getMessages(destination).size().
     *
     * @param destination The destination name used in transmission, such as "/queue/foo".
     *
     * @return A count of messages sent to that destination since last reset.
     */
    public int getCount (String destination) {
        int count = 0;
        List<Frame> messages = destinations.get (destination);
        if (messages != null) {
            count = messages.size();
        }
        return count;
    }

    /**
     * Get the total number of messages across all destinations.
     *
     * @return the total number of messages across all destinations.
     */
    public int getCount () {
        int count = 0;
        for (List<Frame> list : destinations.values()) {
            if (list != null) {
                count += list.size();
            }
        }
        return count;
    }

    /**
     * Return total number of destinations with messages enqueued.
     *
     * @return The number of destinations with messages.
     */
    public int getDestinationCount() {
        return destinations.keySet().size();
    }

    /**
     * Purge any accumulated messages from the given destination.
     * <p>
     * The destination remains defined however.
     *
     * @param destination The destination name such as "/queue/foo".
     */
    public void purge (String destination) {
        destinations.put(destination, null);
    }

    /**
     * Purge all destinations.
     */
    public void purge () {
        for (Object o : destinations.keySet()) {
            purge((String) o);
        }
    }

    /**
     * Delete the given destination.
     */
    public void delete (String destination) {
        destinations.remove(destination);
    }

    /**
     * Delete all destinations.
     */
    public void delete () {
        destinations.clear();
    }

    //----------------------------------------------------------------- Private
    
    private void storeMessage(Frame frame) {
        // Override the command from a SEND to a MESSAGE, ie an incoming message
        // we may need to set some JMS headers to make it look like it came through a real JMS provider
        String dest = frame.getHeaders().get("destination");

        List<Frame> messages = destinations.get (dest);
        if (messages == null) {
            messages = new ArrayList<Frame>();
            destinations.put (dest, messages);
        }
        messages.add (frame);
    }

}