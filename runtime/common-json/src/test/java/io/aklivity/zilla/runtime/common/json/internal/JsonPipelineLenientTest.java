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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;

class JsonPipelineLenientTest
{
    // captures the call-scoped diagnostic the pipeline pushes on a reject or a reported validation failure
    private final String[] reason = new String[1];
    private final JsonReporter reporter = d -> reason[0] = d.message();

    private static final String SCHEMA = "{\"properties\":{\"id\":{\"type\":\"string\"}}}";

    // STRICT (the default): a schema-constraint violation on structurally-valid JSON rejects and reports.
    @Test
    void shouldRejectSchemaViolationUnderStrict()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator(false))
            .lenient(false)
            .reporting(reporter)
            .into(generator);

        byte[] bytes = "{\"id\":123}".getBytes(UTF_8);
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0, output.capacity());

        assertEquals(Status.REJECTED, result.status());
        assertEquals(0, result.produced());
        assertNotNull(reason[0]);
    }

    // LENIENT: the SAME violation passes the original document through unchanged, and the event still fires.
    @Test
    void shouldPassSchemaViolationThroughUnderLenient()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator(true))
            .lenient(true)
            .reporting(reporter)
            .into(generator);

        byte[] bytes = "{\"id\":123}".getBytes(UTF_8);
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0, output.capacity());

        assertEquals(Status.COMPLETED, result.status());
        assertEquals(bytes.length, result.produced());
        assertEquals("{\"id\":123}", output.getStringWithoutLengthUtf8(0, result.produced()));
        // the validation-failed diagnostic still fires under LENIENT
        assertNotNull(reason[0]);
    }

    // A genuine parse failure rejects under STRICT.
    @Test
    void shouldRejectMalformedUnderStrict()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator(false))
            .lenient(false)
            .reporting(reporter)
            .into(generator);

        byte[] bytes = "[1 2]".getBytes(UTF_8);
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0, output.capacity());

        assertEquals(Status.REJECTED, result.status());
    }

    // A genuine parse failure ALSO rejects under LENIENT — lenient never tolerates malformed input.
    @Test
    void shouldRejectMalformedUnderLenient()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator(true))
            .lenient(true)
            .reporting(reporter)
            .into(generator);

        byte[] bytes = "[1 2]".getBytes(UTF_8);
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0, output.capacity());

        assertEquals(Status.REJECTED, result.status());
        assertEquals(0, result.produced());
    }

    // A conforming document completes cleanly and reports nothing, in either mode.
    @Test
    void shouldCompleteConformingUnderLenient()
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[128]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(SCHEMA).validator(true))
            .lenient(true)
            .reporting(reporter)
            .into(generator);

        byte[] bytes = "{\"id\":\"abc\"}".getBytes(UTF_8);
        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0, output.capacity());

        assertEquals(Status.COMPLETED, result.status());
        assertEquals("{\"id\":\"abc\"}", output.getStringWithoutLengthUtf8(0, result.produced()));
        assertNull(reason[0]);
    }
}
