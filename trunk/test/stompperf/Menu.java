package stompperf;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import stomptest.FrameTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.concurrent.Callable;

/**
 * Performance tests:
 *   - Frames marshalled per second (run for 10 seconds and measure)
 *   - TCP with auto-ack versus client-ack
 *   - UDP msg/sec
 */
public class Menu {

    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.printf(
                    "\n\nSTOMP Test Utility\n" +
                    "------------------------------------\n" +
                    "1. Run junit tests.\n" +
                    "2. Run performance tests.\n" +
                            "\nChoose: ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (line == null) {
                break;
            }

            line = line.trim();

            if ("1".equals (line)) {
                runUnitTests();
            } else if ("2".equals (line)) {
                runPerformanceTests();
            }

        }
    }

    private static void runUnitTests() {
        System.out.printf("Running junit ...\n\n");
        JUnitCore core = new JUnitCore();
        core.addListener(new RunListener(){
            @Override
            public void testStarted(Description description) throws Exception {
                System.out.printf("Running %s ... ", description.getDisplayName());
            }

            @Override
            public void testFinished(Description description) throws Exception {
                System.out.printf("Ok.\n");
            }

            @Override
            public void testFailure(Failure failure) throws Exception {
                System.out.printf("FAILED - %s\n", failure.getMessage());
            }

            @Override
            public void testRunFinished(Result result) throws Exception {
                System.out.printf("\nTest completed, %d run, %d failed, total time %dms\n", result.getRunCount(), result.getFailureCount(), result.getRunTime());
            }
        });
        core.run(FrameTest.class);
    }

    //------------------------------------------------------------------------------------------------------------------

    public static void runPerformanceTests () throws Exception {
        PerformanceTest[] strategies = {
                new FrameMarshalling(),
                new FrameUnmarshalling(),
                new TcpSendThroughput(false),
                new TcpSendThroughput(true),
                new TcpReceiveThroughput(false),
                new TcpReceiveThroughput(true),
        };
        for (final PerformanceTest strategy : strategies) {
            strategy.init();
            benchmark(strategy);
            strategy.destroy();
        }
    }

    private static void benchmark(PerformanceTest test) {
        final int SECONDS = 10;
        System.out.println("Running benchmark '" + test + "' for " + SECONDS + " seconds ... ");
        int millis = SECONDS * 1000;
        long count;
        if (test instanceof DaemonPerformanceTest) {
            DaemonPerformanceTest daemon = (DaemonPerformanceTest) test;
            daemon.setRunMillis(millis);
            try {
                daemon.call();
            } catch (Exception e) {
                System.out.printf("Error running benchmark: %s\n", e);
            }
            count = daemon.getCount();
        } else {
            BenchmarkRunner runner = new BenchmarkRunner(test);
            runner.run(millis);
            count = runner.getCount();
        }
        double cps = count / ((double) millis / 1000);
        DecimalFormat numberFormat = new DecimalFormat("#,##0.00");
        System.out.println("    Total time:   " + millis);
        System.out.println("    Total count:  " + count);
        System.out.println("    Count/second: " + numberFormat.format(cps));
    }

    private static class BenchmarkRunner {

        private volatile boolean running;
        private final Callable benchmark;
        private long duration;
        private long count;
        private Thread thread;

        private BenchmarkRunner(Callable benchmark) {
            this.benchmark = benchmark;
        }

        public void run (long millis) {
            start();
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stop();
        }

        private void start () {
            thread = new Thread(new Runnable() {
                public void run() {
                    run2();
                }
            });
            thread.start();
        }

        private void stop() {
            running = false;
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void run2() {
            running = true;
            long count = 0;
            long t0 = System.currentTimeMillis();

            while (running) {
                try {
                    benchmark.call();
                } catch (Exception e) {
                    throw new RuntimeException ("Error: " + e);
                }
                count++;
            }
            long t1 = System.currentTimeMillis();
            this.duration = t1 - t0;
            this.count = count;
        }

        public long getDuration() {
            return duration;
        }

        public long getCount() {
            return count;
        }

        public double getCountPerSecond() {
            return count / ((double) duration / 1000);
        }
    }

}
