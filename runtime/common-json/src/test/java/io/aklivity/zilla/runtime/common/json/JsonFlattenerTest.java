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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonFlattenerTest
{
    @Test
    void shouldRenameTopLevelProperty()
    {
        assertEquals("{\"title\":\"x\"}",
            flatten(Map.of("subject", "title"), "{\"subject\":\"x\",\"other\":\"y\"}"));
    }

    @Test
    void shouldFlattenNestedLeafToTopLevel()
    {
        assertEquals("{\"title\":\"Add feature\"}",
            flatten(Map.of("pr.title", "title"), "{\"pr\":{\"title\":\"Add feature\"},\"other\":1}"));
    }

    @Test
    void shouldFlattenDeeplyNestedLeaf()
    {
        assertEquals("{\"x\":42}",
            flatten(Map.of("a.b.c", "x"), "{\"a\":{\"b\":{\"c\":42,\"d\":43}}}"));
    }

    @Test
    void shouldFlattenMultipleTargetsSharingAncestor()
    {
        assertEquals("{\"title\":\"t\",\"head\":\"h\"}",
            flatten(Map.of("pr.title", "title", "pr.head", "head"),
                "{\"pr\":{\"title\":\"t\",\"head\":\"h\",\"other\":\"z\"}}"));
    }

    @Test
    void shouldKeepWholeSubtreeAtFlattenedTarget()
    {
        assertEquals("{\"meta\":{\"a\":1,\"b\":[1,2]}}",
            flatten(Map.of("pr", "meta"), "{\"pr\":{\"a\":1,\"b\":[1,2]},\"z\":9}"));
    }

    @Test
    void shouldOmitTargetWhenAccessorMissing()
    {
        assertEquals("{}",
            flatten(Map.of("pr.title", "title"), "{\"other\":1}"));
    }

    @Test
    void shouldOmitTargetWhenAncestorIsScalarNotObject()
    {
        assertEquals("{}",
            flatten(Map.of("pr.title", "title"), "{\"pr\":5}"));
    }

    @Test
    void shouldFlattenMultipleUnrelatedTargets()
    {
        assertEquals("{\"title\":\"S\",\"head\":\"H\"}",
            flatten(Map.of("subject", "title", "pr.head", "head"),
                "{\"subject\":\"S\",\"pr\":{\"head\":\"H\",\"other\":\"x\"}}"));
    }

    @Test
    void shouldEmitEmptyObjectWhenNothingFlattened()
    {
        assertEquals("{}", flatten(Map.of("none", "x"), "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldNotDuplicateRenamedKeyAcrossOutputBoundSuspends()
    {
        // the renamed key ("epsilon", longer than the original "e") must not be re-emitted or truncated
        // when its write lands on an output-bound boundary and suspends
        assertEquals("{\"epsilon\":1} ",
            flattenBounded(Map.of("e", "epsilon"), "{\"e\":1}", 4));
    }

    @Test
    void shouldFlattenAccessorKeyThatFragmentsAcrossInputWindows()
    {
        // the accessor key is longer than the feed window and fragments across STARVED windows before this
        // stage can match it against the accessor trie; the match must still land once it completes. Driven
        // directly (no upstream projector) so the fragmenting key reaches this stage's own onKey() rather
        // than being reassembled and decided by the projector first.
        String key = "x".repeat(40);
        assertEquals("{\"y\":1}", flattenWindowed(Map.of(key, "y"), "{\"" + key + "\":1}", 8, -1));
    }

    @Test
    void shouldDropHugeNonMatchingKeyWithoutHittingValueSizeCap()
    {
        // root's only child is "y" (maxKeyLength 1): a 500-char unrelated key can never match, so onKey
        // decides "dead branch" as soon as the fragment exceeds 1 char and drains the rest without ever
        // buffering it -- completion here never depends on MAX_VALUE_SIZE, set far smaller than the dropped
        // key to prove it (see the matching JsonProjectorTest case for the shared, pre-existing constraint
        // this value must still clear: one window's worth of scanned-but-undecided tokenizer content).
        String hugeKey = "x".repeat(500);
        assertEquals("{\"z\":2}",
            flattenWindowed(Map.of("y", "z"), "{\"" + hugeKey + "\":1,\"y\":2}", 8, 50));
    }

    // Drives flatten() (without an upstream projector) through fixed-size input windows, carrying the
    // unconsumed tail (pipeline.remaining()) across STARVED feeds the way a real caller does. maxValueSize
    // configures the parser's MAX_VALUE_SIZE when non-negative, otherwise the parser default applies.
    private static String flattenWindowed(
        Map<String, String> accessorTargets,
        String input,
        int window,
        int maxValueSize)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonParserEx parser = maxValueSize < 0
            ? JsonEx.createParser()
            : JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, maxValueSize));
        JsonPipeline pipeline = JsonEx.stream(parser)
            .transform(JsonTransforms.flatten(accessorTargets))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        byte[] msg = (input + " ").getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (status == Status.STARVED && guard++ < 10_000)
        {
            limit = Math.min(limit + window, msg.length);
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static String flatten(
        Map<String, String> accessorTargets,
        String input)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(pointers(accessorTargets)))
            .transform(JsonTransforms.flatten(accessorTargets))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        feed(pipeline, input + " ");
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // Drives flatten() through an output buffer bounded at outBound, draining and re-targeting the
    // generator on each SUSPENDED and concatenating the drained chunks into one document.
    private static String flattenBounded(
        Map<String, String> accessorTargets,
        String input,
        int outBound)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[outBound]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(pointers(accessorTargets)))
            .transform(JsonTransforms.flatten(accessorTargets))
            .into(JsonEx.createSink(gen));

        byte[] bytes = (input + " ").getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        gen.wrap(buffer, 0, outBound);
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
            byte[] chunk = new byte[gen.length()];
            buffer.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                gen.wrap(buffer, 0, outBound);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 10_000);
        assertEquals(Status.COMPLETED, status);
        return result.toString();
    }

    private static List<String> pointers(
        Map<String, String> accessorTargets)
    {
        return accessorTargets.keySet().stream()
            .map(accessor -> "/" + accessor.replace(".", "/"))
            .toList();
    }

    private static Status feed(
        JsonPipeline pipeline,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        pipeline.reset();
        return pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
