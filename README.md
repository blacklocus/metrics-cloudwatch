CloudWatch integration for codahale metrics
===========================================
This is a [metrics reporter implementation](https://github.com/codahale/metrics/blob/master/metrics-core/src/main/java/com/codahale/metrics/ScheduledReporter.java)
from [codahale metrics](http://metrics.codahale.com/) to [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

Only count metrics are currently relayed to CloudWatch as those translate directly rather naturally.

  - Counters - The difference since the last report is sent every polling interval.
  - Meters - The internal counter is sent as normal Counters are.
  - Gauges - If the value is numeric, the current value of it is sent every polling interval.
  - Histograms - The internal counter is sent as normal Counters are.
  - Timers - The internal counter is sent as normal Counters are.

There is implicit support for CloudWatch dimensions should you choose to use them. Any un-spaced portions of the metric
name that contain a '=' will be interpreted as CloudWatch dimensions. e.g. "CatCounter dev breed=calico" will result
in a CloudWatch metric with Metric Name: "CatCounter dev" and one Dimension: [ "breed" => "calico" ].


Usage
-----

This artifact while useful isn't really "complete" in our opinion and so will live as a snapshot in

    repositories {
        maven {
            name = 'sonatype-snapshots'
            url 'https://oss.sonatype.org/content/repositories/snapshots'
        }
    }

as

    dependencies {
        compile 'com.blacklocus:metrics-cloudwatch:0.1-SNAPSHOT'
    }




Futures
-------

  - Are there are other Metric computations that are useful to translate into CloudWatch? It seems that some values
    could be sent just to leverage free tools (e.g. CloudWatch UI) but would be confusing when aggregated - aggregations
    of aggregations. Example: cluster of machines each sending average computation time per work item. If one machine
    averages 10ms over 200 items and another 30ms over 100 items. Need to carefully consider how to submit this data
    to CloudWatch so that the aggregate average over both machines computes correctly to 16.7 ms, *not* 20ms.



License
-------

Copyright 2013 BlackLocus under [the Apache 2.0 license](LICENSE)

