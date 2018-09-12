### Forked From [blacklocus/metrics-cloudwatch](https://github.com/blacklocus/metrics-cloudwatch) ###

CloudWatch integration for codahale metrics
===========================================


This is a metrics reporter implementation
([codahale/metrics/ScheduledReporter.java](https://github.com/codahale/metrics/blob/master/metrics-core/src/main/java/com/codahale/metrics/ScheduledReporter.java))
from [codahale metrics](http://metrics.codahale.com/) (v3.x) to [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

[![Build Status](https://travis-ci.org/blacklocus/metrics-cloudwatch.svg)](https://travis-ci.org/blacklocus/metrics-cloudwatch)

### Table of Contents ###

  - [dependency](#maven-dependency-gradle)
  - [usage](#usage)
  - [special characters in metric names](#special-characters-in-metric-names)
  - [development](#development)
  - [license](#license)



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


### Usage ###

Here is a bare minimum example, and how we generally use the CloudWatchReporter. We create a class to represent a namespace
of metrics and provide methods enumerating the metrics recorded. The reporter interval is at 1 minute which will report
new data every minute for the last minute to CloudWatch. 1 minute is the minimum resolution of CloudWatch. If you wanted
to save money on API requests, you could go every 5 minutes or longer, keeping in mind that each data point to CloudWatch
then represents 5 minutes, and you shouldn't view periods smaller than that in the CloudWatch console.

<strong>Please prefer the Builder.</strong> Legacy users can continue to use the CloudWatchReporter constructors directly for backwards-compatibility with older versions.

```java
class ExampleMetrics {

    private final MetricRegistry registry = new MetricRegistry();

    public ExampleMetrics() {
        // The builder has many options, but namespace and registry are the minimum.
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

If you already have a Codahale MetricsRegistry, you only need to give it to a CloudWatchReporterBuilder and build a reporter to start submitting
all your existing metrics code to CloudWatch. Note that some symbols in the metric names have special meaning explained below.

In the test code, there is a test app that generates bogus metrics from two simulated machines (threads):
[CloudWatchReporterTest.java](https://github.com/blacklocus/metrics-cloudwatch/blob/master/src/test/java/com/blacklocus/metrics/CloudWatchReporterTest.java)


### Metric types ###

The CloudWatch API speaks in terms of 
[MetricDatum (AWS docs)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/MetricDatum.html) and
[StatisticSet (AWS docs)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/StatisticSet.html). Code Hale's metric classes are thus translated into these constructs in the most direct way possible. The metric classes are NOT reset, so that they retain their original, cumulative functionality. If you haven't you should also read all of the CloudWatch overview: [CloudWatch Concepts (AWS docs)](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html). It will significantly ease understanding translations from the metrics classes to CloudWatch. 

The CloudWatchReporter adds the `metricType` dimension as follows, and submits 

| Class     | Dimension Value | sent statistic meaning per interval                                                     |
| --------- | --------------- | --------------------------------------------------------------------------------------- |
| Gauge     | gauge           | If numeric, .getValue() as MetricDatum. Non-numeric ignored.                            |
| Counter   | counterCount    | Diff in .getCount() since last report as MetricDatum.                                   |
| Meter     | meterCount      | Diff in .getCount() since last report as MetricDatum.                                   |
| Histogram | histoSamples    | Diff in .getCount() (Histogram returns samples) since last report as MetricDatum.       |
|           | histoStats†     | StatisticSet based on .getSnapshot().                                                   |
| Timer     | timerSamples    | Diff in .getCount() (Timer returns samples) since last report as MetricDatum.           |
|           | timerStats†     | StatisticSet based on .getSnapshot(). sum / 1,000,000 (nanos -> millis)                 |

The dimension name and values for each metric type are configurable in the CloudWatchReporterBuilder.

† - For `histoStats` and `timerStats`, you have to consider what the Snapshot actually is to understand how they are
translated to StatisticSets. In a nutshell there is a sliding window of history. At each reporter interval all
available values are read to compute the parts of a CloudWatch StatisticSet: the min, max, sum, average, and samples
(number of data points).

If you plan on seriously using any of this at scale, you should apportion time to go read the code (CloudWatchReporter and Coda Hale metrics classes) to understand
exactly what the metrics classes capture, and how that information gets translated into CloudWatch.


#### Special characters in metric names ####

Dimension support __name=value__: Any un-spaced tokens of the metric
name that contain a '=' will be interpreted as CloudWatch dimensions. e.g. "CatCounter dev breed=calico" will result
in a CloudWatch metric with Metric Name "CatCounter dev" and one Dimension  { "breed": "calico" }.

Duplicate submission support __token*__: Neither the CloudWatch service nor web console will aggregate metrics *across dimensions on custom metrics*
([see CloudWatch documentation](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#Dimension)).
For convenience, we can just submit these metrics in duplicate, once with the dimension and once without (the aggregate over all values of this dimension).

The following example shows how you might name your metrics to submit them to CloudWatch to accomplish this.


##### Example metric naming #####

We have multiple machines in the *Service X* cluster. We want a count over all machines as well as counts
for individual machines. To submit to each machine-specific and machine-ignornat, we 
name the counter **ServiceX Requests machine={insert machine id here}**.

In this example, this turns out to be 

```java
// machine A
metricRegistry.counter("ServiceX Requests machine=1.2.3.4");
// machine B
metricRegistry.counter("ServiceX Requests machine=7.5.3.1");
```

The = signifies a CloudWatch Dimension. This segment would thus be translated into a dimension with dimension
name *machine* and dimension value *1.2.3.4* or *7.5.3.1* depending on the machine. Each machine submits one metric to
CloudWatch. In the CloudWatch UI there would be only two metrics.

To get the aggregate of this metric over all machines, we would add all of the metrics regardless of machine together.
Unfortunately, we use the CloudWatch web console, which will not aggregate across dimensions. So instead we submit the metric twice from each machine: once machine-specific with the machine dimension, and once without the machine dimension.
The metric without the machine dimension represents performance across all machines.

The CloudWatchReporter provided by this project can do that by adding the `*` to any part of the name. In this case we want to *permute* the machine dimension so by appending `*` to the end of the machine dimension, the name string becomes 

    ServiceX Requests machine={insert machine id here}*

and this counter will now be sent twice for any machine. In our example we had two machines 1.2.3.4 and 7.5.3.1.
So all of the metric submissions look like this across all machines.

  - machine 1.2.3.4 submits to CloudWatch two metrics named
    * `ServiceX Requests` and 
    * `ServiceX Requests machine=1.2.3.4`
  - machine 7.5.3.1 submits to CloudWatch two metrics named
    * `ServiceX Requests` and
    * `ServiceX Requests machine=7.5.3.1`

Any *name token* can be permuted by appending it with `*`, not just dimensions. **But be warned**, permutations
can grow exponentially such as in this example.

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



Development
-----------

    git clone git@github.com:blacklocus/metrics-cloudwatch
    cd metrics-cloudwatch
    ./gradlew idea

Open the metrics-cloudwatch.ipr. Do NOT enable gradle integration in IntelliJ.



License
-------

Copyright 2013-2016 BlackLocus under [the Apache 2.0 license](LICENSE)

