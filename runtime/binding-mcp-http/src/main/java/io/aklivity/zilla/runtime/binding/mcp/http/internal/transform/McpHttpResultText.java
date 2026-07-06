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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Watches a {@code tools/call} response body as it streams past and mirrors its canonical JSON text into a
 * dedicated, bounded buffer, forwarding every event downstream unchanged ({@link #identity()} returns
 * {@code true}). Used only when a tool has no {@code tool.summary} template configured, so
 * {@code content[0].text} can still fall back to a human-readable rendering of {@code structuredContent} —
 * the pre-streaming behavior this transform restores — without buffering the whole response the way the
 * pre-streaming implementation did. Sits upstream of {@link McpHttpToolResult} in the same pipeline, so by
 * the time that stage resolves its summary supplier (only once the real content's root has closed), this
 * stage has already mirrored every event of the real document — no separate close-tracking is needed here.
 * <p>
 * Because the destination buffer is bounded and the fragment-safe {@link JsonGeneratorEx#write(CharSequence,
 * Completion)}/{@link JsonGeneratorEx#writeKey(CharSequence, Completion)} overloads are not proven
 * consumption-bounded the way {@link JsonGeneratorEx#writeSegment} is (see {@code common-json} issue #2017 —
 * the convenience API relies on an assertion compiled out in production), this class checks
 * {@link JsonGeneratorEx#remaining()} against a worst-case escape-expansion estimate before every mirrored
 * write and abandons capture for the rest of the document on the first write that would not safely fit,
 * rather than trusting the generator's own internal bound. {@link #text()} returns the empty string once
 * abandoned, matching today's behavior for the one case nothing tests either side of this change: a
 * pathologically large no-summary response.
 * <p>
 * This is a mediating, structure-inspecting transform sitting in front of a byte-preferring terminal sink
 * (see {@code common-json}'s verbatim-validate design notes, and {@link McpHttpResults}'s own javadoc for
 * the same rule applied to a sibling capture stage), so it cannot forward a downstream
 * {@link JsonController#segmentable()} request upstream unchanged: granting it would let the whole document
 * stream past as an opaque {@code SEGMENT} run, and the structured events this class mirrors would never be
 * delivered at all. {@code segmentable()} is always declined, and {@link JsonController#verbatim()} is
 * re-asserted upstream instead.
 */
public final class McpHttpResultText implements JsonTransform
{
    private static final int ESCAPE_EXPANSION = 6;
    private static final int SAFETY_MARGIN = 32;

    private final MutableDirectBufferEx buffer;
    private final JsonGeneratorEx generator;
    private final JsonController downstreamControl = new JsonController()
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void verbatim()
        {
            upstream.verbatim();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }
    };

    private JsonController upstream;
    private boolean abandoned;

    public McpHttpResultText(
        MutableDirectBufferEx buffer,
        JsonGeneratorEx generator)
    {
        this.buffer = buffer;
        this.generator = generator;
    }

    @Override
    public void reset()
    {
        abandoned = false;
        generator.wrap(buffer, 0, buffer.capacity());
        generator.reset();
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        if (!abandoned)
        {
            mirror(event, source);
        }
        return sink.transform(downstreamControl, source, event);
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        return sink.resume(downstreamControl, source, event);
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        upstream = control;
        return sink.flush(downstreamControl, source);
    }

    // The canonical JSON text mirrored so far, or the empty string once capacity-abandoned. Safe to call
    // only after the real document has finished streaming past this stage (see class javadoc).
    public String text()
    {
        return abandoned ? "" : buffer.getStringWithoutLengthUtf8(0, generator.length());
    }

    // Single overflow-check point for every mirrored event: estimate the worst-case output size first
    // (escape-expansion applies only to the variable-length string/number fragments), then either mirror
    // the event or abandon capture for the rest of the document — one guarded branch instead of one per
    // event kind, since every kind shares the same fallback (abandon, never a partial/corrupt write).
    private void mirror(
        JsonEvent event,
        JsonSource source)
    {
        if (generator.remaining() >= requiredCapacity(event, source))
        {
            write(event, source);
        }
        else
        {
            abandoned = true;
        }
    }

    private static int requiredCapacity(
        JsonEvent event,
        JsonSource source)
    {
        int fragmentLength;
        switch (event)
        {
        case KEY_NAME:
        case VALUE_STRING:
        case VALUE_NUMBER:
            fragmentLength = source.getStringView().length();
            break;
        default:
            fragmentLength = 0;
            break;
        }
        return fragmentLength * ESCAPE_EXPANSION + SAFETY_MARGIN;
    }

    private void write(
        JsonEvent event,
        JsonSource source)
    {
        switch (event)
        {
        case START_OBJECT:
            generator.writeStartObject();
            break;
        case START_ARRAY:
            generator.writeStartArray();
            break;
        case END_OBJECT:
        case END_ARRAY:
            generator.writeEnd();
            break;
        case KEY_NAME:
            generator.writeKey(source.getStringView());
            break;
        case VALUE_STRING:
            generator.write(source.getStringView(), source.deferredBytes() ? Completion.INCOMPLETE : Completion.COMPLETE);
            break;
        case VALUE_NUMBER:
            generator.writeNumber(source.getStringView(),
                source.deferredBytes() ? Completion.INCOMPLETE : Completion.COMPLETE);
            break;
        case VALUE_TRUE:
            generator.write(true);
            break;
        case VALUE_FALSE:
            generator.write(false);
            break;
        case VALUE_NULL:
            generator.writeNull();
            break;
        default:
            break;
        }
    }
}
