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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

public class McpToolCallArgsTransformTest
{
    private static final JsonSink DISCARD_SINK = new JsonSink()
    {
        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event)
        {
            return Status.ADVANCED;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    };

    private static final String LOCATION_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}},\"required\":[\"location\"]}";

    @Test
    public void shouldPromoteArgumentsValueToTopLevel()
    {
        Result result = feed("{\"name\":\"get_weather\",\"arguments\":{\"location\":\"New York\"}}");

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.transform.noArgumentsClosed, equalTo(false));
        assertThat(result.output, equalTo("{\"location\":\"New York\"}"));
    }

    @Test
    public void shouldPromoteArgumentsRegardlessOfFieldOrder()
    {
        Result result = feed("{\"arguments\":{\"location\":\"New York\"},\"name\":\"get_weather\"}");

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.output, equalTo("{\"location\":\"New York\"}"));
    }

    @Test
    public void shouldPromoteNestedArgumentsContent()
    {
        Result result = feed("{\"name\":\"x\",\"arguments\":{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}}");

        assertThat(result.output, equalTo("{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}"));
    }

    @Test
    public void shouldPromoteBareScalarArguments()
    {
        Result result = feed("{\"name\":\"x\",\"arguments\":\"foo\"}");

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.output, equalTo("\"foo\""));
    }

    @Test
    public void shouldSignalNoArgumentsClosedWhenArgumentsKeyMissing()
    {
        Result result = feed("{\"name\":\"x\"}");

        assertThat(result.transform.argsSeen, equalTo(false));
        assertThat(result.transform.noArgumentsClosed, equalTo(true));
        assertThat(result.status, equalTo(Status.COMPLETED));
        assertThat(result.output, equalTo(""));
    }

    @Test
    public void shouldSignalNoArgumentsClosedWhenParamsIsBareScalar()
    {
        Result result = feed("\"not-an-object\"");

        assertThat(result.transform.argsSeen, equalTo(false));
        assertThat(result.transform.noArgumentsClosed, equalTo(true));
        assertThat(result.status, equalTo(Status.COMPLETED));
        assertThat(result.output, equalTo(""));
    }

    @Test
    public void shouldSignalNoArgumentsClosedWhenParamsIsArray()
    {
        Result result = feed("[1,2,3]");

        assertThat(result.transform.argsSeen, equalTo(false));
        assertThat(result.transform.noArgumentsClosed, equalTo(true));
        assertThat(result.output, equalTo(""));
    }

    @Test
    public void shouldIgnoreSiblingKeysAfterArguments()
    {
        Result result = feed("{\"name\":\"x\",\"arguments\":{\"a\":1},\"extra\":{\"b\":{\"c\":2}}}");

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.output, equalTo("{\"a\":1}"));
    }

    @Test
    public void shouldCompleteAsSoonAsArgumentsValueCloses()
    {
        final McpToolCallArgsTransform transform = new McpToolCallArgsTransform();
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx outBuf = new UnsafeBufferEx(new byte[1024]);
        generator.wrap(outBuf, 0, outBuf.capacity());
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
            .lenient(false)
            .into(JsonEx.createSink(generator));

        // "arguments" closes well before the whole body -- COMPLETED must fire here, not wait for the
        // trailing sibling key/value or the outer object's own closing brace
        final byte[] bytes = "{\"arguments\":{\"a\":1},\"trailing\":\"ignored-until-later\"}"
            .getBytes(StandardCharsets.UTF_8);

        final Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertThat(status, equalTo(Status.COMPLETED));
        assertThat(transform.argsSeen, equalTo(true));
    }

    @Test
    public void shouldScanArgumentsKeySplitAcrossWindowBoundary()
    {
        final String json = "{\"name\":\"get_weather_forecast\",\"arguments\":{\"location\":\"New York\",\"days\":3}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final String expectedArgs = "{\"location\":\"New York\",\"days\":3}";

        for (int split = 1; split < bytes.length; split++)
        {
            final Result result = feed(bytes, split, bytes.length);

            assertThat("failed at split=" + split, result.transform.argsSeen, equalTo(true));
            assertThat("failed at split=" + split, result.output, equalTo(expectedArgs));
        }
    }

    @Test
    public void shouldScanFedOneByteAtATime()
    {
        final String json = "{\"name\":\"get_weather\",\"arguments\":{\"location\":\"New York\"}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        final int[] bounds = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++)
        {
            bounds[i] = i + 1;
        }

        final Result result = feed(bytes, bounds);

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.output, equalTo("{\"location\":\"New York\"}"));
    }

    @Test
    public void shouldScanLargeArgumentsAcrossManyFrames()
    {
        final StringBuilder blob = new StringBuilder(10_000);
        for (int i = 0; i < 10_000; i++)
        {
            blob.append((char) ('a' + (i % 26)));
        }
        final String json = "{\"name\":\"get_weather\",\"arguments\":{\"image\":\"" + blob + "\"}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final String expectedArgs = "{\"image\":\"" + blob + "\"}";

        final int chunkSize = 512;
        final int chunkCount = (bytes.length + chunkSize - 1) / chunkSize;
        final int[] bounds = new int[chunkCount];
        for (int i = 0; i < chunkCount; i++)
        {
            bounds[i] = Math.min(bytes.length, (i + 1) * chunkSize);
        }

        final Result result = feed(bytes, bounds);

        assertThat(result.transform.argsSeen, equalTo(true));
        assertThat(result.output, equalTo(expectedArgs));
    }

    // Confirms the composed argsTransform -> schema.validator(false) -> discard chain (the exact production
    // composition) reaches COMPLETED as soon as the promoted arguments value itself validates -- not waiting
    // for the whole tools/call body's own END_DOCUMENT -- and REJECTED (via the pipeline's own
    // JsonValidationException -> REJECTED conversion) when the arguments value fails the schema, in both
    // cases well before the trailing sibling content or the outer object's own closing brace is even fed.
    @Test
    public void shouldValidateArgumentsAgainstRealSchemaAndCompleteEarly()
    {
        final JsonSchema schema = JsonSchema.of(LOCATION_SCHEMA);
        final McpToolCallArgsTransform transform = new McpToolCallArgsTransform();
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
            .transform(schema.validator(false))
            .lenient(false)
            .into(DISCARD_SINK);

        final byte[] valid = "{\"arguments\":{\"location\":\"NYC\"},\"trailing\":\"ignored\"}"
            .getBytes(StandardCharsets.UTF_8);
        final Status validStatus = pipeline.transform(new UnsafeBufferEx(valid), 0, valid.length, true);

        assertThat(validStatus, equalTo(Status.COMPLETED));
        assertThat(transform.argsSeen, equalTo(true));
    }

    @Test
    public void shouldRejectArgumentsFailingRealSchema()
    {
        final JsonSchema schema = JsonSchema.of(LOCATION_SCHEMA);
        final McpToolCallArgsTransform transform = new McpToolCallArgsTransform();
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
            .transform(schema.validator(false))
            .lenient(false)
            .into(DISCARD_SINK);

        final byte[] invalid = "{\"arguments\":{\"location\":123}}".getBytes(StandardCharsets.UTF_8);
        final Status status = pipeline.transform(new UnsafeBufferEx(invalid), 0, invalid.length, true);

        assertThat(status, equalTo(Status.REJECTED));
    }

    @Test
    public void shouldRejectDefaultArgumentsWhenSchemaRequiresLocation()
    {
        final JsonSchema schema = JsonSchema.of(LOCATION_SCHEMA);
        final McpToolCallArgsTransform transform = new McpToolCallArgsTransform();
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
            .transform(schema.validator(false))
            .lenient(false)
            .into(DISCARD_SINK);

        final byte[] noArguments = "{\"name\":\"get_weather\"}".getBytes(StandardCharsets.UTF_8);
        final Status status = pipeline.transform(new UnsafeBufferEx(noArguments), 0, noArguments.length, true);

        // the composed pipeline reports COMPLETED here (a synthetic verdict -- see McpToolCallArgsTransform's
        // own javadoc); it is McpServer's job to notice noArgumentsClosed and separately validate the
        // default "{}" against the same schema, which for a schema requiring "location" fails
        assertThat(status, equalTo(Status.COMPLETED));
        assertThat(transform.argsSeen, equalTo(false));
        assertThat(transform.noArgumentsClosed, equalTo(true));

        final JsonPipeline fallback = JsonEx.stream(JsonEx.createParser())
            .transform(schema.validator(false))
            .lenient(false)
            .into(DISCARD_SINK);
        final byte[] emptyArgs = "{}".getBytes(StandardCharsets.UTF_8);
        final Status fallbackStatus = fallback.transform(new UnsafeBufferEx(emptyArgs), 0, emptyArgs.length, true);

        assertThat(fallbackStatus, equalTo(Status.REJECTED));
    }

    private static Result feed(
        String json)
    {
        return feed(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Result feed(
        byte[] bytes,
        int... splits)
    {
        final int[] bounds = splits.length == 0 ? new int[] { bytes.length } : splits;

        final McpToolCallArgsTransform transform = new McpToolCallArgsTransform();
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx outBuf = new UnsafeBufferEx(new byte[Math.max(1024, bytes.length * 2)]);
        generator.wrap(outBuf, 0, outBuf.capacity());
        final JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
            .lenient(false)
            .into(JsonEx.createSink(generator));

        final byte[] carry = new byte[bytes.length + 1024];
        int carryLen = 0;
        int from = 0;
        Status status = Status.ADVANCED;

        for (int i = 0; i < bounds.length; i++)
        {
            final int to = Math.min(bounds[i], bytes.length);
            final boolean last = i == bounds.length - 1;
            final byte[] window = new byte[carryLen + (to - from)];
            System.arraycopy(carry, 0, window, 0, carryLen);
            System.arraycopy(bytes, from, window, carryLen, to - from);

            status = pipeline.transform(new UnsafeBufferEx(window), 0, window.length, last);

            if (status == Status.STARVED)
            {
                carryLen = pipeline.remaining();
                System.arraycopy(window, window.length - carryLen, carry, 0, carryLen);
            }
            else
            {
                carryLen = 0;
            }
            from = to;
        }

        final byte[] out = new byte[generator.length()];
        outBuf.getBytes(0, out);

        return new Result(status, transform, new String(out, StandardCharsets.UTF_8));
    }

    private static final class Result
    {
        private final Status status;
        private final McpToolCallArgsTransform transform;
        private final String output;

        private Result(
            Status status,
            McpToolCallArgsTransform transform,
            String output)
        {
            this.status = status;
            this.transform = transform;
            this.output = output;
        }
    }
}
