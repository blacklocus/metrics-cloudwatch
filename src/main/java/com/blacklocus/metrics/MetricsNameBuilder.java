package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.Dimension;

import java.util.ArrayList;
import java.util.List;

import static com.blacklocus.metrics.CloudWatchReporter.NAME_DIMENSION_SEPARATOR;
import static com.blacklocus.metrics.CloudWatchReporter.NAME_PERMUTE_MARKER;
import static com.blacklocus.metrics.CloudWatchReporter.VALID_DIMENSION_PART_RGX;
import static com.blacklocus.metrics.CloudWatchReporter.VALID_NAME_TOKEN_RGX;

/**
 * A builder for the metrics name syntax defined by this module. Useful when programmatically constructing metric names.
 *
 * @author Jason Dunkelberger (dirkraft)
 */
public class MetricsNameBuilder {

    private final List<String> names = new ArrayList<String>();
    private final List<String> dimensions = new ArrayList<String>();

    public MetricsNameBuilder(String nameSpec) {
        add(nameSpec);
    }

    /**
     * @param nameToken must be a single valid name segment which is summarized by the regex
     *             {@value CloudWatchReporter#VALID_NAME_TOKEN_RGX} (it may end in the permute operator).
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricsNameBuilder addNameToken(String nameToken) throws MetricsNameSyntaxException {
        nameToken = nameToken.trim();
        if (!nameToken.matches(VALID_NAME_TOKEN_RGX)) {
            throw new MetricsNameSyntaxException("Name must match " + VALID_NAME_TOKEN_RGX);
        }

        this.names.add(nameToken);
        return this;
    }

    /**
     * @param nameToken    must be a single valid name segment which is summarized by the regex
     *                {@value CloudWatchReporter#VALID_NAME_TOKEN_RGX} (it may end in the permute operator).
     * @param permute whether or not this token should permute
     * @return this for chaining
     */
    public MetricsNameBuilder addNameToken(String nameToken, boolean permute) {
        if (permute && !nameToken.endsWith(NAME_PERMUTE_MARKER)) {
            nameToken += NAME_PERMUTE_MARKER;
        } else if (!permute && nameToken.endsWith(NAME_PERMUTE_MARKER)) {
            nameToken = nameToken.substring(0, nameToken.length() - 1);
        }
        return addNameToken(nameToken);
    }

    /**
     * @param nameSpec a string of encoded name tokens and dimensions, e.g. "MyMetric SomeTag* color=green machine=1.2.3.4*".
     *                 A metric name of this format is already suitable for direct use with metrics reported by the
     *                 {@link CloudWatchReporter} and demuxes into corresponding dimensions and permutations.
     *                 There is no need to use a MetricsNameBuilder if you already have this string. Set it directly
     *                 to be the metric name, e.g. <pre>
     *                     metricRegistry.counter("MyMetric SomeTag* color=green machine=1.2.3.4*").inc();
     *                 </pre>
     * @return this for chaining
     */
    public MetricsNameBuilder add(String nameSpec) {

    }

    /**
     * {@link #addDimension(Dimension, boolean)} without permutation (false)
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException
     */
    public MetricsNameBuilder addDimension(Dimension dimension) throws MetricsNameSyntaxException {
        return addDimension(dimension, false);
    }

    /**
     * Passes into {@link #addDimension(String, String, boolean)}
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException
     */
    public MetricsNameBuilder addDimension(Dimension dimension, boolean permute) {
        return addDimension(dimension.getName(), dimension.getValue(), false);
    }

    /**
     * {@link #addDimension(String, String, boolean)} without permutation (false)
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException
     */
    public MetricsNameBuilder addDimension(String name, String value) {

    }

    /**
     *
     * @param name
     * @param value
     * @param permute
     * @return this for chaining
     * @throws MetricsNameSyntaxException
     */
    public MetricsNameBuilder addDimension(String name, String value, boolean permute) {
        if (!dimensionName.matches(VALID_DIMENSION_PART_RGX)) {
            throw new MetricsNameSyntaxException("Dimension name must match " + VALID_DIMENSION_PART_RGX);
        }
        if (!dimensionValue.matches(VALID_DIMENSION_PART_RGX)) {
            throw new MetricsNameSyntaxException("Dimension name must match " + VALID_DIMENSION_PART_RGX);
        }

        this.dimensions.add(dimensionName + NAME_DIMENSION_SEPARATOR + dimensionValue);
        return this;

    }

    @Override
    public String toString() {

    }


    public static class MetricsNameSyntaxException extends RuntimeException {
        public MetricsNameSyntaxException(String message) {
            super(message);
        }
    }
}
