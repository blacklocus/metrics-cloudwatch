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
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
* @author Jason Dunkelberger (dirkraft)
*/
class DemuxedKey {

    final PermutableChain<String> nameChain;
    final PermutableChain<Dimension> dimensionChain;

    DemuxedKey(String s) {
        String[] segments = s.split(CloudWatchReporter.NAME_TOKEN_DELIMITER_RGX);

        PermutableChain<String> names = null;
        PermutableChain<Dimension> dimensions = null;

        // Build NameSegmentChain in reverse. Dimension order is irrelevant.
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];

            boolean permutable = segment.endsWith(CloudWatchReporter.NAME_PERMUTE_MARKER);
            if (permutable) {
                segment = segment.substring(0, segment.length() - 1);
            }

            if (segment.contains(CloudWatchReporter.NAME_DIMENSION_SEPARATOR)) {
                String[] dimensionParts = segment.split(CloudWatchReporter.NAME_DIMENSION_SEPARATOR, 2);
                Dimension dimension = new Dimension().withName(dimensionParts[0]).withValue(dimensionParts[1]);
                dimensions = new PermutableChain<Dimension>(dimension, permutable, dimensions);

            } else {
                assert !segment.contains(CloudWatchReporter.NAME_PERMUTE_MARKER);
                names = new PermutableChain<String>(segment, permutable, names);
            }
        }

        this.nameChain = names;
        this.dimensionChain = dimensions;
    }

    Iterable<MetricDatum> newDatums(String type, Function<MetricDatum, MetricDatum> datumSpecification) {

        // All dimension sets include the type dimension.
        PermutableChain<Dimension> withDimensionChain = new PermutableChain<Dimension>(
                new Dimension().withName(CloudWatchReporter.METRIC_TYPE_DIMENSION).withValue(type),
                false,
                dimensionChain
        );

        List<MetricDatum> data = new ArrayList<MetricDatum>();

        for (Iterable<String> nameSet : nameChain) {
            String name = StringUtils.join(nameSet, " ");
            if (StringUtils.isBlank(name)) {
                // If all name segments are permutable, there is one combination where all of them are omitted.
                // This is expected and supported but of course can not be submitted.
                continue;
            }
            for (Iterable<Dimension> dimensionSet : withDimensionChain) {
                data.add(datumSpecification.apply(
                        new MetricDatum().withMetricName(name).withDimensions(Lists.newArrayList(dimensionSet))
                ));
            }
        }

        return data;
    }
}

class PermutableChain<T> implements Iterable<Iterable<T>> {

    final T token;
    final boolean permutable;
    final PermutableChain<T> nextSegment;


    PermutableChain(T token, boolean permutable, PermutableChain<T> nextSegment) {
        this.token = token;
        this.permutable = permutable;
        this.nextSegment = nextSegment;
    }

    @Override
    public Iterator<Iterable<T>> iterator() {
        return new Iterator<Iterable<T>>() {

            int permutation = permutable ? 2 : 1;
            Iterator<Iterable<T>> nextSegmentIt = nextSegment == null ? null : nextSegment.iterator();

            @Override
            public boolean hasNext() {
                boolean isTail = nextSegmentIt == null;
                if (isTail) {
                    return permutation > 0;
                } else {
                    return permutation > 0 && nextSegmentIt != null && nextSegmentIt.hasNext();
                }
            }

            @Override
            public Iterable<T> next() {
                assert permutation > 0 && permutation <= 2;
                boolean isTail = nextSegmentIt == null;
                if (isTail) {
                    if (permutation == 2) {
                        permutation = 1;
                        return Collections.emptyList();
                    } else {
                        assert permutation == 1;
                        permutation = 0;
                        return ImmutableList.of(token);
                    }
                } else {
                    if (permutation == 2) {
                        Iterable<T> next = nextSegmentIt.next();
                        if (!nextSegmentIt.hasNext()) {
                            permutation = 1;
                            nextSegmentIt = nextSegment.iterator();
                        }
                        return next;
                    } else {
                        assert permutation == 1;
                        Iterable<T> next = Iterables.concat(ImmutableList.of(token), nextSegmentIt.next());
                        if (!nextSegmentIt.hasNext()) {
                            permutation = 0;
                            nextSegmentIt = null;
                        }
                        return next;
                    }
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Nope.");
            }
        };
    }
    /*
    Three option*
    hasNext == tail && exhausted || next.hasNext()   next: return
    Three -> ""
    Three -> option
     */
}
