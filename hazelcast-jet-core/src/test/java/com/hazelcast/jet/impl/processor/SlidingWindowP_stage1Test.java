/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.processor;

import com.hazelcast.jet.TimestampKind;
import com.hazelcast.jet.TimestampedEntry;
import com.hazelcast.jet.Watermark;
import com.hazelcast.jet.accumulator.LongAccumulator;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map.Entry;

import static com.hazelcast.jet.aggregate.AggregateOperations.summingLong;
import static com.hazelcast.jet.Util.entry;
import static com.hazelcast.jet.WindowDefinition.slidingWindowDef;
import static com.hazelcast.jet.processor.Processors.accumulateByFrame;
import static com.hazelcast.jet.test.TestSupport.testProcessor;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertTrue;

@Category({QuickTest.class, ParallelTest.class})
@RunWith(HazelcastParallelClassRunner.class)
public class SlidingWindowP_stage1Test {

    private static final long KEY = 77L;

    private SlidingWindowP<Entry<Long, Long>, Long, ?> processor;

    @Before
    @SuppressWarnings("unchecked")
    public void before() {
        processor = (SlidingWindowP<Entry<Long, Long>, Long, ?>) accumulateByFrame(
                x -> KEY,
                Entry::getKey,
                TimestampKind.EVENT,
                slidingWindowDef(16, 4), // this will get converted to tumblingWindowDef(4)
                summingLong(Entry<Long, Long>::getValue)
        ).get();
    }

    @After
    public void after() {
        assertTrue("map not empty after emitting everything: " + processor.tsToKeyToAcc,
                processor.tsToKeyToAcc.isEmpty());
    }

    @Test
    public void smokeTest() {
        testProcessor(() -> processor,
                asList(
                        entry(0L, 1L), // to frame 4
                        entry(1L, 1L), // to frame 4
                        wm(4), // closes frame 4
                        entry(4L, 1L), // to frame 8
                        entry(5L, 1L), // to frame 8
                        entry(8L, 1L), // to frame 12
                        entry(8L, 1L), // to frame 12
                        wm(8), // closes frame 8
                        entry(8L, 1L), // to frame 12
                        wm(12),
                        wm(16),
                        wm(20) // closes everything
                ),
                asList(
                        frame(4, 2),
                        wm(4),
                        frame(8, 2),
                        wm(8),
                        frame(12, 3),
                        wm(12),
                        wm(16),
                        wm(20)
                ), true, false, false, false, Object::equals);
    }

    @Test
    public void when_noEvents_then_wmsEmitted() {
        List<Watermark> someWms = asList(
                wm(4),
                wm(8),
                wm(12)
        );

        testProcessor(() -> processor, someWms, someWms, true, false, false, false, Object::equals);
    }

    @Test
    public void when_batch_then_emitEverything() {
        long start = System.nanoTime();
        testProcessor(() -> processor,
                asList(
                        entry(0L, 1L), // to frame 4
                        entry(4L, 1L), // to frame 8
                        entry(8L, 1L), // to frame 12
                        wm(4) // closes frame 4
                        // no WM to emit any window, everything should be emitted in complete as if we received
                        // wm(4), wm(8), wm(12)
                ),
                asList(
                        frame(4, 1),
                        wm(4),
                        frame(8, 1),
                        frame(12, 1)
                ), true, false, false, true, Object::equals);

        long processTime = System.nanoTime() - start;
        // this is to test that there is no iteration from current watermark up to Long.MAX_VALUE, which
        // will take too long.
        assertTrue("process took too long: " + processTime, processTime < MILLISECONDS.toNanos(300));
    }

    @Test
    public void when_wmNeverReceived_then_emitEverythingInComplete() {
        long start = System.nanoTime();
        testProcessor(() -> processor,
                asList(
                        entry(0L, 1L), // to frame 4
                        entry(4L, 1L) // to frame 8
                        // no WM to emit any window, everything should be emitted in complete as if we received
                        // wm(4), wm(8)
                ),
                asList(
                        frame(4, 1),
                        frame(8, 1)
                ), true, false, false, true, Object::equals);

        long processTime = System.nanoTime() - start;
        // this is to test that there is no iteration from current watermark up to Long.MAX_VALUE, which
        // will take too long.
        assertTrue("process took too long: " + processTime, processTime < MILLISECONDS.toNanos(300));
    }

    private static TimestampedEntry<Long, LongAccumulator> frame(long timestamp, long value) {
        return new TimestampedEntry<>(timestamp, KEY, new LongAccumulator(value));
    }

    private static Watermark wm(long timestamp) {
        return new Watermark(timestamp);
    }
}
