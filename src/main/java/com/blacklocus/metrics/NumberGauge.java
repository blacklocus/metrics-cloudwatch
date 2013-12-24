package com.blacklocus.metrics;

import com.codahale.metrics.Gauge;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe {@link Gauge} implementation for any sort of Number.
 *
 * @author Jason Dunkelberger (dirkraft)
 */
public class NumberGauge implements Gauge<Number> {

    private final AtomicReference<Number> value;

    /**
     * Initialized with value of 0.
     */
    public NumberGauge() {
        this(0);
    }

    public NumberGauge(Number initialValue) {
        value = new AtomicReference<Number>(initialValue);
    }

    @Override
    public Number getValue() {
        return value.get();
    }

    public void setValue(Number n) {
        this.value.set(n);
    }
}
