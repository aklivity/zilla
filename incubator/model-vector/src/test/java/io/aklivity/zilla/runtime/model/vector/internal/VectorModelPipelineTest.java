/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.model.vector.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.model.vector.VectorModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.engine.test.internal.embedding.TestEmbeddingHandler;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class VectorModelPipelineTest
{
    private static final int FLAGS_FIN = 0x01;

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private VectorModelHandlerImpl handler;

    @Before
    public void init()
    {
        EngineContext context = mock(EngineContext.class);
        EmbeddingHandler embedding = new TestEmbeddingHandler(tasks::add);
        when(context.supplyEmbedding(anyLong())).thenReturn(embedding);

        // Raw: TestStoreHandler's own lock-entry/watcher value types are package-private.
        ConcurrentMap<String, String> entries = new ConcurrentHashMap<>();
        ConcurrentMap listeners = new ConcurrentHashMap();
        ConcurrentMap locks = new ConcurrentHashMap();
        StoreHandler store = new TestStoreHandler(null, tasks::add, entries, listeners, locks);
        when(context.supplyStore(anyLong())).thenReturn(store);
        when(context.signaler()).thenReturn(mock(Signaler.class));

        VectorModelConfig config = VectorModelConfig.builder()
            .embedding("moderator0")
            .reject("reject this message")
            .threshold(0.99)
            .store("cache0")
            .build();

        handler = new VectorModelHandlerImpl(context, config);
        drain();
    }

    @Test
    public void shouldAcceptDissimilarMessage()
    {
        // GIVEN
        int[] resumed = new int[1];
        VectorModelPipeline pipeline = new VectorModelPipeline(handler, () -> resumed[0]++);
        UnsafeBufferEx src = new UnsafeBufferEx("a completely unrelated message".getBytes(StandardCharsets.UTF_8));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[128]);

        // WHEN
        ModelPipelineResult suspended = pipeline.transform(
            0L, 0L, 0L, FLAGS_FIN, src, 0, src.capacity(), dst, 0, dst.capacity());

        // THEN
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(resumed[0], equalTo(0));

        // WHEN
        drain();

        // THEN
        assertThat(resumed[0], equalTo(1));

        // WHEN
        ModelPipelineResult resolved = pipeline.transform(
            0L, 0L, 0L, 0x00, src, 0, 0, dst, 0, dst.capacity());

        // THEN
        assertThat(resolved.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(pipeline.identity(), equalTo(true));
    }

    @Test
    public void shouldRejectMatchingMessage()
    {
        // GIVEN
        int[] resumed = new int[1];
        VectorModelPipeline pipeline = new VectorModelPipeline(handler, () -> resumed[0]++);
        UnsafeBufferEx src = new UnsafeBufferEx("reject this message".getBytes(StandardCharsets.UTF_8));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[128]);

        // WHEN
        ModelPipelineResult suspended = pipeline.transform(
            0L, 0L, 0L, FLAGS_FIN, src, 0, src.capacity(), dst, 0, dst.capacity());
        drain();

        // THEN
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(resumed[0], equalTo(1));

        // WHEN
        ModelPipelineResult resolved = pipeline.transform(
            0L, 0L, 0L, 0x00, src, 0, 0, dst, 0, dst.capacity());

        // THEN
        assertThat(resolved.status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldResumeOnlyOnce()
    {
        // GIVEN
        int[] resumed = new int[1];
        VectorModelPipeline pipeline = new VectorModelPipeline(handler, () -> resumed[0]++);
        UnsafeBufferEx src = new UnsafeBufferEx("another unrelated message".getBytes(StandardCharsets.UTF_8));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[128]);

        // WHEN
        pipeline.transform(0L, 0L, 0L, FLAGS_FIN, src, 0, src.capacity(), dst, 0, dst.capacity());
        drain();
        pipeline.transform(0L, 0L, 0L, 0x00, src, 0, 0, dst, 0, dst.capacity());
        pipeline.reset();

        // THEN
        assertThat(resumed[0], equalTo(1));
    }

    private void drain()
    {
        while (!tasks.isEmpty())
        {
            tasks.poll().run();
        }
    }
}
