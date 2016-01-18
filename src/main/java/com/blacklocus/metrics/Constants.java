package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public class Constants {

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

    // Should line up with constants. Name should not contain any special character, and may optionally end with the
    // permute marker.
    public static final String VALID_NAME_TOKEN_RGX = "[^\\s=\\*]+\\*?";
    public static final String VALID_DIMENSION_PART_RGX = "[^\\s=\\*]+";

    /**
     * Default {@link Dimension#name} for the type of metric submission, e.g. gauge, counterSum, meterSum, ...
     */
    public static final String DEF_DIM_NAME_TYPE = "metricType";

    /**
     * Default {@link Dimension#value} for the <i>metric type</i> dimension added to all gauge metrics.
     * Only numeric gauges can be reported.
     */
    public static final String DEF_DIM_VAL_GAUGE = "gauge";

    /**
     * {@link Counter#getCount()} returns the total of the values put in the counter.
     * This is "counterCount" instead of just "count" or "counter" in order to search for it exclusive
     * to "meterCount". The CloudWatch console indexes names in a way where it is beneficial that one metric
     * identity is not a substring or subset of another metric's identity. In addition "counterCount" captures
     * that it was a coda hale {@link Counter}, vs a {@link Meter} which includes the same {@link Counting} semantics.
     * <p>
     * Final note latter "count" is preferred to "sum" to reduce word collision when aggregating over (summing) these
     * metrics, i.e. spoken "I am summing the counts" vs "I am summing the sums"; it's a little less confusing
     * to use different words.
     */
    public static final String DEF_DIM_VAL_COUNTER_COUNT = "counterCount";

    /**
     * {@link Meter#getCount()} returns the total of the values put in the counter, just like {@link Counter#getCount()}.
     */
    public static final String DEF_DIM_VAL_METER_COUNT = "meterCount";

    /**
     * {@link Histogram#getCount()} returns the number of samples recorded, UNlike {@link Counter} and {@link Meter}.
     */
    public static final String DEF_DIM_VAL_HISTO_SAMPLES = "histoSamples";

    /**
     * {@link Histogram#getSnapshot()} can be mapped into a {@link StatisticSet}. Histogram's have sliding window
     * mechanics.
     */
    public static final String DEF_DIM_VAL_HISTO_STATS = "histoStats";

    /**
     * {@link Timer#getCount()} returns the number of samples recorded, UNlike {@link Counter} and {@link Meter}.
     */
    public static final String DEF_DIM_VAL_TIMER_SAMPLES = "timerSamples";

    /**
     *
     */
    public static final String DEF_DIM_VAL_TIMER_STATS = "timerStats";

}
