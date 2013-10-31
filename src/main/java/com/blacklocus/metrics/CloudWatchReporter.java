package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class CloudWatchReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchReporter.class);

    private final AmazonCloudWatch cloudWatch;
    private final String metricNamespace;

    private final Map<Counter, Long> lastPolledCounts = new HashMap<Counter, Long>();

    /**
     * Creates a new {@link ScheduledReporter} instance.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     * @param metricNamespace CloudWatch metric namespace that all metrics reported by this reporter will use
     */
    public CloudWatchReporter(MetricRegistry registry, String metricNamespace, AmazonCloudWatch cloudWatch) {
        super(registry, "CloudWatchReporter:" + metricNamespace, ALL, TimeUnit.MINUTES, TimeUnit.MINUTES);
        this.cloudWatch = cloudWatch;
        this.metricNamespace = metricNamespace;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {

        Collection<MetricDatum> data = new ArrayList<MetricDatum>(gauges.size() + counters.size() + histograms.size() +
                meters.size() + timers.size()); // something like that

        if (gauges.size() > 0) {
            LOG.warn("CloudWatchReporter Gauge reporting not implemented.");
        }

        if (counters.size() > 0) {
            for (Map.Entry<String, Counter> counterEntry : counters.entrySet()) {
                Counter counter = counterEntry.getValue();
                long count = counter.getCount();
                Long lastCount = lastPolledCounts.get(counter);
                if (lastCount == null) {
                    lastCount = 0L;
                }
                long diff = count - lastCount;
                lastPolledCounts.put(counter, count);
                data.add(new MetricDatum().withMetricName(counterEntry.getKey()).withValue((double) diff).withUnit(StandardUnit.Count));
            }
        }

        if (histograms.size() > 0) {
            LOG.warn("CloudWatchReporter Histogram reporting not implemented.");
        }

        if (meters.size() > 0) {
            for (Map.Entry<String, Meter> meterEntry : meters.entrySet()) {
                Meter meter = meterEntry.getValue();
                // Only send the 1-minute. Use CloudWatch to smooth over longer periods. Yeah... I guess that makes sense.
                double oneMinuteRate = meter.getOneMinuteRate();
                data.add(new MetricDatum().withMetricName(meterEntry.getKey()).withValue(oneMinuteRate));
            }
        }

        if (timers.size() > 0) {
            LOG.warn("CloudWatchReporter Timer reporting not implemented.");
        }

        cloudWatch.putMetricData(new PutMetricDataRequest().withNamespace(metricNamespace).withMetricData(data));

        LOG.info("Sent {} metric datas to CloudWatch", data.size());
    }

    static final MetricFilter ALL = new MetricFilter() {
        @Override
        public boolean matches(String name, Metric metric) {
            return true;
        }
    };
}
