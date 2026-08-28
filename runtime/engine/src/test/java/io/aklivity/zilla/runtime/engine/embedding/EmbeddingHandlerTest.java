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
package io.aklivity.zilla.runtime.engine.embedding;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EmbeddingHandlerTest
{
    @Test
    public void shouldResolveNothingFromNoneHandlerSync()
    {
        EmbeddingHandler handler = EmbeddingHandler.NONE;

        assertNull(handler.embed(0L, 0L, "text"));
    }

    @Test
    public void shouldCompleteWithNothingFromNoneHandlerAsync()
    {
        EmbeddingHandler handler = EmbeddingHandler.NONE;
        float[][] captured = new float[1][];

        handler.embed(0L, 0L, 0L, "text", new EmbeddingHandler.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[] result)
            {
                captured[0] = result;
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertNull(captured[0]);
    }
}
