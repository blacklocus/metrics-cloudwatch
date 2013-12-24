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

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.blacklocus.metrics.CloudWatchReporter.NAME_DIMENSION_SEPARATOR;
import static com.blacklocus.metrics.CloudWatchReporter.NAME_PERMUTE_MARKER;
import static com.blacklocus.metrics.CloudWatchReporter.NAME_TOKEN_DELIMITER;
import static com.blacklocus.metrics.CloudWatchReporter.NAME_TOKEN_DELIMITER_RGX;
import static com.blacklocus.metrics.CloudWatchReporter.VALID_DIMENSION_PART_RGX;
import static com.blacklocus.metrics.CloudWatchReporter.VALID_NAME_TOKEN_RGX;

/**
 * A builder for the metrics name syntax defined by this module. Useful when programmatically constructing metric names.
 *
 * @author Jason Dunkelberger (dirkraft)
 */
public class MetricNameBuilder {

    private final List<String> names = new ArrayList<String>();
    private final List<String> dimensions = new ArrayList<String>();

    public MetricNameBuilder() {
    }

    public MetricNameBuilder(String nameSpec) {
        add(nameSpec);
    }

    /**
     * @param nameToken must be a single valid name segment which is summarized by the regex
     *             {@link CloudWatchReporter#VALID_NAME_TOKEN_RGX} (it may end in the permute operator).
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addNameToken(String nameToken) throws MetricsNameSyntaxException {
        nameToken = nameToken.trim();
        if (!nameToken.matches(VALID_NAME_TOKEN_RGX)) {
            throw new MetricsNameSyntaxException("Name must match " + VALID_NAME_TOKEN_RGX);
        }

        this.names.add(nameToken);
        return this;
    }

    /**
     * @param nameToken    must be a single valid name segment which is summarized by the regex
     *                {@link CloudWatchReporter#VALID_NAME_TOKEN_RGX} (it may end in the permute operator).
     * @param permute whether or not this token should permute
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addNameToken(String nameToken, boolean permute) throws MetricsNameSyntaxException {
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
     *                 There is no need to use a MetricsNameBuilder if you already have the completed string. Set it
     *                 directly to be the metric name, e.g. <pre>
     *                     metricRegistry.counter("MyMetric SomeTag* color=green machine=1.2.3.4*").inc();
     *                 </pre>
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder add(String nameSpec) throws MetricsNameSyntaxException {
        for (String token : nameSpec.split(NAME_TOKEN_DELIMITER_RGX)) {
            if (token.contains(NAME_DIMENSION_SEPARATOR)) {
                String[] dimensionTuple = token.split(NAME_DIMENSION_SEPARATOR, 2);
                addDimension(dimensionTuple[0], dimensionTuple[1]);
            } else {
                addNameToken(token);
            }
        }
        return this;
    }

    /**
     * {@link #addDimension(Dimension, boolean)} without permutation (false)
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addDimension(Dimension dimension) throws MetricsNameSyntaxException {
        return addDimension(dimension, false);
    }

    /**
     * Passes into {@link #addDimension(String, String, boolean)}
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addDimension(Dimension dimension, boolean permute) throws MetricsNameSyntaxException {
        return addDimension(dimension.getName(), dimension.getValue(), false);
    }

    /**
     * {@link #addDimension(String, String, boolean)} without permutation (false)
     *
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addDimension(String name, String value) throws MetricsNameSyntaxException {
        return addDimension(name, value, false);
    }

    /**
     * @param name of dimension
     * @param value of dimension
     * @param permute permutability of dimension
     * @return this for chaining
     * @throws MetricsNameSyntaxException on validation failure
     */
    public MetricNameBuilder addDimension(String name, String value, boolean permute) throws MetricsNameSyntaxException {
        if (!name.matches(VALID_DIMENSION_PART_RGX)) {
            throw new MetricsNameSyntaxException("Dimension name must match " + VALID_DIMENSION_PART_RGX);
        }
        if (!value.matches(VALID_DIMENSION_PART_RGX)) {
            throw new MetricsNameSyntaxException("Dimension name must match " + VALID_DIMENSION_PART_RGX);
        }

        this.dimensions.add(name + NAME_DIMENSION_SEPARATOR + value + (permute ? NAME_PERMUTE_MARKER : ""));
        return this;

    }

    /**
     * @return properly formatted metric name for use with the {@link MetricRegistry}. The CloudWatchReporter will
     * be able to demux this name spec into corresponding permutable name tokens and dimensions.
     */
    public String build() {
        return toString();
    }

    /**
     * @return properly formatted metric name for use with the {@link MetricRegistry}. The CloudWatchReporter will
     * be able to demux this name spec into corresponding permutable name tokens and dimensions.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> iterator = Iterables.concat(names, dimensions).iterator(); iterator.hasNext(); ) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(NAME_TOKEN_DELIMITER);
            }
        }
        return sb.toString();
    }


    public static class MetricsNameSyntaxException extends RuntimeException {
        public MetricsNameSyntaxException(String message) {
            super(message);
        }
    }
}
