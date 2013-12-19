CloudWatch integration for codahale metrics
===========================================
This is a [metrics reporter implementation](https://github.com/codahale/metrics/blob/master/metrics-core/src/main/java/com/codahale/metrics/ScheduledReporter.java)
from [codahale metrics](http://metrics.codahale.com/) to [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

Only two metric types are currently relayed to CloudWatch

  - Counters - The difference since the last report is sent every polling interval.
  - Meters - The internal counter is sent as normal Counters are.
  - ~~Gauges~~ - Not sent
  - ~~Histograms~~ - Not sent
  - ~~Timers~~ - Note sent




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

  - Send other Meter rates
  - Figure out which other metric types can be usefully conveyed to CloudWatch



License
-------

Copyright 2013 BlackLocus under [the Apache 2.0 license](LICENSE)

