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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

final class LlmToolTransformRunner
{
    private static final int[] WINDOWS = {64, 65, 100, 128, 1000};
    private static final int[] BOUNDS = {24, 40, 64, 4096};
    private static final int REJECT_BOUND = 4096;

    static void assertTransforms(
        JsonTransform transform,
        String json,
        String expected)
    {
        for (int window : WINDOWS)
        {
            for (int bound : BOUNDS)
            {
                final ByteArrayOutputStream output = new ByteArrayOutputStream();
                final boolean accepted = run(transform, json, window, bound, output);

                assertThat("accepted window " + window + " bound " + bound, accepted, is(true));
                assertThat("window " + window + " bound " + bound, output.toString(UTF_8), equalTo(expected));
            }
        }
    }

    static void assertRejects(
        JsonTransform transform,
        String json)
    {
        for (int window : WINDOWS)
        {
            final boolean accepted = run(transform, json, window, REJECT_BOUND, new ByteArrayOutputStream());

            assertThat("rejected window " + window, accepted, is(false));
        }
    }

    static String json(
        String text)
    {
        return text.replace('\'', '"');
    }

    static String quote(
        String text)
    {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static boolean run(
        JsonTransform transform,
        String json,
        int window,
        int bound,
        ByteArrayOutputStream output)
    {
        final LlmSectionPipeline pipeline = new LlmSectionPipeline(transform, new UnsafeBufferEx(new byte[bound]));
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);

        pipeline.begin(new LlmSectionAttributes());

        boolean accepted = true;

        for (int offset = 0; accepted && offset < bytes.length; offset += window)
        {
            final int length = Math.min(window, bytes.length - offset);
            final boolean last = offset + length == bytes.length;

            accepted = pipeline.feed(input, offset, length, last,
                (buffer, index, size, done) -> collect(output, buffer, index, size));
        }

        return accepted;
    }

    private static void collect(
        ByteArrayOutputStream output,
        DirectBufferEx buffer,
        int index,
        int size)
    {
        final byte[] chunk = new byte[size];

        buffer.getBytes(index, chunk);
        output.write(chunk, 0, size);
    }

    private LlmToolTransformRunner()
    {
    }
}
