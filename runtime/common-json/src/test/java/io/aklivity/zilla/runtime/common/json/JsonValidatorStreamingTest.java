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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// Issue #1926: a scalar position whose applicable schema needs no value content (no pattern/enum/const/
// minLength/maxLength/numeric bounds keyword applies) should stream fragment-by-fragment through the
// validator (forward-and-suppress) rather than being reassembled whole and held to JsonParserEx.MAX_VALUE_SIZE.
// A position whose schema does need content still reassembles the value whole (Option A), including still
// failing closed against the cap.
class JsonValidatorStreamingTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);

    @Test
    void shouldStreamUnconstrainedFragmentedRootValuePastValueSizeCap()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16)))
            .transform(JsonSchema.of("{\"type\":\"string\"}").validator())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // no keyword reads the string's content: each window's chunk (10 chars) is forwarded and consumed
        // immediately rather than retained, so the 30-char total never grows the retained backlog past the
        // 16-char MAX_VALUE_SIZE cap, unlike a decline-and-reassemble stage that would accumulate all of it
        byte[] f1 = "\"aaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbb".getBytes(UTF_8);
        byte[] f3 = "cccccccccc\"".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f3), 0, f3.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("\"aaaaaaaaaabbbbbbbbbbcccccccccc\"", new String(out, UTF_8));
    }

    @Test
    void shouldStreamUnconstrainedFragmentedPropertyValuePastValueSizeCap()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        String schema = "{\"type\":\"object\",\"properties\":{\"data\":{\"type\":\"string\"}}}";
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16)))
            .transform(JsonSchema.of(schema).validator())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // the applicable schema for "data" is type-only: routing through the object property must resolve
        // the same no-content position as the root case, streaming the value past the small cap. The key
        // and opening quote arrive in their own window so the value's own window matches the root case
        // exactly (a window mixing key bytes and value bytes changes the tokenizer's own fill-the-window
        // bookkeeping, which is incidental to what this test is proving).
        byte[] f0 = "{\"data\":".getBytes(UTF_8);
        byte[] f1 = "\"aaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbb".getBytes(UTF_8);
        byte[] f3 = "cccccccccc\"}".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f0), 0, f0.length, false));
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f3), 0, f3.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"data\":\"aaaaaaaaaabbbbbbbbbbcccccccccc\"}", new String(out, UTF_8));
    }

    @Test
    void shouldStillCapConstrainedFragmentedValueAtValueSize()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16)))
            .transform(JsonSchema.of("{\"type\":\"string\",\"minLength\":1}").validator())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // minLength reads the value's content: this position keeps the Option A decline-and-reassemble path,
        // so the declined backlog keeps growing window over window and still fails closed past the cap
        byte[] f1 = "\"aaaaaaaaaa".getBytes(UTF_8);
        byte[] f2 = "bbbbbbbbbb".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, false);

        assertEquals(Status.REJECTED, status);
    }

    @Test
    void shouldValidateConstrainedFragmentedValueThatSatisfiesSchema()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"string\",\"maxLength\":5}").validator())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // "abc" (3 chars) reassembled whole from two fragments still satisfies maxLength:5
        byte[] f1 = "\"ab".getBytes(UTF_8);
        byte[] f2 = "c\"".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, true);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("\"abc\"", new String(out, UTF_8));
    }

    @Test
    void shouldRejectConstrainedFragmentedValueThatViolatesSchema()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"string\",\"maxLength\":5}").validator())
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        // "abcdefghij" (10 chars) reassembled whole from two fragments violates maxLength:5
        byte[] f1 = "\"abcde".getBytes(UTF_8);
        byte[] f2 = "fghij\"".getBytes(UTF_8);
        assertEquals(Status.STARVED, pipeline.transform(new UnsafeBufferEx(f1), 0, f1.length, false));
        Status status = pipeline.transform(new UnsafeBufferEx(f2), 0, f2.length, true);

        assertEquals(Status.REJECTED, status);
    }
}
