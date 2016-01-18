package com.blacklocus.metrics;

import com.codahale.metrics.MetricRegistry;

import java.util.List;
import java.util.concurrent.TimeUnit;

class ExampleMetrics {

    private final MetricRegistry registry = new MetricRegistry();

    public ExampleMetrics() {
        new CloudWatchReporterBuilder()
                .withNamespace(ExampleMetrics.class.getSimpleName())
                .withRegistry(registry)
                .build()
                .start(1, TimeUnit.MINUTES);
    }

    public void sentThatThing() {
        registry.counter("sentThatThing").inc();
    }

    public void gotABatchOfThoseThingsYaSentMe(int count) {
        registry.counter("gotThatThing").inc(count);
    }
}

public class ExampleApp {

    private final ExampleMetrics exampleMetrics;

    public ExampleApp(ExampleMetrics exampleMetrics) {
        this.exampleMetrics = exampleMetrics;
    }

    public void sendAThing() {
        // ... somewhere in the code not so far away ...
        exampleMetrics.sentThatThing();
    }

    public void receiveSomeThings(List<Object> thoseThings) {
        exampleMetrics.gotABatchOfThoseThingsYaSentMe(thoseThings.size());
        // ... and so on ...
    }
}