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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class TestModelMaskPipelineTest
{
    @Test
    public void shouldMaskWordStraddlingFragments()
    {
        for (int fragment = 1; fragment <= 12; fragment++)
        {
            assertEquals("fragment " + fragment, "my ****** is safe, a ****** too",
                run("my secret is safe, a secret too", fragment, 256));
        }
    }

    @Test
    public void shouldDrainAcrossSmallDestination()
    {
        assertEquals("my ****** is safe", run("my secret is safe", 5, 4));
    }

    @Test
    public void shouldRetainOverlapUntilMoreInputOrCompletion()
    {
        final TestModelMaskPipeline pipeline = new TestModelMaskPipeline(List.of("secret"));
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[64]);
        final byte[] bytes = "abcdefghij".getBytes(UTF_8);

        final ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, Flags.INIT, new UnsafeBufferEx(bytes), 0,
            bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.UNDERFLOW, first.status());
        assertEquals(5, first.produced());

        final ModelPipelineResult last = pipeline.transform(0L, 0L, 0L, Flags.FIN, new UnsafeBufferEx(new byte[0]), 0, 0,
            dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, last.status());
        assertEquals(5, last.produced());
        assertFalse(pipeline.identity());
    }

    private static String run(
        String text,
        int fragment,
        int capacity)
    {
        final TestModelMaskPipeline pipeline = new TestModelMaskPipeline(List.of("secret"));
        final byte[] bytes = text.getBytes(UTF_8);
        final UnsafeBufferEx src = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[capacity]);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int offset = 0; offset < bytes.length; offset += fragment)
        {
            final int limit = Math.min(offset + fragment, bytes.length);
            final int flags = (offset == 0 ? Flags.INIT : 0) | (limit == bytes.length ? Flags.FIN : 0);

            int position = offset;
            ModelStatus status;

            do
            {
                final ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags & ~(position > offset ? Flags.INIT : 0),
                    src, position, limit, dst, 0, capacity);

                status = result.status();
                position += result.consumed();

                final byte[] produced = new byte[result.produced()];
                dst.getBytes(0, produced);
                output.write(produced, 0, produced.length);
            }
            while (status == ModelStatus.OVERFLOW);
        }

        return output.toString(UTF_8);
    }
}
