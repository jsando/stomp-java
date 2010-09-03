package stompperf;

import java.util.concurrent.Callable;

abstract class PerformanceTest implements Callable {
    void init () {}
    void destroy() {}

    public String toString () {
        return getClass().getSimpleName();
    }
}
