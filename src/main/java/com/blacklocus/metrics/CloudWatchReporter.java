/**
 * Copyright 2013 BlackLocus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
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
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class CloudWatchReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchReporter.class);

    private final AmazonCloudWatchAsync cloudWatch;
    private final String metricNamespace;

    private final Map<Counter, Long> lastPolledCounts = new HashMap<Counter, Long>();

    /**
     * Creates a new {@link ScheduledReporter} instance.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     * @param metricNamespace CloudWatch metric namespace that all metrics reported by this reporter will use
     */
    public CloudWatchReporter(MetricRegistry registry, String metricNamespace, AmazonCloudWatchAsync cloudWatch) {
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

        List<MetricDatum> data = new ArrayList<MetricDatum>(gauges.size() + counters.size() + histograms.size() +
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


        // Each CloudWatch API request may contain at maximum 20 datums.
        List<List<MetricDatum>> dataPartitions = Lists.partition(data, 20);
        List<Future<?>> cloudWatchFutures = new ArrayList<Future<?>>(dataPartitions.size());

        for (List<MetricDatum> dataSubset : dataPartitions) {
            cloudWatchFutures.add(cloudWatch.putMetricDataAsync(new PutMetricDataRequest()
                    .withNamespace(metricNamespace)
                    .withMetricData(dataSubset)));
        }
        for (Future<?> cloudWatchFuture : cloudWatchFutures) {
            // We can't let an exception leak out of here, or else the reporter will cease running per mechanics of
            // java.util.concurrent.ScheduledExecutorService.scheduleAtFixedRate(Runnable, long, long, TimeUnit unit)
            try {
                // See what happened in case of an error.
                cloudWatchFuture.get();
            } catch (Exception e) {
                LOG.error("Exception reporting metrics to CloudWatch. The data sent in this CloudWatch API request " +
                        "may have been discarded.", e);
            }
        }

        LOG.info("Sent {} metric datas to CloudWatch", data.size());
    }

    static final MetricFilter ALL = new MetricFilter() {
        @Override
        public boolean matches(String name, Metric metric) {
            return true;
        }
    };
}
