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
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

final class LlmToolTransformRunner
{
    private static final int[] WINDOWS = {1, 7, 13, 64, 65, 100, 128, 1000};
    private static final int[] BOUNDS = {24, 40, 64, 4096};
    private static final int REJECT_BOUND = 4096;

    static void assertTransforms(
        JsonTransform transform,
        String json,
        String expected)
    {
        assertTransforms(transform, null, null, json, expected);
    }

    static void assertTransforms(
        JsonTransform transform,
        Consumer<LlmSectionAttributes> early,
        Consumer<LlmSectionAttributes> late,
        String json,
        String expected)
    {
        for (int window : WINDOWS)
        {
            for (int bound : BOUNDS)
            {
                for (boolean emptyLast : new boolean[] {false, true})
                {
                    final ByteArrayOutputStream output = new ByteArrayOutputStream();
                    final boolean accepted = run(transform, early, late, json, window, bound, emptyLast, output);

                    assertThat("accepted window " + window + " bound " + bound + " emptyLast " + emptyLast,
                        accepted, is(true));
                    assertThat("window " + window + " bound " + bound + " emptyLast " + emptyLast,
                        output.toString(UTF_8), equalTo(expected));
                }
            }
        }
    }

    static void assertRoundTrips(
        JsonTransform toCanonical,
        JsonTransform fromCanonical,
        String json)
    {
        assertRoundTrips(toCanonical, fromCanonical, null, json);
    }

    static void assertRoundTrips(
        JsonTransform toCanonical,
        JsonTransform fromCanonical,
        Consumer<LlmSectionAttributes> attributes,
        String json)
    {
        for (int window : WINDOWS)
        {
            for (int bound : BOUNDS)
            {
                final ByteArrayOutputStream canonical = new ByteArrayOutputStream();
                final boolean encoded = run(toCanonical, null, null, json, window, bound, false, canonical);

                assertThat("encoded window " + window + " bound " + bound, encoded, is(true));

                final ByteArrayOutputStream nativeOutput = new ByteArrayOutputStream();
                final boolean decoded = run(fromCanonical, attributes, null, canonical.toString(UTF_8), window, bound, false,
                    nativeOutput);

                assertThat("decoded window " + window + " bound " + bound, decoded, is(true));
                assertThat("window " + window + " bound " + bound, nativeOutput.toString(UTF_8), equalTo(json));
            }
        }
    }

    static void assertRejects(
        JsonTransform transform,
        String json)
    {
        assertRejects(transform, null, null, json);
    }

    static void assertRejects(
        JsonTransform transform,
        Consumer<LlmSectionAttributes> early,
        Consumer<LlmSectionAttributes> late,
        String json)
    {
        for (int window : WINDOWS)
        {
            final boolean accepted = run(transform, early, late, json, window, REJECT_BOUND, false, new ByteArrayOutputStream());

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
        Consumer<LlmSectionAttributes> early,
        Consumer<LlmSectionAttributes> late,
        String json,
        int window,
        int bound,
        boolean emptyLast,
        ByteArrayOutputStream output)
    {
        final LlmSectionPipeline pipeline = new LlmSectionPipeline(transform, new UnsafeBufferEx(new byte[bound]));
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);

        final LlmSectionAttributes attributes = new LlmSectionAttributes();

        if (early != null)
        {
            early.accept(attributes);
        }

        pipeline.begin(attributes);

        boolean accepted = true;

        for (int offset = 0; accepted && offset < bytes.length; offset += window)
        {
            if (offset > 0 && offset - window == 0 && late != null)
            {
                late.accept(attributes);
            }

            final int length = Math.min(window, bytes.length - offset);
            final boolean last = offset + length == bytes.length && !emptyLast;

            accepted = pipeline.feed(input, offset, length, last,
                (buffer, index, size, done) -> collect(output, buffer, index, size));
        }

        if (accepted && emptyLast)
        {
            accepted = pipeline.feed(input, bytes.length, 0, true,
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
