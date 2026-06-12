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

    private final JsonController downstreamControl = this::onDownstreamSegmentable;

    private int depth;
    private int containers;
    private Decision keyDecision;
    private String pendingKey;
    private boolean rootDone;
    private boolean downstreamDemand;
    private Status downstream;
    private SegMode segMode = SegMode.NONE;
    private JsonEvent deferredStart;

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
    }

    private void onDownstreamSegmentable()
    {
        downstreamDemand = true;
    }

    @Override
    public Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        downstream = Status.RESUMABLE;
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
            status = Status.COMPLETE;
        }
        else
        {
            status = Status.RESUMABLE;
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
        case COMPLETE -> 1;
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
            onKey(source);
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
        JsonSource source)
    {
        StringBuilder key = segmentKeys[depth];
        key.setLength(0);
        key.append(source.getKey());
        segmentIsKey[depth] = true;
        indexes[depth] = -1;
        depth++;
        boolean parentKeepAll = containers > 0 && frameKeepAll[containers - 1];
        Decision d = parentKeepAll ? Decision.KEEP_ALL : decide();
        keyDecision = d;
        // Only a key whose value will be emitted is forwarded, so only then is a String materialized;
        // SKIP keys (the majority) are matched by chars above and never allocate.
        pendingKey = d == Decision.SKIP ? null : source.getString();
    }

    private void forwardPendingKey(
        JsonSink sink)
    {
        if (pendingKey != null)
        {
            forward(sink, keySource.with(pendingKey), JsonEvent.KEY_NAME);
            pendingKey = null;
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
        if (emit && d == Decision.KEEP_ALL && downstreamDemand)
        {
            forwardPendingKey(sink);
            control.segmentable();
            segMode = SegMode.AWAITING;
            deferredStart = event;
        }
        else if (emit)
        {
            forwardPendingKey(sink);
            forward(sink, source, event);
            pushFrame(event, true, d);
        }
        else
        {
            pendingKey = null;
            pushFrame(event, false, d);
        }
    }

    private void onAwaiting(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (event == JsonEvent.START_SEGMENT)
        {
            segMode = SegMode.FORWARDING;
            deferredStart = null;
            forward(sink, source, event);
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
        if (event == JsonEvent.END_SEGMENT)
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
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d == Decision.KEEP_ALL;
        if (emit)
        {
            forwardPendingKey(sink);
            forward(sink, source, event);
        }
        else
        {
            pendingKey = null;
        }
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
        private String key;

        private KeySource with(
            String key)
        {
            this.key = key;
            return this;
        }

        @Override
        public String getString()
        {
            return key;
        }

        @Override
        public CharSequence getKey()
        {
            return key;
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
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }
    }
}
