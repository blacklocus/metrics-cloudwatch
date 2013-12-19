package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class CloudWatchReporterTest {

    @Test
    @Ignore("ad-hoc usage")
    public void createTestData() throws InterruptedException {

        ExecutorService executors = Executors.newCachedThreadPool();

        // increments the counter by 1 every second, meter ticked once, histogram ticked once, gauge given 1
        executors.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {

                MetricRegistry metricRegistry = new MetricRegistry();
                new CloudWatchReporter(
                        metricRegistry,
                        CloudWatchReporterTest.class.getSimpleName(),
                        new AmazonCloudWatchAsyncClient()
                ).start(1, TimeUnit.MINUTES);

                Gauge<Long> theGauge = metricRegistry.register("TheGauge", new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return 1L;
                    }
                });
                // Should be ignored by reporter
                Gauge<String> nonNumericGauge = metricRegistry.register("TheGauge notNumeric", new Gauge<String>() {
                    @Override
                    public String getValue() {
                        return "yellow";
                    }
                });

                while(!Thread.interrupted()) {
                    metricRegistry.counter("TheCounter TestDim=Yellow").inc(1);
                    metricRegistry.meter("TheMeter").mark();
                    metricRegistry.histogram("TheHistogram").update(1);
                    Timer.Context theTimer = metricRegistry.timer("TheTimer").time();
                    Thread.sleep(1000);
                    theTimer.close();
                }
                return null;
            }
        });

        // increments the counter by 2 every second, meter ticked twice, histogram ticked twice, gauge given 2
        executors.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {

                MetricRegistry metricRegistry = new MetricRegistry();
                new CloudWatchReporter(
                        metricRegistry,
                        CloudWatchReporterTest.class.getSimpleName(),
                        new AmazonCloudWatchAsyncClient()
                ).start(1, TimeUnit.MINUTES);

                Gauge<Long> theGauge = metricRegistry.register("TheGauge", new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return 2L;
                    }
                });
                // Should be ignored by reporter
                Gauge<String> nonNumericGauge = metricRegistry.register("TheGauge notNumeric", new Gauge<String>() {
                    @Override
                    public String getValue() {
                        return "green";
                    }
                });

                while(!Thread.interrupted()) {
                    metricRegistry.counter("TheCounter TestDim=Yellow").inc(2);
                    metricRegistry.meter("TheMeter").mark(2);
                    metricRegistry.histogram("TheHistogram").update(2);
                    Timer.Context theTimer = metricRegistry.timer("TheTimer").time();
                    Thread.sleep(500);
                    theTimer.close();
                    theTimer = metricRegistry.timer("TheTimer").time();
                    Thread.sleep(500);
                    theTimer.close();
                }
                return null;
            }
        });

        for (int i = 0; i < 15; i++) {
            System.out.printf("Sleeping... %d minutes elapsed%n", i);
            Thread.sleep(60 * 1000);
        }
        executors.shutdownNow();
        executors.awaitTermination(5, TimeUnit.SECONDS);

    }
}
