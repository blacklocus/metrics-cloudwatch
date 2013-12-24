package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class DemuxedKeyTest {

    @Test
    public void testSingleName() {
        DemuxedKey key = new DemuxedKey("SingleToken");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", new Function<MetricDatum, MetricDatum>() {
            @Override
            public MetricDatum apply(MetricDatum datum) {
                return datum.withValue(3.5);
            }
        }));
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testMultiName() {
        DemuxedKey key = new DemuxedKey("TokenOne Two");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testDefaultDimension() {
        DemuxedKey key = new DemuxedKey("wheee");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
        MetricDatum metricDatum = data.get(0);
        Assert.assertEquals(1, metricDatum.getDimensions().size());
        Dimension dimension = metricDatum.getDimensions().get(0);
        Assert.assertEquals(CloudWatchReporter.METRIC_TYPE_DIMENSION, dimension.getName());
        Assert.assertEquals("test", dimension.getValue());
    }

    @Test
    public void testMultiDimension() {
        DemuxedKey key = new DemuxedKey("wheee color=orange token animal=okapi");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
        MetricDatum metricDatum = data.get(0);

    }

    @Test
    public void testMixedNameAndDimension() {

    }

    @Test
    public void testMultiPermName() {
        DemuxedKey key = new DemuxedKey("One*");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(containsExactly(data, "One"));

        key = new DemuxedKey("Two option*");
        data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(2, data.size());
        // Note that token order must be maintained
        Assert.assertTrue(containsExactly(data, "Two", "Two option"));

        key = new DemuxedKey("Three double* option*");
        data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(4, data.size());
        Assert.assertTrue(containsExactly(data, "Three", "Three double", "Three option", "Three double option"));

        key = new DemuxedKey("All* keys* optional*");
        data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(7, data.size());
        Assert.assertTrue(containsExactly(data, "All", "keys", "optional", "All keys", "keys optional", "All optional", "All keys optional"));

        key = new DemuxedKey("Tail* required");
        data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(2, data.size());
        Assert.assertTrue(containsExactly(data, "Tail required", "required"));
    }

    @Test
    public void testMultiPermDimensions() {
        DemuxedKey key = new DemuxedKey("Name key=value* color=green* machine=localhost");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("testMultiPermDimensions", Functions.<MetricDatum>identity()));
        Assert.assertEquals(4, data.size());
        Assert.assertTrue(containsExactly(data, "Name"));
        Assert.assertTrue(containsExactly(data,
                d("type", "testMultiPermDimensions", "machine", "localhost"),
                d("type", "testMultiPermDimensions", "key", "value", "machine", "localhost"),
                d("type", "testMultiPermDimensions", "color", "green", "machine", "localhost"),
                d("type", "testMultiPermDimensions", "key", "value", "color", "green", "machine", "localhost")
        ));
    }

    @Test
    public void testComplex() {

    }

    boolean containsExactly(List<MetricDatum> data, String... names) {
        return Sets.symmetricDifference(Sets.newHashSet(Lists.transform(data, new Function<MetricDatum, String>() {
            @Override
            public String apply(MetricDatum input) {
                return input.getMetricName();
            }
        })), Sets.newHashSet(names)).isEmpty();
    }

    boolean containsExactly(List<MetricDatum> data, List<Dimension>... dimensions) {
        return Sets.symmetricDifference(Sets.newHashSet(Lists.transform(data, new Function<MetricDatum, List<Dimension>>() {
            @Override
            public List<Dimension> apply(MetricDatum input) {
                return input.getDimensions();
            }
        })), Sets.newHashSet(dimensions)).isEmpty();
    }

    List<Dimension> d(String... keyValuePairs) {
        List<Dimension> dimensions = new ArrayList<Dimension>(keyValuePairs.length / 2);
        for (int i = 0; i < keyValuePairs.length; i+= 2) {
            dimensions.add(new Dimension().withName(keyValuePairs[i]).withValue(keyValuePairs[i + 1]));
        }
        return dimensions;
    }
}
