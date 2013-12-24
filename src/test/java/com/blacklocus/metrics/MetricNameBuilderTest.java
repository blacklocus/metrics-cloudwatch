package com.blacklocus.metrics;

import com.amazonaws.services.cloudwatch.model.Dimension;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Jason Dunkelberger (dirkraft)
 */
public class MetricNameBuilderTest {

    @Test
    public void test() {
        MetricNameBuilder builder = new MetricNameBuilder();
        builder.addNameToken("Token");
        builder.addDimension("key", "value", true);
        Assert.assertEquals("Token key=value*", builder.toString());

        builder.add("thing=stuff abc def*");
        Assert.assertEquals("Token abc def* key=value* thing=stuff", builder.toString());

        builder.addNameToken("herring", true);
        builder.addNameToken("option*");
        builder.addDimension(new Dimension().withName("color").withValue("red"));
        Assert.assertEquals("Token abc def* herring* option* key=value* thing=stuff color=red", builder.toString());
    }
}
