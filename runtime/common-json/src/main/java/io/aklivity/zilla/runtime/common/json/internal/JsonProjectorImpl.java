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
package io.aklivity.zilla.runtime.common.json.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonLocation;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Resumable, event-driven {@link JsonTransform} that projects a document down to a set of retained
 * RFC 6901 pointers, forwarding the kept events to the downstream {@code sink} passed into each
 * {@link #feed(JsonController, JsonSource, JsonEvent, JsonSink)}. This class holds the per-value descent
 * state only; the downstream is bound once at assembly and supplied per event.
 */
public final class JsonProjectorImpl implements JsonTransform
{
    private static final int MAX_DEPTH = 64;
    private static final String WILDCARD = "-";

    private enum Decision
    {
        KEEP_ALL, DESCEND, SKIP
    }

    private final List<String[]> retained;

    // Per-depth reused key buffers — an object-key segment is captured here char-by-char (no String);
    // segmentIsKey distinguishes an object key from an array index at this depth.
    private final StringBuilder[] segmentKeys = new StringBuilder[MAX_DEPTH];
    private final boolean[] segmentIsKey = new boolean[MAX_DEPTH];
    private final int[] indexes = new int[MAX_DEPTH];

    private final boolean[] frameInArray = new boolean[MAX_DEPTH];
    private final boolean[] frameEmit = new boolean[MAX_DEPTH];
    private final boolean[] frameKeepAll = new boolean[MAX_DEPTH];
    private final int[] frameNextIndex = new int[MAX_DEPTH];
    private final int[] frameDepthAtOpen = new int[MAX_DEPTH];

    private final KeySource keySource = new KeySource();

    private final DownstreamControl downstreamControl = new DownstreamControl();

    private JsonController upstreamControl;
    private int depth;
    private int containers;
    private Decision keyDecision;
    private CharSequence pendingKey;
    private boolean rootDone;
    private boolean downstreamDemand;
    private Status downstream;
    private SegMode segMode = SegMode.NONE;
    private JsonEvent deferredStart;
    private boolean scalarPending;
    private boolean scalarEmit;
    // true while a buffered key is being forwarded downstream: the parser delivered that key live and already
    // moved on, so the sink's consumed() pushback advances the KeySource cursor rather than the parser's
    // now-current (value) char cursor
    private boolean forwardingKey;
    // a buffered key forward filled the bounded output mid-key: resume must drain its remainder before the
    // held value (the event that triggered the forward) is processed
    private boolean keyDraining;
    private JsonEvent heldEvent;
    private boolean heldStart;
    private Decision heldDecision;

    private enum SegMode
    {
        NONE, AWAITING, FORWARDING
    }

    public JsonProjectorImpl(
        List<String> pointers)
    {
        this.retained = compileAll(pointers);
        for (int i = 0; i < MAX_DEPTH; i++)
        {
            segmentKeys[i] = new StringBuilder();
        }
    }

    @Override
    public void reset()
    {
        depth = 0;
        containers = 0;
        keyDecision = null;
        pendingKey = null;
        rootDone = false;
        downstreamDemand = false;
        segMode = SegMode.NONE;
        deferredStart = null;
        scalarPending = false;
        scalarEmit = false;
        forwardingKey = false;
        keyDraining = false;
        heldEvent = null;
        heldStart = false;
        heldDecision = null;
    }

    private void onDownstreamSegmentable()
    {
        downstreamDemand = true;
    }

    // Relays the sink's consumed() pushback to the projector's own upstream, the same way it relays
    // segmentable(); the upstream control is captured per feed/resume.
    private final class DownstreamControl implements JsonController
    {
        @Override
        public void segmentable()
        {
            onDownstreamSegmentable();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            // a buffered key's pushback advances the KeySource's own cursor so its remainder is re-presented on
            // resume (the parser already delivered that key live and moved on); a value's pushback relays to
            // the parser so it re-exposes the value remainder on resume
            if (forwardingKey)
            {
                keySource.consume(sourceBytes);
            }
            else
            {
                upstreamControl.consumed(sourceBytes);
            }
        }
    }

    @Override
    public Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        downstream = Status.ADVANCED;
        if (segMode == SegMode.AWAITING)
        {
            onAwaiting(control, source, event, sink);
        }
        else if (segMode == SegMode.FORWARDING)
        {
            onForwarding(source, event, sink);
        }
        else
        {
            route(control, source, event, sink);
        }
        return mapStatus();
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        downstream = Status.ADVANCED;
        if (keyDraining)
        {
            // continue draining the buffered key; once it fully drains, process the value that was held back
            forwardPendingKey(sink);
            if (pendingKey == null)
            {
                keyDraining = false;
                JsonEvent held = heldEvent;
                heldEvent = null;
                if (heldStart)
                {
                    startEmit(control, source, held, sink, heldDecision);
                }
                else
                {
                    forwardScalarValue(source, held, sink);
                }
            }
        }
        else
        {
            Status status = sink.resume(control, source, event);
            if (rank(status) > rank(downstream))
            {
                downstream = status;
            }
        }
        return mapStatus();
    }

    private Status mapStatus()
    {
        Status status;
        if (downstream == Status.REJECTED)
        {
            status = Status.REJECTED;
        }
        else if (downstream == Status.SUSPENDED)
        {
            status = Status.SUSPENDED;
        }
        else if (rootDone)
        {
            status = Status.COMPLETED;
        }
        else
        {
            status = Status.ADVANCED;
        }
        return status;
    }

    // Forwards one event downstream, retaining the most terminal status seen across the (possibly
    // several) downstream feeds a single upstream event triggers, so backpressure (SUSPENDED) and
    // rejection (REJECTED) propagate while the value is still in progress.
    private void forward(
        JsonSink sink,
        JsonSource source,
        JsonEvent event)
    {
        Status status = sink.feed(downstreamControl, source, event);
        if (rank(status) > rank(downstream))
        {
            downstream = status;
        }
    }

    private static int rank(
        Status status)
    {
        return switch (status)
        {
        case REJECTED -> 3;
        case SUSPENDED -> 2;
        case COMPLETED -> 1;
        default -> 0;
        };
    }

    private void route(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_DOCUMENT:
        case END_DOCUMENT:
            forward(sink, source, event);
            break;
        case KEY_NAME:
            onKey(control, source);
            break;
        case START_OBJECT:
        case START_ARRAY:
            onStart(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onEnd(source, event, sink);
            break;
        default:
            onScalar(source, event, sink);
            break;
        }
    }

    private void onKey(
        JsonController control,
        JsonSource source)
    {
        StringBuilder key = segmentKeys[depth];
        key.setLength(0);
        key.append(source.getStringView());
        segmentIsKey[depth] = true;
        indexes[depth] = -1;
        depth++;
        boolean parentKeepAll = containers > 0 && frameKeepAll[containers - 1];
        Decision d = parentKeepAll ? Decision.KEEP_ALL : decide();
        keyDecision = d;
        // A forwarded key reuses the char view already buffered above for path matching, so no key
        // ever materializes a String — neither the SKIP majority nor the KEEP keys carried downstream.
        pendingKey = d == Decision.SKIP ? null : key;
        if (pendingKey != null)
        {
            keySource.with(key);
        }
        // arm the kept value for verbatim segment delivery; best-effort, demand-gated
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        if (parentEmit && d == Decision.KEEP_ALL && downstreamDemand)
        {
            control.segmentable();
        }
    }

    private void forwardPendingKey(
        JsonSink sink)
    {
        if (pendingKey != null)
        {
            forwardingKey = true;
            forward(sink, keySource, JsonEvent.KEY_NAME);
            forwardingKey = false;
            // a bounded sink may have written only part of even a whole buffered key; clear pendingKey only
            // once the key has fully drained, so resume re-presents the remainder
            if (keySource.drained())
            {
                pendingKey = null;
            }
        }
    }

    private void pushFrame(
        JsonEvent event,
        boolean emit,
        Decision d)
    {
        frameInArray[containers] = event == JsonEvent.START_ARRAY;
        frameEmit[containers] = emit;
        frameKeepAll[containers] = d == Decision.KEEP_ALL;
        frameNextIndex[containers] = 0;
        frameDepthAtOpen[containers] = depth;
        containers++;
    }

    private void onStart(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d != Decision.SKIP;
        if (emit)
        {
            forwardPendingKey(sink);
            if (pendingKey != null)
            {
                // the buffered key filled the bounded output; hold this start until the key drains on resume
                keyDraining = true;
                heldEvent = event;
                heldStart = true;
                heldDecision = d;
            }
            else
            {
                startEmit(control, source, event, sink, d);
            }
        }
        else
        {
            pendingKey = null;
            pushFrame(event, false, d);
        }
    }

    private void startEmit(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink,
        Decision d)
    {
        if (d == Decision.KEEP_ALL && downstreamDemand)
        {
            control.segmentable();
            segMode = SegMode.AWAITING;
            deferredStart = event;
        }
        else
        {
            forward(sink, source, event);
            pushFrame(event, true, d);
        }
    }

    private void onAwaiting(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (event == JsonEvent.SEGMENT)
        {
            segMode = SegMode.FORWARDING;
            deferredStart = null;
            forward(sink, source, event);
            if (!source.deferredBytes())
            {
                finishSegment();
            }
        }
        else
        {
            segMode = SegMode.NONE;
            forward(sink, source, deferredStart);
            pushFrame(deferredStart, true, Decision.KEEP_ALL);
            deferredStart = null;
            route(control, source, event, sink);
        }
    }

    private void onForwarding(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        forward(sink, source, event);
        if (!source.deferredBytes())
        {
            finishSegment();
        }
    }

    private void finishSegment()
    {
        segMode = SegMode.NONE;
        if (containers == 0)
        {
            rootDone = true;
        }
        else
        {
            depth--;
        }
    }

    private void onEnd(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        containers--;
        if (frameEmit[containers])
        {
            forward(sink, source, event);
        }
        if (containers == 0)
        {
            depth = 0;
            rootDone = true;
        }
        else
        {
            depth = frameDepthAtOpen[containers] - 1;
        }
    }

    private void onScalar(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (scalarPending)
        {
            // a continuation fragment of a kept/dropped scalar value split across input windows: the value
            // was already entered on its first fragment, so forward (if kept) without re-entering and only
            // account for the consumed value once its closing fragment arrives (deferredBytes false)
            if (scalarEmit)
            {
                forward(sink, source, event);
            }
            if (!source.deferredBytes())
            {
                finishScalar();
            }
        }
        else
        {
            Decision d = enterValue();
            boolean parentEmit = containers == 0 || frameEmit[containers - 1];
            scalarEmit = parentEmit && d == Decision.KEEP_ALL;
            if (scalarEmit)
            {
                forwardPendingKey(sink);
                if (pendingKey != null)
                {
                    // the buffered key filled the bounded output; hold this scalar until the key drains
                    keyDraining = true;
                    heldEvent = event;
                    heldStart = false;
                }
                else
                {
                    forwardScalarValue(source, event, sink);
                }
            }
            else
            {
                pendingKey = null;
                if (source.deferredBytes())
                {
                    scalarPending = true;
                }
                else
                {
                    finishScalar();
                }
            }
        }
    }

    private void forwardScalarValue(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        forward(sink, source, event);
        if (source.deferredBytes())
        {
            scalarPending = true;
        }
        else
        {
            finishScalar();
        }
    }

    private void finishScalar()
    {
        scalarPending = false;
        if (containers == 0)
        {
            rootDone = true;
        }
        else
        {
            depth--;
        }
    }

    private Decision enterValue()
    {
        Decision result;
        if (containers == 0)
        {
            result = decide();
        }
        else
        {
            int parent = containers - 1;
            if (frameInArray[parent])
            {
                segmentIsKey[depth] = false;
                indexes[depth] = frameNextIndex[parent];
                frameNextIndex[parent] = frameNextIndex[parent] + 1;
                depth++;
                result = frameKeepAll[parent] ? Decision.KEEP_ALL : decide();
            }
            else
            {
                result = keyDecision;
                keyDecision = null;
            }
        }
        return result;
    }

    private Decision decide()
    {
        boolean keepAll = false;
        boolean descend = false;
        for (String[] pointer : retained)
        {
            if (pointer.length <= depth)
            {
                if (matchesPrefix(pointer, pointer.length))
                {
                    keepAll = true;
                    break;
                }
            }
            else if (matchesPrefix(pointer, depth))
            {
                descend = true;
            }
        }
        return keepAll ? Decision.KEEP_ALL : descend ? Decision.DESCEND : Decision.SKIP;
    }

    private boolean matchesPrefix(
        String[] pointer,
        int length)
    {
        boolean matches = true;
        for (int i = 0; i < length; i++)
        {
            if (!segmentMatches(pointer[i], i))
            {
                matches = false;
                break;
            }
        }
        return matches;
    }

    private boolean segmentMatches(
        String pointerSegment,
        int pathIndex)
    {
        return segmentIsKey[pathIndex]
            ? charsEqual(pointerSegment, segmentKeys[pathIndex])
            : WILDCARD.equals(pointerSegment) || matchesIndex(pointerSegment, indexes[pathIndex]);
    }

    private static boolean charsEqual(
        String pointerSegment,
        CharSequence key)
    {
        boolean matches = pointerSegment.length() == key.length();
        for (int i = 0; matches && i < pointerSegment.length(); i++)
        {
            matches = pointerSegment.charAt(i) == key.charAt(i);
        }
        return matches;
    }

    private static boolean matchesIndex(
        String segment,
        int index)
    {
        boolean matches = !segment.isEmpty() && (segment.length() == 1 || segment.charAt(0) != '0');
        int value = 0;
        for (int i = 0; matches && i < segment.length(); i++)
        {
            char c = segment.charAt(i);
            matches = Character.isDigit(c);
            if (matches)
            {
                int digit = c - '0';
                matches = value <= (Integer.MAX_VALUE - digit) / 10;
                if (matches)
                {
                    value = value * 10 + digit;
                }
            }
        }
        return matches && value == index;
    }

    private static List<String[]> compileAll(
        List<String> pointers)
    {
        List<String[]> compiled = new ArrayList<>();
        for (String pointer : pointers)
        {
            compiled.add(compile(pointer));
        }
        return compiled;
    }

    private static String[] compile(
        String pointer)
    {
        String[] result;
        if (pointer.isEmpty())
        {
            result = new String[0];
        }
        else
        {
            String[] parts = pointer.substring(1).split("/", -1);
            for (int i = 0; i < parts.length; i++)
            {
                parts[i] = parts[i].replace("~1", "/").replace("~0", "~");
            }
            result = parts;
        }
        return result;
    }

    private static final class KeySource implements JsonSource
    {
        private final View view = new View();

        private CharSequence key;
        private int offset;

        private KeySource with(
            CharSequence key)
        {
            this.key = key;
            this.offset = 0;
            return this;
        }

        // advance past the chars the sink wrote, so getStringView re-exposes the remainder on resume
        private void consume(
            int chars)
        {
            offset += chars;
        }

        private boolean drained()
        {
            return key == null || offset >= key.length();
        }

        @Override
        public String getString()
        {
            return key == null ? null : view.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return view;
        }

        // a non-allocating view of the unconsumed remainder of the buffered key
        private final class View implements CharSequence
        {
            @Override
            public int length()
            {
                return key.length() - offset;
            }

            @Override
            public char charAt(
                int index)
            {
                return key.charAt(offset + index);
            }

            @Override
            public CharSequence subSequence(
                int start,
                int end)
            {
                return key.subSequence(offset + start, offset + end);
            }

            @Override
            public String toString()
            {
                return key.subSequence(offset, key.length()).toString();
            }
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }
}
