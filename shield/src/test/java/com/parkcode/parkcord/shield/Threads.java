package com.parkcode.parkcord.shield;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

final class Threads
{

    private Threads()
    {
    }

    static void run(int count, IntConsumer body) throws Exception
    {
        CyclicBarrier start = new CyclicBarrier( count );
        ExecutorService pool = Executors.newFixedThreadPool( count );
        try
        {
            List<Future<?>> futures = new ArrayList<>();
            for ( int i = 0; i < count; i++ )
            {
                int thread = i;
                futures.add( pool.submit( () ->
                {
                    start.await();
                    body.accept( thread );
                    return null;
                } ) );
            }
            for ( Future<?> future : futures )
            {
                future.get( 60, TimeUnit.SECONDS );
            }
        } finally
        {
            pool.shutdownNow();
        }
    }
}
