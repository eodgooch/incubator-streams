/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.streams.local.builders;

import org.apache.streams.core.StreamBuilder;
import org.apache.streams.threaded.builders.StreamBuilderEventHandler;
import org.apache.streams.threaded.builders.StreamsGraphElement;
import org.apache.streams.threaded.builders.ThreadedStreamBuilder;
import org.apache.streams.local.test.processors.PassThroughStaticCounterProcessor;
import org.apache.streams.local.test.processors.SimpleProcessorCounter;
import org.apache.streams.local.test.providers.NumericMessageProvider;
import org.apache.streams.local.test.providers.NumericMessageProviderDelayed;
import org.apache.streams.local.test.writer.DatumCounterWriter;
import org.apache.streams.threaded.tasks.StatusCounts;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * These tests ensure that StreamsBuilder works
 */
public class ThreadedStreamBuilderDelayTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadedStreamBuilderDelayTest.class);

    @Test
    public void delayedWriterTest() {

        int numDatums = 5;
        ThreadedStreamBuilder builder = new ThreadedStreamBuilder();
        DatumCounterWriter writer = new DatumCounterWriter(50); // give the DatumCounter a 50ms delay
        SimpleProcessorCounter proc1 = new SimpleProcessorCounter();
        SimpleProcessorCounter proc2 = new SimpleProcessorCounter();

        builder.newReadCurrentStream("prov1", new NumericMessageProvider(numDatums))
                .addStreamsProcessor("proc1", proc1, 1, "prov1")
                .addStreamsProcessor("proc2", proc2, 1, "prov1")
                .addStreamsPersistWriter("w1", writer, 1, "proc1", "proc2");

        builder.start();

        assertTrue(builder.getUpdateCounts().get("prov1").getType().equals("provider"));
        assertTrue(builder.getUpdateCounts().get("proc1").getType().equals("processor"));
        assertTrue(builder.getUpdateCounts().get("proc2").getType().equals("processor"));
        assertTrue(builder.getUpdateCounts().get("w1").getType().equals("writer"));

        assertEquals("Number in should equal number out", numDatums, proc1.getMessageCount());
        assertEquals("Number in should equal number out", numDatums, proc2.getMessageCount());
        assertEquals("Number in should equal number out", numDatums * 2, writer.getDatumsCounted());

        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }

    /**
     * This test stops the stream after 2 datums have been written. There is a 3 second delay between
     * datums ensuring that we have ample time to support that everything was cleaned up properly.
     */
    @Test
    public void testStopStream() {

        int numDatums = 5;
        final ThreadedStreamBuilder builder = new ThreadedStreamBuilder();
        NumericMessageProviderDelayed numericMessageProviderDelayed = new NumericMessageProviderDelayed(numDatums, 3000);
        DatumCounterWriter writer = new DatumCounterWriter();

        builder.newReadCurrentStream("prov1", numericMessageProviderDelayed)
                .addStreamsPersistWriter("w1", writer, 1, "prov1")
                .addEventHandler(new StreamBuilderEventHandler() {
                    @Override
                    public void update(Map<String, StatusCounts> counts, List<StreamsGraphElement> graph) {
                        if (counts.get("w1").getSuccess() == 2) {
                            builder.stop();
                        }
                    }
                });

        builder.start();
        assertEquals("Number in should equal number out", 2, writer.getDatumsCounted());

        assertTrue("Writer: Prepare called", writer.wasPrepeareCalled());
        assertTrue("Provider: Prepare Called", numericMessageProviderDelayed.wasCleanupCalled());

        assertTrue("Provider: CleanUp Called", numericMessageProviderDelayed.wasCleanupCalled());
        assertTrue("Writer: CleanUp called", writer.wasCleanupCalled());
    }

    @Test
    public void delayedProcessorTest() {
        int numDatums = 5;
        StreamBuilder builder = new ThreadedStreamBuilder();
        DatumCounterWriter writer = new DatumCounterWriter();
        SimpleProcessorCounter proc1 = new SimpleProcessorCounter(50);
        SimpleProcessorCounter proc2 = new SimpleProcessorCounter(25);

        builder.newReadCurrentStream("prov1", new NumericMessageProvider(numDatums))
                .addStreamsProcessor("proc1", proc1, 1, "prov1")
                .addStreamsProcessor("proc2", proc2, 1, "prov1")
                .addStreamsPersistWriter("w1", writer, 1, "proc1", "proc2");

        builder.start();

        assertEquals("Number in should equal number out", numDatums, proc1.getMessageCount());
        assertEquals("Number in should equal number out", numDatums, proc2.getMessageCount());
        assertEquals("Number in should equal number out", numDatums * 2, writer.getDatumsCounted());

        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }


    @Test
    public void delayedProviderTest()  {
        int numDatums = 10;
        StreamBuilder builder = new ThreadedStreamBuilder();

        NumericMessageProviderDelayed provider = new NumericMessageProviderDelayed(numDatums, 10);
        PassThroughStaticCounterProcessor processor = new PassThroughStaticCounterProcessor(25);
        DatumCounterWriter writer = new DatumCounterWriter(125);
        builder.newReadCurrentStream("sp1", provider)
                .addStreamsProcessor("proc1", processor, 1, "sp1")
                .addStreamsPersistWriter("writer1", writer, 1, "proc1");
        builder.start();
        assertEquals("Should have same number", numDatums, writer.getDatumsCounted());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }


    @Test
    public void streamStressTest() {

        int numConcurrentStreams = 20;

        final List<AtomicBoolean> runningList = new ArrayList<AtomicBoolean>();
        final List<AtomicBoolean> failureMarker = new ArrayList<AtomicBoolean>();

        for(int i = 0; i < numConcurrentStreams; i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        AtomicBoolean done = new AtomicBoolean(false);
                        try {
                            synchronized (ThreadedStreamBuilderDelayTest.class) {
                                runningList.add(done);
                            }
                            dualDelayedMergedTest();
                        } catch (Throwable e) {
                            failureMarker.add(new AtomicBoolean(true));
                        } finally {
                            done.set(true);
                        }
                    }
                }).start();
        }

        while(runningList.size() < numConcurrentStreams) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        boolean shouldStop = false;
        while(!shouldStop) {
            shouldStop = true;
            synchronized (ThreadedStreamBuilderDelayTest.class) {
                for (AtomicBoolean b : runningList) {
                    shouldStop = b != null && b.get() && shouldStop;
                }
            }
        }

        // check to see if anything bubbled up.
        for(AtomicBoolean failure : failureMarker) {
            if (failure.get()) {
                fail("this failed...");
            }
        }
    }

    @Test
    public void noDataTest()  {
        int numDatums = 8;
        StreamBuilder builder = new ThreadedStreamBuilder();
        PassThroughStaticCounterProcessor processor = new PassThroughStaticCounterProcessor(10);
        DatumCounterWriter writer = new DatumCounterWriter(20);
        builder.newReadCurrentStream("sp1", new NumericMessageProviderDelayed(numDatums, 10))
                .addStreamsProcessor("proc1", processor, 1, "sp1")
                .addStreamsPersistWriter("writer1", writer, 1, "proc1");
        builder.start();
        assertEquals("Should have same number", numDatums, writer.getDatumsCounted());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }

    @Test
    public void dualDelayedProcessorsLinearTest() {
        int numDatums = 12;

        StreamBuilder builder = new ThreadedStreamBuilder();
        PassThroughStaticCounterProcessor proc1 = new PassThroughStaticCounterProcessor(10);
        PassThroughStaticCounterProcessor proc2 = new PassThroughStaticCounterProcessor(20);
        DatumCounterWriter writer = new DatumCounterWriter();

        builder.newReadCurrentStream("sp1", new NumericMessageProviderDelayed(numDatums))
                .addStreamsProcessor("proc1", proc1, 1, "sp1")
                .addStreamsProcessor("proc2", proc2, 1, "proc1")
                .addStreamsPersistWriter("writer1", writer, 1, "proc2");

        builder.start();

        assertEquals("Should have same number", numDatums, writer.getDatumsCounted());
        assertEquals("Should have same number", numDatums, proc1.getMessageCount());
        assertEquals("Should have same number", numDatums, proc2.getMessageCount());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }

    @Test
    public void dualDelayedProcessorsBranchTest() {
        int numDatums = 10;

        StreamBuilder builder = new ThreadedStreamBuilder();
        PassThroughStaticCounterProcessor proc1 = new PassThroughStaticCounterProcessor(10);
        PassThroughStaticCounterProcessor proc2 = new PassThroughStaticCounterProcessor(20);
        DatumCounterWriter writer = new DatumCounterWriter();

        builder.newReadCurrentStream("sp1", new NumericMessageProviderDelayed(numDatums))
                .addStreamsProcessor("proc1", proc1, 1, "sp1")
                .addStreamsProcessor("proc2", proc2, 1, "sp1")
                .addStreamsPersistWriter("writer1", writer, 1, "proc1", "proc2");

        builder.start();

        assertEquals("Should have same number", 2 * numDatums, writer.getDatumsCounted());
        assertEquals("Should have same number", numDatums, proc1.getMessageCount());
        assertEquals("Should have same number", numDatums, proc2.getMessageCount());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }

    @Test
    public void dualDelayedProviderTest() {
        int numDatums = 4;

        StreamBuilder builder = new ThreadedStreamBuilder();
        PassThroughStaticCounterProcessor proc1 = new PassThroughStaticCounterProcessor(20);
        DatumCounterWriter writer = new DatumCounterWriter();

        builder.newReadCurrentStream("sp1", new NumericMessageProviderDelayed(numDatums, 30))
                .newReadCurrentStream("sp2", new NumericMessageProviderDelayed(numDatums, 20))
                .addStreamsProcessor("proc1", proc1, 1, "sp1", "sp2")
                .addStreamsPersistWriter("writer1", writer, 1, "proc1");

        builder.start();

        assertEquals("Should have same number", 2 * numDatums, writer.getDatumsCounted());
        assertEquals("Should have same number", 2 * numDatums, proc1.getMessageCount());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }

    @Test
    public void dualDelayedMergedTest() throws Exception {
        int numDatums = 6;

        StreamBuilder builder = new ThreadedStreamBuilder();
        PassThroughStaticCounterProcessor proc1 = new PassThroughStaticCounterProcessor(20);
        PassThroughStaticCounterProcessor proc2 = new PassThroughStaticCounterProcessor(50);
        DatumCounterWriter writer = new DatumCounterWriter(150);

        builder.newReadCurrentStream("sp1", new NumericMessageProviderDelayed(numDatums, 50))
                .newReadCurrentStream("sp2", new NumericMessageProviderDelayed(numDatums, 20))
                .addStreamsProcessor("proc1", proc1, 1, "sp1")
                .addStreamsProcessor("proc2", proc2, 1, "sp2")
                .addStreamsPersistWriter("writer1", writer, 1, "proc1", "proc2");

        builder.start();

        assertEquals("Should have same number", 2 * numDatums, writer.getDatumsCounted());
        assertEquals("Should have same number", numDatums, proc1.getMessageCount());
        assertEquals("Should have same number", numDatums, proc2.getMessageCount());
        assertTrue("cleanup called", writer.wasCleanupCalled());
        assertTrue("cleanup called", writer.wasPrepeareCalled());
    }
}