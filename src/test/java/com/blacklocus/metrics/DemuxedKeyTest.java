package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class DemuxedKeyTest {

    @Test
    public void testSingle() {
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
    public void testMulti() {
        DemuxedKey key = new DemuxedKey("TokenOne Two");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testMultiPerm() {
        DemuxedKey key = new DemuxedKey("Three option*");
        List<MetricDatum> data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(2, data.size());

        key = new DemuxedKey("Three*");
        data = Lists.newArrayList(key.newDatums("test", Functions.<MetricDatum>identity()));
        Assert.assertEquals(1, data.size());
    }


}
