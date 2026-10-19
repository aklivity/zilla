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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class McpExecuteArgsTransformTest
{
    private static final McpExecuteArgsTransform.ProgressListener NO_OP = McpExecuteArgsTransformTest::onProgress;

    @Test
    public void shouldCaptureNameAndArguments()
    {
        Result result = feed("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\":\"New York\"}}}");

        assertThat(result.transform.name, equalTo("get_weather"));
        assertThat(result.transform.malformed, equalTo(false));
        assertThat(result.status, equalTo(Status.COMPLETED));
        assertThat(result.output, equalTo("{\"location\":\"New York\"}"));
    }

    @Test
    public void shouldCaptureNestedArguments()
    {
        Result result = feed("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}}}");

        assertThat(result.transform.name, equalTo("get_weather"));
        assertThat(result.transform.malformed, equalTo(false));
        assertThat(result.output, equalTo("{\"nested\":{\"a\":[1,2,3]},\"flag\":true,\"n\":null}"));
    }

    @Test
    public void shouldLeaveArgumentsEmptyWhenMissing()
    {
        Result result = feed("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"}}");

        assertThat(result.transform.name, equalTo("get_weather"));
        assertThat(result.transform.malformed, equalTo(false));
        assertThat(result.transform.argsSeen, equalTo(false));
        assertThat(result.output, equalTo(""));
    }

    @Test
    public void shouldReturnNullNameWhenMissing()
    {
        Result result = feed("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"arguments\":{\"location\":\"New York\"}}}");

        assertThat(result.transform.name, nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        Result result = feed("not valid json at all");

        assertThat(result.transform.name, nullValue());
        assertThat(result.status, equalTo(Status.REJECTED));
    }

    @Test
    public void shouldMarkMalformedWhenTargetArgumentsIsNotAnObject()
    {
        Result result =
            feed("{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\",\"arguments\":\"oops\"}}");

        assertThat(result.transform.malformed, equalTo(true));
        assertThat(result.status, equalTo(Status.REJECTED));
    }

    @Test
    public void shouldScanSplitAcrossWindowBoundary()
    {
        final String json =
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather_forecast\"," +
            "\"arguments\":{\"location\":\"New York\",\"days\":3,\"units\":\"imperial\"}}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final String expectedArgs = "{\"location\":\"New York\",\"days\":3,\"units\":\"imperial\"}";

        for (int split = 1; split < bytes.length; split++)
        {
            final Result result = feed(bytes, split, bytes.length);

            assertThat("failed at split=" + split, result.transform.malformed, equalTo(false));
            assertThat("failed at split=" + split, result.transform.name, equalTo("get_weather_forecast"));
            assertThat("failed at split=" + split, result.output, equalTo(expectedArgs));
        }
    }

    @Test
    public void shouldScanFedOneByteAtATime()
    {
        final String json =
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\":\"New York\"}}}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        final int[] bounds = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++)
        {
            bounds[i] = i + 1;
        }

        final Result result = feed(bytes, bounds);

        assertThat(result.transform.name, equalTo("get_weather"));
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
        final String json =
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"image\":\"" + blob + "\"}}}";
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

        assertThat(result.transform.malformed, equalTo(false));
        assertThat(result.transform.name, equalTo("get_weather"));
        assertThat(result.output, equalTo(expectedArgs));
    }

    // the upper bound (outerContentLength - argsValueStreamOffset) must never undershoot the target's own
    // arguments value's real length, across field orderings and incidental whitespace differences a real
    // caller's JSON encoder might introduce -- an undershoot would corrupt the delegate's declared
    // contentLength, not merely waste a few bytes of padding
    @Test
    public void shouldComputeArgsUpperBoundConservatively()
    {
        assertUpperBoundConservative(
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\":\"NYC\"}}}",
            "{\"location\":\"NYC\"}");
        assertUpperBoundConservative(
            "{\"name\": \"zilla__execute_tool\", \"arguments\": {\"name\": \"get_weather\", " +
            "\"arguments\": {\"location\": \"NYC\"} } }",
            "{\"location\":\"NYC\"}");
        assertUpperBoundConservative(
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\":\"NYC\"},\"extra\":true}}",
            "{\"location\":\"NYC\"}");
        assertUpperBoundConservative(
            "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"foo\",\"arguments\":{\"a\":[1,2,3]}}}",
            "{\"a\":[1,2,3]}");
    }

    private static void assertUpperBoundConservative(
        String json,
        String expectedArgs)
    {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final Result result = feed(bytes);

        assertThat("failed for: " + json, result.output, equalTo(expectedArgs));
        assertThat("failed for: " + json, result.transform.argsSeen, equalTo(true));

        final long upperBound = bytes.length - result.transform.argsValueStreamOffset;
        assertThat("failed for: " + json, upperBound, greaterThanOrEqualTo((long) expectedArgs.length()));
    }

    // argsValueStreamOffset is captured at the target's own "arguments" KEY_NAME event, before its colon --
    // so with the target's own arguments value as the last member of its wrapper object (the tightest
    // framing a caller can produce) the padding accounts for that colon (1) plus the two mandatory closing
    // braces (2) plus the one insignificant space this source's own "location": value carries that
    // canonical re-rendering drops -- landing on exactly 4, never 3 or 5, ruling out a genuine off-by-one
    @Test
    public void shouldReportZeroPaddingWhenValueIsBodyFinal()
    {
        final String json = "{\"name\":\"zilla__execute_tool\",\"arguments\":{\"name\":\"get_weather\"," +
            "\"arguments\":{\"location\": \"New York\"}}}";
        final String expectedArgs = "{\"location\":\"New York\"}";
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        final Result result = feed(bytes);

        assertThat(result.output, equalTo(expectedArgs));
        final long upperBound = bytes.length - result.transform.argsValueStreamOffset;
        final long padding = upperBound - expectedArgs.length();
        assertThat(padding, equalTo(4L));
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

        final McpExecuteArgsTransform transform = new McpExecuteArgsTransform(NO_OP);
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

    private static void onProgress()
    {
    }

    private static final class Result
    {
        private final Status status;
        private final McpExecuteArgsTransform transform;
        private final String output;

        private Result(
            Status status,
            McpExecuteArgsTransform transform,
            String output)
        {
            this.status = status;
            this.transform = transform;
            this.output = output;
        }
    }
}
