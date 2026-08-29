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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Streaming {@link JsonTransform} that re-roots a {@code tools/call} {@code params} object to its own
 * top-level {@code arguments} member -- suppressing the {@code name}/{@code arguments} wrapper entirely, so a
 * downstream schema validator sees the {@code arguments} value promoted to look exactly like a fresh
 * top-level document, matching the shape a tool's {@code inputSchema} is written against. Mirrors the
 * equivalent, already-reviewed re-rooting {@code McpHttpArguments} performs in {@code binding-mcp-http} for
 * the same reason ({@link io.aklivity.zilla.runtime.common.json.JsonTransforms#projector(java.util.List)}
 * keeps the wrapper key -- confirmed empirically against its own test suite -- so a dedicated re-rooting
 * stage is required rather than the generic projector); this stage adds the fragmented-key STARVED handling
 * {@code McpHttpArguments} does not need (no upstream schema validator there rejects on a mismatched key) and
 * the {@link #noArgumentsClosed} signal a caller uses to run a separate default-{@code "{}"} validation when
 * the {@code arguments} key never arrives at all.
 * <p>
 * {@code depth} tracks nesting of the outer {@code params} object/array; a key is compared against {@code
 * "arguments"} only at {@code depth == 1} (a direct child of {@code params}), matching {@code
 * McpBindingConfig}'s retired DOM-based {@code scanArguments}. Once armed, the very next event -- a
 * container open or a bare scalar -- begins forwarding: every event through the matching close (tracked by
 * {@code forwardDepth}, independent of {@code depth} so the suppressed wrapper's own nesting is never
 * conflated with the promoted value's) is forwarded verbatim to {@code sink} (the schema validator), whose
 * own {@link Status} is returned unchanged -- including {@link Status#COMPLETED} the instant the validator's
 * own root value (the {@code arguments} value) validates, and any {@link Status#REJECTED} raised as a thrown
 * {@code JsonValidationException} the pipeline itself converts to {@link Status#REJECTED}.
 * <p>
 * If the outer {@code params} value closes (or was never an object/array to begin with -- a bare top-level
 * scalar) without an {@code arguments} key ever having armed, {@link #noArgumentsClosed} is set and this
 * stage returns a synthetic {@link Status#COMPLETED} of its own accord (never having touched {@code sink} at
 * all) purely to unblock the driving {@link io.aklivity.zilla.runtime.common.json.JsonPipeline}, which
 * otherwise has no way to end a value that legitimately omits an optional {@code arguments} member. This
 * synthetic verdict is not itself a schema verdict -- the caller must check {@link #argsSeen} and, when
 * {@code false}, separately validate the default {@code "{}"} arguments a caller omitting the member is
 * entitled to (matching {@code McpBindingConfig}'s retired {@code scanArguments} default), rather than
 * treating this stage's own {@code COMPLETED} as proof of validity.
 * <p>
 * One instance is constructed fresh per stream and used for exactly one top-level {@code params} value (a
 * single {@code tools/call} request body) -- never {@link #reset()} and reused for a second value.
 */
final class McpToolCallArgsTransform implements JsonTransform
{
    private static final String ARGUMENTS_NAME = "arguments";

    private int depth;
    private boolean argumentsArmed;
    private boolean forwarding;
    private int forwardDepth;

    boolean argsSeen;
    boolean noArgumentsClosed;

    @Override
    public void reset()
    {
        depth = 0;
        argumentsArmed = false;
        forwarding = false;
        forwardDepth = 0;
        argsSeen = false;
        noArgumentsClosed = false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final Status status;
        switch (event)
        {
        case START_DOCUMENT:
        case END_DOCUMENT:
            status = Status.ADVANCED;
            break;
        case KEY_NAME:
            status = onKey(control, source, sink);
            break;
        case START_OBJECT:
        case START_ARRAY:
            status = onOpen(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            status = onClose(control, source, event, sink);
            break;
        default:
            status = onScalar(control, source, event, sink);
            break;
        }
        return status;
    }

    private Status onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        final Status status;
        if (forwarding)
        {
            status = sink.transform(control, source, JsonEvent.KEY_NAME);
        }
        else if (source.deferredBytes())
        {
            // a key straddling this window and the next cannot yet be compared against "arguments" --
            // decline the fragment so the source accumulates it whole and re-presents it complete later
            control.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            if (depth == 1)
            {
                argumentsArmed = ARGUMENTS_NAME.contentEquals(source.getStringView());
            }
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onOpen(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final Status status;
        if (forwarding)
        {
            forwardDepth++;
            status = sink.transform(control, source, event);
        }
        else if (argumentsArmed)
        {
            // this open belongs to the arguments value itself, not to the outer params object -- depth
            // must NOT count it: forwardDepth tracks its nesting instead, so depth stays exactly where
            // params left it and the arguments value's own later close (via the forwarding branch above)
            // never has to decrement a level it never incremented
            argumentsArmed = false;
            argsSeen = true;
            forwarding = true;
            forwardDepth = 1;
            status = sink.transform(control, source, event);
        }
        else
        {
            depth++;
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onClose(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final Status status;
        if (forwarding)
        {
            status = sink.transform(control, source, event);
            forwardDepth--;
            if (forwardDepth == 0)
            {
                forwarding = false;
            }
        }
        else
        {
            depth--;
            if (depth == 0 && !argsSeen)
            {
                // the outer params object/array closed (or this container was never params to begin with)
                // without an "arguments" key ever arriving -- nothing further could ever arm one
                noArgumentsClosed = true;
                status = Status.COMPLETED;
            }
            else
            {
                status = Status.ADVANCED;
            }
        }
        return status;
    }

    private Status onScalar(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final Status status;
        if (forwarding)
        {
            status = sink.transform(control, source, event);
            if (forwardDepth == 0 && !source.deferredBytes())
            {
                // the arguments value itself is a bare scalar (not a container): this fragment closes it
                forwarding = false;
            }
        }
        else if (argumentsArmed)
        {
            argumentsArmed = false;
            argsSeen = true;
            forwarding = true;
            status = sink.transform(control, source, event);
            if (!source.deferredBytes())
            {
                forwarding = false;
            }
        }
        else if (depth == 0)
        {
            // the whole params value is itself a bare scalar -- no "arguments" key was ever possible
            noArgumentsClosed = true;
            status = Status.COMPLETED;
        }
        else
        {
            status = Status.ADVANCED;
        }
        return status;
    }
}
