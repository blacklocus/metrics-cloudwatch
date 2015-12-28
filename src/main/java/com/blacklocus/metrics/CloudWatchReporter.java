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
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
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
 * <h2>Included dimension</h2>
 *
 * One dimension is always included with the dimension name defined in {@link #METRIC_TYPE_DIMENSION}. This corresponds
 * to a type of metric submissions to CloudWatch.
 * <p>
 * Refer to [README.md](https://github.com/blacklocus/metrics-cloudwatch/blob/master/README.md) for documentation on
 * metric submission types.
 *
 * <h2>Custom dimensions</h2>
 *
 * CloudWatch does not aggregate over dimensionChain on custom metrics, see
 * http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#Dimension
 * To achieve similar convenience, we can submit metrics in duplicate, once for each tuple of attributes against which
 * we would aggregate metrics.
 * <p>
 * Refer to [README.md](https://github.com/blacklocus/metrics-cloudwatch/blob/master/README.md) for documentation on
 * the name-encoded syntax of dimensions and permutable components.
 *
 *
 * @author Jason Dunkelberger (dirkraft)
 */
public class CloudWatchReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchReporter.class);


    /**
     * Delimiter of tokens in the metric name. Plain tokens will be retained as the CloudWatch "Metric Name".
     */
    public static final String NAME_TOKEN_DELIMITER_RGX = "\\s";
    // For building; should qualify against NAME_TOKEN_DELIMITER_RGX
    public static final String NAME_TOKEN_DELIMITER = " ";

    /**
     * Separator of key and value segments of a metric name. These segments will be split into the key and value of
     * a CloudWatch {@link Dimension}.
     */
    public static final String NAME_DIMENSION_SEPARATOR = "=";

    /**
     * If any token, whether a simple string or a dimension pair ends with this marker, then metrics will be sent once
     * with and once without.
     */
    public static final String NAME_PERMUTE_MARKER = "*";

    /**
     * Key of {@link Dimension#name} for the type of metric submission, e.g. gauge, counterSum, meterSum, ...
     */
    public static final String METRIC_TYPE_DIMENSION = "type";


    // Should line up with constants. Name should not contain any special character, and may optionally end with the
    // permute marker.
    public static final String VALID_NAME_TOKEN_RGX = "[^\\s=\\*]+\\*?";
    public static final String VALID_DIMENSION_PART_RGX = "[^\\s=\\*]+";

    /**
     * Carried into to CloudWatch namespace
     */
    private final String metricNamespace;

    private final AmazonCloudWatchAsync cloudWatch;

    private final Map<Counting, Long> lastPolledCounts = new HashMap<Counting, Long>();

    /**
     * Global reporter-wide dimensions automatically appended to all metrics.
     */
    private String dimensions;


    /**
     * Creates a new {@link ScheduledReporter} instance. The reporter does not report metrics until
     * {@link #start(long, TimeUnit)}.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     */
    public CloudWatchReporter(MetricRegistry registry, AmazonCloudWatchAsync cloudWatch) {
        this(registry, null, cloudWatch);
    }

    /**
     * Creates a new {@link ScheduledReporter} instance. The reporter does not report metrics until
     * {@link #start(long, TimeUnit)}.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     * @param metricNamespace (optional) CloudWatch metric namespace that all metrics reported by this reporter will
     *                        fall under
     */
    public CloudWatchReporter(MetricRegistry registry,
                              String metricNamespace,
                              AmazonCloudWatchAsync cloudWatch) {
        this(registry, metricNamespace, ALL, cloudWatch);
    }

    /**
     * Creates a new {@link ScheduledReporter} instance. The reporter does not report metrics until
     * {@link #start(long, TimeUnit)}.
     *
     * @param registry        the {@link MetricRegistry} containing the metrics this reporter will report
     * @param metricNamespace (optional) CloudWatch metric namespace that all metrics reported by this reporter will
     *                        fall under
     * @param metricFilter    (optional) see {@link MetricFilter}
     */
    public CloudWatchReporter(MetricRegistry registry,
                              String metricNamespace,
                              MetricFilter metricFilter,
                              AmazonCloudWatchAsync cloudWatch) {

        super(registry, "CloudWatchReporter:" + metricNamespace, metricFilter, TimeUnit.MINUTES, TimeUnit.MINUTES);

        this.metricNamespace = metricNamespace;
        this.cloudWatch = cloudWatch;
    }

    /**
     * Sets global reporter-wide dimensions and returns itself.
     *
     * @param dimensions the string representing global dimensions
     * @return this instance
     */
    public CloudWatchReporter withDimensions(String dimensions) {
        this.dimensions = dimensions;
        return this;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {

        try {
            List<MetricDatum> data = new ArrayList<MetricDatum>(
                    gauges.size() + counters.size() + meters.size() + 2 * histograms.size() + 2 * timers.size()
            );
            // something like that


            for (Map.Entry<String, Gauge> gaugeEntry : gauges.entrySet()) {
                reportGauge(gaugeEntry, "gauge", data);
            }

            for (Map.Entry<String, Counter> counterEntry : counters.entrySet()) {
                reportCounter(counterEntry, "counterSum", data);
            }

            for (Map.Entry<String, Meter> meterEntry : meters.entrySet()) {
                reportCounter(meterEntry, "meterSum", data);
            }

            for (Map.Entry<String, Histogram> histogramEntry : histograms.entrySet()) {
                reportCounter(histogramEntry, "histogramCount", data);
                reportSampling(histogramEntry, "histogramSet", 1.0, data);
            }

            for (Map.Entry<String, Timer> timerEntry : timers.entrySet()) {
                reportCounter(timerEntry, "timerCount", data);
                reportSampling(timerEntry, "timerSet", 0.000001, data); // nanos -> millis
            }

            // CloudWatch rejects any Statistic Sets with sample count == 0
            Collection<MetricDatum> nonEmptyData = Collections2.filter(data, new Predicate<MetricDatum>() {
                @Override
                public boolean apply(MetricDatum input) {
                    if (input == null) {
                        return false;
                    } else if (input.getStatisticValues() != null) {
                        return input.getStatisticValues().getSampleCount() > 0;
                    }
                    return true;
                }
            });
            // Each CloudWatch API request may contain at maximum 20 datums.
            Iterable<List<MetricDatum>> dataPartitions = Iterables.partition(nonEmptyData, 20);
            List<Future<?>> cloudWatchFutures = Lists.newArrayListWithExpectedSize(data.size());

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

            LOG.debug("Sent {} metric data to CloudWatch. namespace: {}", data.size(), metricNamespace);

        } catch (RuntimeException e) {
            LOG.error("Error marshalling CloudWatch metrics.", e);
        }
    }


    void reportGauge(Map.Entry<String, Gauge> gaugeEntry, String type, List<MetricDatum> data) {
        Gauge gauge = gaugeEntry.getValue();

        Object valueObj = gauge.getValue();
        if (valueObj == null) {
            return;
        }

        String valueStr = valueObj.toString();
        if (NumberUtils.isNumber(valueStr)) {
            final Number value = NumberUtils.createNumber(valueStr);

            DemuxedKey key = new DemuxedKey(appendGlobalDimensions(gaugeEntry.getKey()));
            Iterables.addAll(data, key.newDatums(type, new Function<MetricDatum, MetricDatum>() {
                @Override
                public MetricDatum apply(MetricDatum datum) {
                    return datum.withValue(value.doubleValue());
                }
            }));
        }
    }

    void reportCounter(Map.Entry<String, ? extends Counting> entry, String type, List<MetricDatum> data) {
        Counting metric = entry.getValue();
        final long diff = diffLast(metric);
        if (diff == 0) {
            // Don't submit metrics that have not changed. No reason to keep these alive. Also saves on CloudWatch
            // costs.
            return;
        }

        DemuxedKey key = new DemuxedKey(appendGlobalDimensions(entry.getKey()));
        Iterables.addAll(data, key.newDatums(type, new Function<MetricDatum, MetricDatum>() {
            @Override
            public MetricDatum apply(MetricDatum datum) {
                return datum.withValue((double) diff).withUnit(StandardUnit.Count);
            }
        }));
    }

    /**
     * @param rescale the submitted sum by this multiplier. 1.0 is the identity (no rescale).
     */
    void reportSampling(Map.Entry<String, ? extends Sampling> entry, String type, double rescale, List<MetricDatum> data) {
        Sampling metric = entry.getValue();
        Snapshot snapshot = metric.getSnapshot();
        double scaledSum = sum(snapshot.getValues()) * rescale;
        final StatisticSet statisticSet = new StatisticSet()
                .withSum(scaledSum)
                .withSampleCount((double) snapshot.size())
                .withMinimum((double) snapshot.getMin() * rescale)
                .withMaximum((double) snapshot.getMax() * rescale);

        DemuxedKey key = new DemuxedKey(appendGlobalDimensions(entry.getKey()));
        Iterables.addAll(data, key.newDatums(type, new Function<MetricDatum, MetricDatum>() {
            @Override
            public MetricDatum apply(MetricDatum datum) {
                return datum.withStatisticValues(statisticSet);
            }
        }));
    }


    private long diffLast(Counting metric) {
        long count = metric.getCount();

        Long lastCount = lastPolledCounts.get(metric);
        lastPolledCounts.put(metric, count);

        if (lastCount == null) {
            lastCount = 0L;
        }
        return count - lastCount;
    }

    private long sum(long[] values) {
        long sum = 0L;
        for (long value : values) sum += value;
        return sum;
    }


    private String appendGlobalDimensions(String metric) {
        if (StringUtils.isBlank(StringUtils.trim(dimensions))) {
            return metric;
        } else {
            return metric + NAME_TOKEN_DELIMITER + dimensions;
        }
    }

    static final MetricFilter ALL = new MetricFilter() {
        @Override
        public boolean matches(String name, Metric metric) {
            return true;
        }
    };

}
