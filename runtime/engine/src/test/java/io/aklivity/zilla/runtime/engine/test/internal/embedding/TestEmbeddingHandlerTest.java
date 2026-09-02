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
package io.aklivity.zilla.runtime.engine.test.internal.embedding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public class TestEmbeddingHandlerTest
{
    @Test
    public void shouldEmbedDeterministically()
    {
        float[] first = TestEmbeddingHandler.generate("hello world");
        float[] second = TestEmbeddingHandler.generate("hello world");
        float[] different = TestEmbeddingHandler.generate("goodbye world");

        assertNotNull(first);
        assertEquals(8, first.length);
        assertArrayEquals(first, second, 0.0f);
        assertFalse(Arrays.equals(first, different));
    }

    @Test
    public void shouldEmbedAsyncStrictlyLaterThanCallReturns()
    {
        Deque<Runnable> deferred = new ArrayDeque<>();
        Consumer<Runnable> dispatcher = deferred::addLast;
        TestEmbeddingHandler handler = new TestEmbeddingHandler(dispatcher);

        float[] expected = TestEmbeddingHandler.generate("hello world");

        boolean[] completed = new boolean[1];
        float[][][] captured = new float[1][][];
        handler.embed(0L, 0L, 42L, List.of("hello world"), new EmbeddingHandler.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[][] results)
            {
                assertEquals(42L, contextId);
                completed[0] = true;
                captured[0] = results;
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertFalse("completion must not fire on the caller's stack", completed[0]);

        assertEquals(1, deferred.size());
        deferred.poll().run();

        assertTrue(completed[0]);
        assertEquals(1, captured[0].length);
        assertArrayEquals(expected, captured[0][0], 0.0f);
    }
}
