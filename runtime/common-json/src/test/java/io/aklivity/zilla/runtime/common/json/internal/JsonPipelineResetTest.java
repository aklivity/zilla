/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonPipelineResetTest
{
    // Models a pooled generator returned to the pool mid-value: the first caller abandons a partial
    // array (leaving open structure in the generator), and a second caller checks the instance back out
    // and, per the pooling discipline, resets the pipeline before reuse. The reset must clear the stale
    // generator context so the next value is emitted clean (no leaked separator from the open array).
    @Test
    void shouldClearGeneratorContextOnResetForReuse()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[1024]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        generator.wrap(buffer, 0, buffer.capacity());
        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx("[1,".getBytes(UTF_8)), 0, 3, false));

        pipeline.reset();
        generator.wrap(buffer, 0, buffer.capacity());
        byte[] bytes = "{\"b\":2} ".getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"b\":2} ", new String(out, UTF_8));
    }
}
