CloudWatch integration for codahale metrics
===========================================

This is a metrics reporter implementation
([codahale/metrics/ScheduledReporter.java](https://github.com/codahale/metrics/blob/master/metrics-core/src/main/java/com/codahale/metrics/ScheduledReporter.java))
from [codahale metrics](http://metrics.codahale.com/) (v3.x) to [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

[![Build Status](https://travis-ci.org/blacklocus/metrics-cloudwatch.svg)](https://travis-ci.org/blacklocus/metrics-cloudwatch)



### Maven Dependency (Gradle) ###

##### Current Stable Release #####

```gradle
repositories {
    mavenCentral()
}

dependencies {
    compile 'com.blacklocus:metrics-cloudwatch:0.4.0'
}
```

Other dependency formats on [mvnrepository.com](http://mvnrepository.com/artifact/com.blacklocus/metrics-cloudwatch/0.4.0)

#### Current Snapshot Release ####

```gradle
repositories {
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots/'
    }
}

dependencies {
    compile 'com.blacklocus:metrics-cloudwatch:0.4.1-SNAPSHOT'
}
```


#### Code Integration ####

Here is a bare minimum example, and how we generally use the CloudWatchReporter. We create a class to represent a namespace
of metrics and provide methods enumerating the metrics recorded. The reporter interval is at 1 minute which will report
new data every minute for the last minute to CloudWatch. 1 minute is the minimum resolution of CloudWatch. If you wanted
to save money on API requests, you could go every 5 minutes or longer, keeping in mind that each data point to CloudWatch
then represents 5 minutes, and you shouldn't view periods smaller than that in the CloudWatch console.

```java
class ExampleMetrics {

    private final MetricRegistry registry = new MetricRegistry();

    public ExampleMetrics() {
        new CloudWatchReporterBuilder()
                .withNamespace(ExampleMetrics.class.getSimpleName())
                .withRegistry(registry)
                .build()
                .start(1, TimeUnit.MINUTES);
    }

    public void sentThatThing() {
        registry.counter("sentThatThing").inc();
    }

    public void gotABatchOfThoseThingsYaSentMe(int count) {
        registry.counter("gotThatThing").inc(count);
    }
}

public class ExampleApp {

    private final ExampleMetrics exampleMetrics;

    public ExampleApp(ExampleMetrics exampleMetrics) {
        this.exampleMetrics = exampleMetrics;
    }

    public void sendAThing() {
        // ... somewhere in the code not so far away ...
        exampleMetrics.sentThatThing();
    }
    
    public void receiveSomeThings(List<Object> thoseThings) {
        exampleMetrics.gotABatchOfThoseThingsYaSentMe(thoseThings.size());
        // ... and so on ...
    }
}
```

If you already have a Codahale MetricsRegistry, you only need to give it to a CloudWatchReporter to start submitting
all your existing metrics code to CloudWatch. There are certain symbols which if part of metric names will result
in RuntimeExceptions in the CloudWatchReporter thread. These metrics should be renamed to avoid these symbols
to be used with the CloudWatchReporter.

See the test which generates bogus metrics from two simulated machines (threads):
[CloudWatchReporterTest.java](https://github.com/blacklocus/metrics-cloudwatch/blob/master/src/test/java/com/blacklocus/metrics/CloudWatchReporterTest.java)


### Metric submission types ###

These translations from codahale metric classes have been made to CloudWatch. Generally only the atomic data (in AWS SDK terms, one of
[MetricDatum](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/MetricDatum.html) or
[StatisticSet](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/StatisticSet.html))
is submitted so that it can be predictably aggregated via the CloudWatch API or UI. Codahale Metrics instances are NOT
reset on
each CloudWatch report so they retain their original, cumulative functionality. The following`type` is submitted with
each metric as a CloudWatch Dimension.

| Metric    | Type           | sent statistic meaning per interval                                                     |
| --------- | -------------- | --------------------------------------------------------------------------------------- |
| Gauge     | gauge          | current value (if numeric)                                                              |
| Counter   | counterSum     | change in sum since last report                                                         |
| Meter     | meterSum       | change in sum since last report                                                         |
| Histogram | histogramCount | change in samples since last report                                                     |
|           | histogramSet   | CloudWatch StatisticSet based on Snapshot                                               |
| Timer     | timerCount     | change in samples since last report                                                     |
|           | timerSet       | CloudWatch StatisticSet based on Snapshot; sum / 1,000,000 (nanos -> millis)            |

`histogramSum` and `timerSum` do not submit differences per polling interval due to the possible sliding history
mechanics in each of them. Instead all available values are summed and counted to be sent as the simplified CloudWatch
StatisticSet. In this way, multiple submissions at the same time aggregate predictably with the standard CloudWatch UI
functions. As a consequence, any new process using these abstractions when viewed in CloudWatch as *sums* or *samples*
over time will steadily grow until the Codahale Metrics Reservoir decides to eject values: see
[Codahale Metrics: Histograms](http://metrics.codahale.com/manual/core/#histograms). Viewing/aggregating these metrics as
*averages* is usually the most explainable way to use that data.






Metric Naming
-------------

###### CloudWatch Dimensions support ######

There is implicit support for CloudWatch Dimensions should you choose to use them. Any un-spaced portions of the metric
name that contain a '=' will be interpreted as CloudWatch dimensions. e.g. "CatCounter dev breed=calico" will result
in a CloudWatch metric with Metric Name "CatCounter dev" and one Dimension  { "breed": "calico" }.

Additionally, CloudWatch does not aggregate metrics over dimensions on custom metrics
([see CloudWatch documentation](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#Dimension)).
As a possible convenience to ourselves we can just submit these metrics in duplicate, once for each
*scope*. This is best understood by example.

> Example Scenario: Number of Requests to Service X, is a Counter with name "ServiceX Requests"

We have multiple machines in the *Service X* cluster. We want a count over all machine as well as counts
for individual machines. To enable duplicate submission per each scope (global and per-machine), we could
name the counter **ServiceX Requests machine={insert machine id here}**.
At runtime this might turn out to be

```java
// machine A
metricRegistry.counter("ServiceX Requests machine=1.2.3.4");
// machine B
metricRegistry.counter("ServiceX Requests machine=7.5.3.1");
```

The = signifies a CloudWatch Dimension. This segment would thus be translated into a dimension with dimension
name *machine* and dimension value *1.2.3.4* or *7.5.3.1* depending on the machine. Each machine submits one metric to
CloudWatch. In the CloudWatch UI there would be only two metrics.

###### Multiple-scope submissions support ######

So continuing the previous scenario, in order to get a total count for all machines, you would have to add
all such counters together regardless of their *machine* dimension. We just use the CloudWatch console which
cannot do this for you. So instead of building a whole new UI to do that for us, we have both machines
also submit the metric without their unique **machine** dimension value, which can be achieved by certain
symbols in the metric's name.

```java
// machine A
metricRegistry.counter("ServiceX Requests machine=1.2.3.4*");
// machine B
metricRegistry.counter("ServiceX Requests machine=7.5.3.1*");
```

The * at the end of a dimension (or plain token) segment signifies that this component is *permutable*. The metric must be
submitted at least once with this component, and once without. The CloudWatchReporter on each machine will resolve this
to 2 metrics on each machine, where the *global* metric overlaps and individual machines' remain unique.

  - `ServiceX Requests machine=1.2.3.4*` resolves to submissions...
    * ServiceX Requests
    * ServiceX Requests machine=1.2.3.4
  - `ServiceX Requests machine=7.5.3.1*` resolves to submissions...
    * ServiceX Requests
    * ServiceX Requests machine=7.5.3.1

In this way now in the CloudWatch UI we will find 3 unique metrics, one for each individual machine with dimension={ip
address}, and one *ServiceX Requests* which scopes all machines together.

Both simple name tokens and dimensions can be permuted (appended with *). Note that this can produce a multiplicative
number of submissions. e.g. Some machine constructs this individualized metric name

```java
metricRegistry.counter("ServiceX Requests group-tag* machine=1.2.3.4* strategy=dolphin* environment=development");
```

This resolves to all of the following CloudWatch metrics.

  - ServiceX Requests environment=development
  - ServiceX Requests environment=development machine=1.2.3.4
  - ServiceX Requests environment=development strategy=dolphin
  - ServiceX Requests environment=development machine=1.2.3.4 strategy=dolphin
  - ServiceX Requests group-tag environment=development
  - ServiceX Requests group-tag environment=development machine=1.2.3.4
  - ServiceX Requests group-tag environment=development strategy=dolphin
  - ServiceX Requests group-tag environment=development machine=1.2.3.4 strategy=dolphin

In case you forgot, AWS costs money. Metrics and monitoring can easily become the most expensive part
of your stack. So be wary of metrics explosions.





License
-------

Copyright 2015 BlackLocus under [the Apache 2.0 license](LICENSE)

