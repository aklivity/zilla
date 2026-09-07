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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Watches a {@code tools/call} response body as it streams past and captures the value at each of a fixed set
 * of dotted {@code result.<path>} references (e.g. {@code number}, {@code data.id}) — the paths a configured
 * {@code tool.summary} template interpolates — into {@code captured}, while forwarding every event downstream
 * unchanged ({@link #identity()} returns {@code true}). This lets a summary be resolved once the response has
 * fully streamed past without re-scanning a buffered copy, the response-side counterpart to
 * {@link McpHttpArguments}'s request-side capture.
 * <p>
 * Each configured path tracks its own match progress independently against the shared document depth, mirroring
 * the depth/matched-segment algorithm a one-shot buffered lookup would use (match segment {@code n} of a path
 * only at a {@code KEY_NAME} seen at depth {@code n + 1}), extended to watch several paths — of possibly
 * different lengths and depths — over one incremental pass instead of one re-scan per path.
 * <p>
 * A path whose value turns out to be a scalar ({@code VALUE_STRING}, {@code VALUE_NUMBER}, {@code VALUE_TRUE},
 * {@code VALUE_FALSE}, {@code VALUE_NULL}) captures that scalar's own text verbatim (a string capturing its
 * content unquoted, a number its digits, {@code true}/{@code false}/{@code null} their literal spelling) —
 * exactly what a template author who wrote {@code Created pull request #${result.number}} expects to see
 * substituted in place of {@code ${result.number}}. A path whose value turns out to be an object or array
 * instead re-serializes that whole subtree into {@code text} as compact JSON (see {@link #captureContainer}),
 * so a template can still say something meaningful about a response shaped as a list or nested record even
 * though there is no single scalar to point at. A value spanning more than one input window accumulates across
 * every fragment into {@code text} and only commits once {@link JsonSource#deferredBytes()} reports the value
 * complete (for a container capture, once the container's own matching close event is reached); since only one
 * value can ever be captured "in flight" at a time (JSON parsing is strictly sequential), a single reused
 * accumulator is sufficient. While one path's capture is in flight ({@code awaiting != -1}), {@link #onKeyName}
 * is skipped entirely — a nested key inside the very subtree being serialized must never be allowed to arm a
 * second, different path and corrupt the first one's still-open accumulation.
 * <p>
 * The empty path (zero segments) denotes a bare {@code result} reference — the response body's own root
 * value, with no key to match a {@code KEY_NAME} against — so it is armed as {@code awaiting} up front by
 * {@link #reset()} instead of via {@link #onKeyName}. {@link #transform} excludes the pipeline's own
 * {@code START_DOCUMENT}/{@code END_DOCUMENT} framing events from reaching {@link #capture} so that framing,
 * not the real root value, is what the root reference would otherwise capture first; the first real event
 * after that is the document's own root, whatever shape it turns out to be.
 * <p>
 * This is a mediating, structure-inspecting transform sitting in front of a byte-preferring terminal sink
 * (see {@code common-json}'s verbatim-validate design notes), so it cannot forward a downstream
 * {@link JsonController#segmentable()} request upstream unchanged the way a purely forwarding stage would:
 * granting it would let the whole document stream past as an opaque {@code SEGMENT} run, and the {@code
 * KEY_NAME} events this class matches paths against would never be delivered at all. While any path remains
 * configured, {@code segmentable()} is declined so its own upstream keeps delivering structured events, and
 * {@link JsonController#verbatim()} is re-asserted upstream instead — the byte-preserving fidelity modifier
 * that rides alongside structured events rather than substituting for them. When no paths are configured
 * (nothing to watch), both requests pass through unchanged, so a tool with no {@code tool.summary} references
 * keeps the full byte-passthrough optimization.
 */
public final class McpHttpResults implements JsonTransform
{
    private static final String[] EMPTY_SEGMENTS = new String[0];
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final Map<String, String> captured;
    private final String[] paths;
    private final String[][] segments;
    private final int[] matched;
    private final StringBuilder text = new StringBuilder();
    private final JsonController downstreamControl = new JsonController()
    {
        @Override
        public void segmentable()
        {
            if (paths.length == 0)
            {
                upstream.segmentable();
            }
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
    private int depth;
    private int awaiting = -1;
    private int captureDepth;
    private boolean nestedValueOpen;

    public McpHttpResults(
        Map<String, String> captured,
        List<String> paths)
    {
        this.captured = captured;
        this.paths = paths.toArray(String[]::new);
        this.segments = new String[this.paths.length][];
        for (int i = 0; i < this.paths.length; i++)
        {
            this.segments[i] = this.paths[i].isEmpty() ? EMPTY_SEGMENTS : this.paths[i].split("\\.");
        }
        this.matched = new int[this.paths.length];
    }

    @Override
    public void reset()
    {
        depth = 0;
        awaiting = -1;
        captureDepth = 0;
        nestedValueOpen = false;
        text.setLength(0);
        for (int i = 0; i < matched.length; i++)
        {
            matched[i] = 0;
            if (segments[i].length == 0)
            {
                awaiting = i;
            }
        }
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
        if (awaiting != -1 && event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT)
        {
            capture(awaiting, event, source);
        }

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            // a nested key inside a subtree already being serialized for some other in-flight capture
            // must never be allowed to arm a second, different path -- see the class Javadoc
            if (awaiting == -1)
            {
                onKeyName(source);
            }
            break;
        default:
            break;
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

    private void onKeyName(
        JsonSource source)
    {
        for (int i = 0; i < segments.length; i++)
        {
            if (matched[i] < segments[i].length &&
                depth == matched[i] + 1 &&
                segments[i][matched[i]].contentEquals(source.getStringView()))
            {
                matched[i]++;
                if (matched[i] == segments[i].length)
                {
                    awaiting = i;
                }
            }
        }
    }

    // Appends this fragment to the in-flight value's accumulator, committing to `captured` (and clearing
    // `awaiting`, so the next KEY_NAME can arm a fresh capture) only once deferredBytes() reports no more
    // fragments follow. A VERBATIM event rides alongside the structured event stream for the same value
    // rather than substituting for it (see the class Javadoc), so it is ignored here rather than treated as
    // an unexpected event. Once already inside a container capture (captureDepth > 0), every event -- keys,
    // nested values, nested containers -- routes to captureContainer() instead; a START_OBJECT/START_ARRAY
    // seen here for the first time is what puts it there.
    private void capture(
        int index,
        JsonEvent event,
        JsonSource source)
    {
        if (captureDepth > 0 || event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
        {
            captureContainer(index, event, source);
        }
        else
        {
            switch (event)
            {
            case VALUE_STRING:
            case VALUE_NUMBER:
                text.append(source.getStringView());
                if (!source.deferredBytes())
                {
                    captured.put(paths[index], text.toString());
                    text.setLength(0);
                    awaiting = -1;
                }
                break;
            case VALUE_TRUE:
                captured.put(paths[index], "true");
                awaiting = -1;
                break;
            case VALUE_FALSE:
                captured.put(paths[index], "false");
                awaiting = -1;
                break;
            case VALUE_NULL:
                captured.put(paths[index], "null");
                awaiting = -1;
                break;
            case VERBATIM:
                break;
            default:
                awaiting = -1;
                break;
            }
        }
    }

    // Re-serializes an object/array value into `text` as compact JSON while it streams past, one event at a
    // time, tracking captureDepth (the nesting depth *within this captured subtree*, independent of the
    // document-wide `depth` field) to know when the value's own matching close event is reached. A comma is
    // owed before any new key or value except the first child of a container or the value right after a key
    // -- appendSeparator() decides this from the last character already written rather than a separate
    // "first child" flag per nesting level, since '{', '[' and ':' are exactly the characters nothing else
    // in a compact JSON document ever ends a token with. A string or number value spanning more than one
    // input window is tracked via nestedValueOpen the same way the top-level scalar case tracks it via
    // deferredBytes() directly -- the leading separator/quote is written only for the first fragment, the
    // closing quote only for the last.
    private void captureContainer(
        int index,
        JsonEvent event,
        JsonSource source)
    {
        switch (event)
        {
        case START_OBJECT:
            appendSeparator();
            text.append('{');
            captureDepth++;
            break;
        case START_ARRAY:
            appendSeparator();
            text.append('[');
            captureDepth++;
            break;
        case END_OBJECT:
            text.append('}');
            closeContainer(index);
            break;
        case END_ARRAY:
            text.append(']');
            closeContainer(index);
            break;
        case KEY_NAME:
            appendSeparator();
            text.append('"');
            appendEscaped(source.getStringView());
            text.append('"').append(':');
            break;
        case VALUE_STRING:
            if (!nestedValueOpen)
            {
                appendSeparator();
                text.append('"');
                nestedValueOpen = true;
            }
            appendEscaped(source.getStringView());
            if (!source.deferredBytes())
            {
                text.append('"');
                nestedValueOpen = false;
            }
            break;
        case VALUE_NUMBER:
            if (!nestedValueOpen)
            {
                appendSeparator();
                nestedValueOpen = true;
            }
            text.append(source.getStringView());
            if (!source.deferredBytes())
            {
                nestedValueOpen = false;
            }
            break;
        case VALUE_TRUE:
            appendSeparator();
            text.append("true");
            break;
        case VALUE_FALSE:
            appendSeparator();
            text.append("false");
            break;
        case VALUE_NULL:
            appendSeparator();
            text.append("null");
            break;
        case VERBATIM:
            break;
        default:
            break;
        }
    }

    // A comma is owed before a new key or value unless it would be the first child of the container just
    // opened (last character '{' or '[') or the value immediately following a key (last character ':') --
    // none of which a completed value (a closing quote, digit, e/l, or nested '}'/']') ever ends with, so
    // this single check replaces a separate "first child of this nesting level" flag per depth.
    private void appendSeparator()
    {
        if (text.length() > 0)
        {
            final char last = text.charAt(text.length() - 1);
            if (last != '{' && last != '[' && last != ':')
            {
                text.append(',');
            }
        }
    }

    // captureDepth reaching zero means the close event just appended matched the container capture's own
    // opening event, so the fully reconstructed subtree in `text` is ready to commit.
    private void closeContainer(
        int index)
    {
        captureDepth--;
        if (captureDepth == 0)
        {
            captured.put(paths[index], text.toString());
            text.setLength(0);
            awaiting = -1;
        }
    }

    private void appendEscaped(
        CharSequence value)
    {
        for (int i = 0; i < value.length(); i++)
        {
            final char c = value.charAt(i);
            switch (c)
            {
            case '"':
                text.append("\\\"");
                break;
            case '\\':
                text.append("\\\\");
                break;
            case '\n':
                text.append("\\n");
                break;
            case '\r':
                text.append("\\r");
                break;
            case '\t':
                text.append("\\t");
                break;
            case '\b':
                text.append("\\b");
                break;
            case '\f':
                text.append("\\f");
                break;
            default:
                if (c < 0x20)
                {
                    text.append("\\u00")
                        .append(HEX_DIGITS[(c >> 4) & 0xf])
                        .append(HEX_DIGITS[c & 0xf]);
                }
                else
                {
                    text.append(c);
                }
                break;
            }
        }
    }
}
