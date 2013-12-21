CloudWatch integration for codahale metrics
===========================================
This is a [metrics reporter implementation](https://github.com/codahale/metrics/blob/master/metrics-core/src/main/java/com/codahale/metrics/ScheduledReporter.java)
from [codahale metrics](http://metrics.codahale.com/) to [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

These translations have been made to CloudWatch. Generally only the atomic data is submitted so that it can be
predictably aggregated via the CloudWatch API or UI. Codehale Metrics instances are NOT reset on
each CloudWatch report so they retain their original, reflective functionality.

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
[Codahale Metrics: Histograms](http://metrics.codahale.com/manual/core/#histograms). Viewing these metrics as
*averages* in CloudWatch is usually the most obvious indication of performance.



Metric Naming
-------------

The CloudWatchReporter constructor optionally accepts a CloudWatch namespace and permutation suffixes. Refer to
[constructor java-doc](https://github.com/blacklocus/metrics-cloudwatch/blob/master/src/main/java/com/blacklocus/metrics/CloudWatchReporter.java#L123)
for detail on permutation suffixes.


There is implicit support for CloudWatch Dimensions should you choose to use them. Any un-spaced portions of the metric
name that contain a '=' will be interpreted as CloudWatch dimensions. e.g. "CatCounter dev breed=calico" will result
in a CloudWatch metric with Metric Name "CatCounter dev" and one Dimension  { "breed": "calico" }.



Usage
-----

    repositories {
        mavenCentral()
    }

    dependencies {
        compile 'com.blacklocus:metrics-cloudwatch:0.2'
    }

See the test which generates bogus metrics from two simulated machines (threads):
[CloudWatchReporterTest.java](https://github.com/blacklocus/metrics-cloudwatch/blob/master/src/test/java/com/blacklocus/metrics/CloudWatchReporterTest.java)



License
-------

Copyright 2013 BlackLocus under [the Apache 2.0 license](LICENSE)

