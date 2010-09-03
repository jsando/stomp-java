package stompperf;

public abstract class DaemonPerformanceTest extends PerformanceTest {

    protected long runMillis;
    protected int count;

    public long getRunMillis() {
        return runMillis;
    }

    public void setRunMillis(long runMillis) {
        this.runMillis = runMillis;
    }

    public int getCount() {
        return count;
    }
}
