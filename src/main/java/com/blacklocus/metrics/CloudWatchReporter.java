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
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Sampling;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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

    public static final String NAME_DELIMITER = " ";
    public static final String NAME_SEPARATOR = "=";

    public static final String METRIC_TYPE = "type";


    private final AmazonCloudWatchAsync cloudWatch;
    private final String metricNamespace;

    private final Map<Counting, Long> lastPolledCounts = new HashMap<Counting, Long>();

    /**
     * Creates a new {@link ScheduledReporter} instance.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     * @param metricNamespace (optional) CloudWatch metric namespace that all metrics reported by this reporter will
     *                        fall under
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

        try {
            List<MetricDatum> data = new ArrayList<MetricDatum>(gauges.size() + counters.size() + meters.size() +
                    2 * histograms.size() + 2 * timers.size()); // something like that


            for (Map.Entry<String, Gauge> gaugeEntry : gauges.entrySet()) {
                DemuxedKey key = new DemuxedKey(gaugeEntry.getKey());
                Gauge gauge = gaugeEntry.getValue();

                Object valueObj = gauge.getValue();
                if (valueObj == null) {
                    continue;
                }

                String valueStr = valueObj.toString();
                if (NumberUtils.isNumber(valueStr)) {
                    Number value = NumberUtils.createNumber(valueStr);
                    data.add(key.newDatum("gauge").withValue(value.doubleValue()));
                }
            }

            for (Map.Entry<String, Counter> counterEntry : counters.entrySet()) {
                DemuxedKey key = new DemuxedKey(counterEntry.getKey());
                Counter counter = counterEntry.getValue();

                count(key, counter, "counterSum", data);
            }

            for (Map.Entry<String, Meter> meterEntry : meters.entrySet()) {
                DemuxedKey key = new DemuxedKey(meterEntry.getKey());
                Meter meter = meterEntry.getValue();

                count(key, meter, "meterSum", data);
            }

            for (Map.Entry<String, Histogram> histogramEntry : histograms.entrySet()) {
                DemuxedKey key = new DemuxedKey(histogramEntry.getKey());
                Histogram histogram = histogramEntry.getValue();

                count(key, histogram, "histogramCount", data);
                sampling(key, histogram, 1.0, "histogramSum", data);
            }

            for (Map.Entry<String, Timer> timerEntry : timers.entrySet()) {
                DemuxedKey key = new DemuxedKey(timerEntry.getKey());
                Timer timer = timerEntry.getValue();

                count(key, timer, "timerCount", data);
                // nanos -> millis
                sampling(key, timer, 0.000001, "timerSum", data);

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

            LOG.info("Sent {} metric data to CloudWatch. namespace: {}", data.size(), metricNamespace);

        } catch (RuntimeException e) {
            LOG.error("Error marshalling CloudWatch metrics.", e);
        }
    }


    private void count(DemuxedKey key, Counting metric, String type, Collection<MetricDatum> data) {
        long count = metric.getCount();
        Long lastCount = lastPolledCounts.get(metric);
        if (lastCount == null) {
            lastCount = 0L;
        }
        double diff = count - lastCount;
        lastPolledCounts.put(metric, count);

        data.add(key.newDatum(type).withValue(diff).withUnit(StandardUnit.Count));
    }

    /**
     * @param rescale the submitted sum by this multiplier. 1.0 is the identity (no rescale).
     */
    private void sampling(DemuxedKey key, Sampling metric, double rescale, String type, Collection<MetricDatum> data) {
        Snapshot snapshot = metric.getSnapshot();
        data.add(key.newDatum(type).withStatisticValues(new StatisticSet()
                .withSum(sum(snapshot.getValues()) * rescale)
                .withSampleCount((double) snapshot.size())
                .withMinimum((double) snapshot.getMin())
                .withMaximum((double) snapshot.getMax())));
    }

    private long sum(long[] values) {
        long sum = 0L;
        for (long value : values) sum += value;
        return sum;
    }

    static final MetricFilter ALL = new MetricFilter() {
        @Override
        public boolean matches(String name, Metric metric) {
            return true;
        }
    };

    static class DemuxedKey {

        final String name;
        final Collection<Dimension> dimensions = new ArrayList<Dimension>();

        DemuxedKey(String s) {
            String[] segments = s.split(NAME_DELIMITER);
            StringBuilder name = new StringBuilder(segments[0]);

            for (int i = 1; i < segments.length; i++) {
                String segment = segments[i];

                if (segment.contains(NAME_SEPARATOR)) {
                    String[] dimension = segment.split(NAME_SEPARATOR, 2);
                    dimensions.add(new Dimension().withName(dimension[0]).withValue(dimension[1]));
                } else {
                    name.append(" ").append(segment);
                }
            }

            this.name = name.toString();
        }

        /**
         * @return new MetricDatum initialized with {@link #name} and {@link #dimensions}
         */
        MetricDatum newDatum(String type) {
            List<Dimension> dimensions = new ArrayList<Dimension>(this.dimensions.size() + 1);
            dimensions.add(new Dimension().withName(METRIC_TYPE).withValue(type));
            dimensions.addAll(this.dimensions);
            return new MetricDatum().withMetricName(name).withDimensions(dimensions);
        }
    }

}