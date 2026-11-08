/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.instanceId;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.throttleIndex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.budget.BudgetHandleRegistry;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;

public class TargetTest
{
    private static final long INITIAL_ID = 1L;
    private static final long REPLY_ID = 2L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final MutableDirectBufferEx frameBuffer = new UnsafeBufferEx(new byte[1024]);
    private final AtomicInteger closes = new AtomicInteger();

    private StreamsLayout streamsLayout;
    private Int2ObjectHashMap<MessageConsumer>[] throttles;
    private BudgetHandleRegistry budgetHandles;
    private Target target;

    @Before
    @SuppressWarnings("unchecked")
    public void init() throws IOException
    {
        final Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), folder.getRoot().toString());
        final EngineConfiguration config = new EngineConfiguration(properties);

        Files.createDirectories(config.directory());
        streamsLayout = new StreamsLayout.Builder()
            .path(config.directory().resolve("data0"))
            .streamsCapacity(config.streamsBufferCapacity())
            .readonly(false)
            .build();

        final Int2ObjectHashMap<MessageConsumer>[] streams = new Int2ObjectHashMap[64];
        throttles = new Int2ObjectHashMap[64];
        for (int index = 0; index < streams.length; index++)
        {
            streams[index] = new Int2ObjectHashMap<>();
            throttles[index] = new Int2ObjectHashMap<>();
        }

        budgetHandles = new BudgetHandleRegistry();
        target = new Target(config, 0, new UnsafeBufferEx(new byte[1024]), new Long2ObjectHashMap<>(), streams,
            new Long2ObjectHashMap<LongHashSet>(), throttles, budgetHandles);
    }

    @After
    public void close() throws Exception
    {
        target.close();
        streamsLayout.close();
    }

    @Test
    public void shouldReleaseBudgetHandleWhenReplyEnds()
    {
        attach(REPLY_ID);

        target.writeHandler().accept(end(REPLY_ID).typeId(), frameBuffer, 0, end(REPLY_ID).sizeof());

        assertReleased(REPLY_ID);
    }

    @Test
    public void shouldReleaseBudgetHandleWhenReplyAborts()
    {
        attach(REPLY_ID);

        target.writeHandler().accept(abort(REPLY_ID).typeId(), frameBuffer, 0, abort(REPLY_ID).sizeof());

        assertReleased(REPLY_ID);
    }

    @Test
    public void shouldReleaseBudgetHandleWhenInitialEnds()
    {
        attach(INITIAL_ID);

        target.writeHandler().accept(end(INITIAL_ID).typeId(), frameBuffer, 0, end(INITIAL_ID).sizeof());

        assertReleased(INITIAL_ID);
    }

    @Test
    public void shouldReleaseBudgetHandleWhenInitialAborts()
    {
        attach(INITIAL_ID);

        target.writeHandler().accept(abort(INITIAL_ID).typeId(), frameBuffer, 0, abort(INITIAL_ID).sizeof());

        assertReleased(INITIAL_ID);
    }

    @Test
    public void shouldRetainBudgetHandleWhileStreamIsOpen()
    {
        attach(REPLY_ID);

        final BeginFW begin = new BeginFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(REPLY_ID)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(0)
            .affinity(0L)
            .build();
        target.writeHandler().accept(begin.typeId(), frameBuffer, 0, begin.sizeof());

        assertThat(closes.get(), equalTo(0));
        assertThat(throttles[throttleIndex(REPLY_ID)].get(instanceId(REPLY_ID)) == null, equalTo(false));
    }

    private void attach(
        long streamId)
    {
        throttles[throttleIndex(streamId)].put(instanceId(streamId), MessageConsumer.NOOP);
        budgetHandles.register(streamId, closes::incrementAndGet);
    }

    private void assertReleased(
        long streamId)
    {
        assertThat(closes.get(), equalTo(1));
        assertThat(throttles[throttleIndex(streamId)].get(instanceId(streamId)), nullValue());

        budgetHandles.release(streamId);
        assertThat(closes.get(), equalTo(1));
    }

    private EndFW end(
        long streamId)
    {
        return new EndFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(0)
            .build();
    }

    private AbortFW abort(
        long streamId)
    {
        return new AbortFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(0)
            .build();
    }
}
