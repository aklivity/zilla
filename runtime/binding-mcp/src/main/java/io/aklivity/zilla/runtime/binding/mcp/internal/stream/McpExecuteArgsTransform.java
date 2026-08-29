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
 * Streaming {@link JsonTransform} that projects a {@code zilla__execute_tool} request payload down to just
 * the target tool's own {@code arguments} value, forwarding its events verbatim to the terminal sink while
 * dropping every other event of the outer envelope -- replaces {@code McpExecuteToolCallScanner}'s
 * whole-body buffering with a bounded, resumable pump driven one event at a time.
 * <p>
 * Depth/key-matching mirrors the scanner it replaces: {@code depth} tracks nesting, {@code argumentsDepth}
 * is the depth of the target's own wrapper object (the value of the envelope's own {@code arguments} key),
 * and {@code name}/the target's own {@code arguments} key are matched only at that depth. The target's own
 * {@code arguments} value is never buffered -- once its {@code START_OBJECT} is seen, every event beneath it
 * is forwarded to {@code sink} with the real, non-mediating {@link JsonController} passed straight through
 * (a non-mediating stage, so a downstream that does want byte-preserving delivery is free to negotiate it).
 * <p>
 * This stage deliberately does not itself request {@link JsonController#segmentable()} for the value: doing
 * so is only safe when the value is known to fit one input window (confirmed empirically -- see the R1 risk
 * this design carries) -- across multiple windows a segmented run's own resume accounting silently drops
 * content, a limitation in the segment/verbatim delivery path itself, not this stage. Ordinary structured
 * forwarding (one event at a time, resumed the standard way when it straddles a window) has no such limit at
 * any size, so the terminal generator canonically re-renders the target's own arguments value -- identical
 * data, insignificant whitespace not necessarily preserved -- which the padding math this stage's caller
 * layers on top of already treats as the expected case, not a fallback: an upper bound is never undershot by
 * canonical output (it is always no longer than the original span), so correctness never depends on which
 * rendering was used.
 * <p>
 * One instance is created per stream and never reused or reset -- a {@code zilla__execute_tool} request is
 * a single top-level value for the life of the stream. {@link #malformed} is set (and {@link Status#REJECTED}
 * returned) only when the target's own {@code arguments} value is present but is not an object; a JSON syntax
 * error is instead caught upstream by the pipeline itself.
 * <p>
 * {@code listener} is invoked once after every event this transform processes, letting the owning stream
 * react as soon as enough is known to dispatch (the target name plus either its own {@code arguments} key or
 * proof no such key is coming) or to finalize the reconstructed delegate body (the target's own {@code
 * arguments} value has structurally closed) -- without ever waiting for the whole envelope to finish parsing.
 */
final class McpExecuteArgsTransform implements JsonTransform
{
    private static final String ARGUMENTS_NAME = "arguments";
    private static final String NAME_NAME = "name";

    @FunctionalInterface
    interface ProgressListener
    {
        void onProgress();
    }

    private final ProgressListener listener;

    private int depth;
    private int argumentsDepth = -1;
    private boolean argumentsArmed;
    private boolean nameArmed;
    private boolean targetArgsArmed;
    private boolean forwarding;
    private int forwardDepth;

    String name;
    boolean malformed;
    boolean done;
    boolean argsSeen;
    boolean argsClosed;
    boolean wrapperClosed;
    long argsValueStreamOffset = -1;

    McpExecuteArgsTransform(
        ProgressListener listener)
    {
        this.listener = listener;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final Status downstream;
        switch (event)
        {
        case START_DOCUMENT:
            downstream = Status.ADVANCED;
            break;
        case END_DOCUMENT:
            done = true;
            downstream = Status.ADVANCED;
            break;
        case KEY_NAME:
            downstream = onKey(control, source, sink);
            break;
        case START_OBJECT:
        case START_ARRAY:
            downstream = onOpen(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            downstream = onClose(control, source, event, sink);
            break;
        default:
            downstream = onScalar(control, source, event, sink);
            break;
        }

        final Status status = resolveStatus(downstream);
        listener.onProgress();
        return status;
    }

    private Status resolveStatus(
        Status downstream)
    {
        final Status status;
        if (malformed || downstream == Status.REJECTED)
        {
            status = Status.REJECTED;
        }
        else if (downstream == Status.SUSPENDED)
        {
            status = Status.SUSPENDED;
        }
        else if (downstream == Status.STARVED)
        {
            status = Status.STARVED;
        }
        else if (done)
        {
            status = Status.COMPLETED;
        }
        else
        {
            status = Status.ADVANCED;
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
            control.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            final CharSequence key = source.getStringView();
            if (depth == 1)
            {
                argumentsArmed = ARGUMENTS_NAME.contentEquals(key);
            }
            else if (depth == argumentsDepth)
            {
                nameArmed = NAME_NAME.contentEquals(key);
                targetArgsArmed = ARGUMENTS_NAME.contentEquals(key);
                if (targetArgsArmed)
                {
                    argsSeen = true;
                    argsValueStreamOffset = source.getLocation().getStreamOffset();
                }
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
        else if (targetArgsArmed)
        {
            // this open belongs to the target's own arguments value, not to the outer envelope --
            // depth must NOT count it: everything from here to its matching close is tracked via
            // forwardDepth instead (set immediately below), so depth stays exactly where the envelope
            // left it and its own later close (via the forwarding branch above) never has to
            // decrement a level it never incremented
            targetArgsArmed = false;
            if (event == JsonEvent.START_OBJECT)
            {
                forwarding = true;
                forwardDepth = 1;
                status = sink.transform(control, source, event);
            }
            else
            {
                malformed = true;
                done = true;
                status = Status.REJECTED;
            }
        }
        else
        {
            depth++;
            if (argumentsArmed)
            {
                argumentsArmed = false;
                argumentsDepth = depth;
            }
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
                argsClosed = true;
            }
        }
        else
        {
            if (depth == argumentsDepth)
            {
                argumentsDepth = -1;
                nameArmed = false;
                targetArgsArmed = false;
                wrapperClosed = true;
            }
            depth--;
            if (depth == 0)
            {
                done = true;
            }
            status = Status.ADVANCED;
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
        }
        else if (event == JsonEvent.VALUE_STRING && nameArmed)
        {
            if (source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                name = source.getString();
                nameArmed = false;
                status = Status.ADVANCED;
            }
        }
        else if (targetArgsArmed)
        {
            malformed = true;
            done = true;
            status = Status.REJECTED;
        }
        else
        {
            status = Status.ADVANCED;
        }
        return status;
    }
}
