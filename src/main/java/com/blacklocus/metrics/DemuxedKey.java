package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.List;

/**
* @author Jason Dunkelberger (dirkraft)
*/
class DemuxedKey {

    final PermutableChain<String> names;
    final PermutableChain<Dimension> dimensions;

    DemuxedKey(String s) {
        String[] segments = s.split(CloudWatchReporter.NAME_TOKEN_DELIMITER);

        PermutableChain<String> names = null;
        PermutableChain<Dimension> dimensions = null;

        // Build NameSegmentChain in reverse. Dimension order is irrelevant.
        for (int i = segments.length - 1; i >= 0; i++) {
            String segment = segments[i];

            boolean permutable = segment.endsWith(CloudWatchReporter.NAME_PERMUTATION_MARKER);
            if (permutable) {
                segment = segment.substring(0, segment.length() - 1);
            }

            if (segment.contains(CloudWatchReporter.NAME_DIMENSION_SEPARATOR)) {
                String[] dimensionParts = segment.split(CloudWatchReporter.NAME_DIMENSION_SEPARATOR, 2);
                Dimension dimension = new Dimension().withName(dimensionParts[0]).withValue(dimensionParts[1]);
                dimensions = new PermutableChain<Dimension>(dimension, permutable, dimensions);

            } else {
                assert !segment.contains(CloudWatchReporter.NAME_PERMUTATION_MARKER);
                names = new PermutableChain<String>(segment, permutable, names);
            }
        }

        this.names = names;
        this.dimensions = dimensions;
    }

    /**
     * @return new MetricDatum initialized with {@link #name} and {@link #dimensions}
     */
    Iterable<MetricDatum> newDatums(String type, Function<MetricDatum, MetricDatum> datumSpecification) {

        // generate Dimension permutations



        return null;
//            return Iterables.concat(Arrays.asList(), null);
//        dimensions.add(new Dimension().withName(CloudWatchReporter.METRIC_TYPE_DIMENSION).withValue(type));
//        dimensions.addAll(this.dimensions);
//        return new MetricDatum().withMetricName(name).withDimensions(dimensions);
    }
}

class PermutableChain<T> {

    final T token;
    final boolean permutable;
    final PermutableChain nextSegment;


    PermutableChain(T token, boolean permutable, PermutableChain nextSegment) {
        this.token = token;
        this.permutable = permutable;
        this.nextSegment = nextSegment;
    }
}
