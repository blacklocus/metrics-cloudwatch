package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.codahale.metrics.MetricRegistry;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class CloudWatchReporterTest {

    public static void main(String[] args) throws InterruptedException {

        ExecutorService executors = Executors.newCachedThreadPool();

        // increments the counter by 1 every second, meter ticked once
        executors.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {

                MetricRegistry metricRegistry = new MetricRegistry();
                new CloudWatchReporter(
                        metricRegistry,
                        CloudWatchReporterTest.class.getSimpleName(),
                        new AmazonCloudWatchAsyncClient()
                ).start(1, TimeUnit.MINUTES);

                while(!Thread.interrupted()) {
                    metricRegistry.counter("TheCounter TestDim=Yellow").inc(1);
                    Thread.sleep(1000);
                }
                return null;
            }
        });

        // increments the counter by 2 every second, meter ticked twice
        executors.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {

                MetricRegistry metricRegistry = new MetricRegistry();
                new CloudWatchReporter(
                        metricRegistry,
                        CloudWatchReporterTest.class.getSimpleName(),
                        new AmazonCloudWatchAsyncClient()
                ).start(1, TimeUnit.MINUTES);

                while(!Thread.interrupted()) {
                    metricRegistry.counter("TheCounter TestDim=Yellow").inc(2);
                    Thread.sleep(1000);
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
