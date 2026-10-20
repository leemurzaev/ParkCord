package com.parkcode.parkcord.shield;

import com.google.common.base.Ticker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class FakeTicker extends Ticker
{

    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read()
    {
        return nanos.get();
    }

    void set(long value)
    {
        nanos.set( value );
    }

    void advanceSeconds(long seconds)
    {
        nanos.addAndGet( TimeUnit.SECONDS.toNanos( seconds ) );
    }

    void advanceMillis(long millis)
    {
        nanos.addAndGet( TimeUnit.MILLISECONDS.toNanos( millis ) );
    }
}
