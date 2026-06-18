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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonReporter;

class JsonPipelineRejectTest
{
    // captures the call-scoped diagnostic the pipeline pushes on a terminal REJECTED, copying the message out
    private final String[] reason = new String[1];
    private final JsonReporter reporter = d -> reason[0] = d.message();

    // Malformed JSON is a rejection of the value, surfaced as the terminal REJECTED status rather than
    // an exception escaping feed() — mirroring how the protobuf pipeline maps a decode failure.
    @Test
    void shouldRejectMalformedJson()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createSink(generator));

        byte[] bytes = "[1 2]".getBytes(UTF_8);
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldReportReasonOnMalformedJson()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .reporting(reporter)
            .into(JsonEx.createSink(generator));

        byte[] bytes = "[1 2]".getBytes(UTF_8);
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        assertEquals(Status.REJECTED, pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length));
        assertNotNull(reason[0]);
    }

    @Test
    void shouldNotReportOnValidJson()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .reporting(reporter)
            .into(JsonEx.createSink(generator));

        byte[] bytes = "[1,2]".getBytes(UTF_8);
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        assertEquals(Status.COMPLETED, pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length));
        assertNull(reason[0]);
    }
}
