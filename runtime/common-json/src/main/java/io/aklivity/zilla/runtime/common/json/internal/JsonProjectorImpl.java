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

    private final String[] segments = new String[MAX_DEPTH];
    private final int[] indexes = new int[MAX_DEPTH];

    private final boolean[] frameInArray = new boolean[MAX_DEPTH];
    private final boolean[] frameEmit = new boolean[MAX_DEPTH];
    private final boolean[] frameKeepAll = new boolean[MAX_DEPTH];
    private final int[] frameNextIndex = new int[MAX_DEPTH];
    private final int[] frameDepthAtOpen = new int[MAX_DEPTH];

    private final KeySource keySource = new KeySource();

    private int depth;
    private int containers;
    private Decision keyDecision;
    private String pendingKey;
    private boolean rootDone;

    public JsonProjectorImpl(
        List<String> pointers)
    {
        this.retained = compileAll(pointers);
    }

    @Override
    public void reset()
    {
        depth = 0;
        containers = 0;
        keyDecision = null;
        pendingKey = null;
        rootDone = false;
    }

    @Override
    public Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case KEY_NAME:
            onKey(source);
            break;
        case START_OBJECT:
        case START_ARRAY:
            onStart(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onEnd(control, source, event, sink);
            break;
        default:
            onScalar(control, source, event, sink);
            break;
        }
        return rootDone ? Status.COMPLETE : Status.PENDING;
    }

    private void onKey(
        JsonSource source)
    {
        String key = source.getString();
        segments[depth] = key;
        indexes[depth] = -1;
        depth++;
        boolean parentKeepAll = containers > 0 && frameKeepAll[containers - 1];
        Decision d = parentKeepAll ? Decision.KEEP_ALL : decide();
        keyDecision = d;
        pendingKey = key;
    }

    private void forwardPendingKey(
        JsonController control,
        JsonSink sink)
    {
        if (pendingKey != null)
        {
            sink.feed(control, keySource.with(pendingKey), JsonEvent.KEY_NAME);
            pendingKey = null;
        }
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
            forwardPendingKey(control, sink);
            sink.feed(control, source, event);
        }
        else
        {
            pendingKey = null;
        }
        frameInArray[containers] = event == JsonEvent.START_ARRAY;
        frameEmit[containers] = emit;
        frameKeepAll[containers] = d == Decision.KEEP_ALL;
        frameNextIndex[containers] = 0;
        frameDepthAtOpen[containers] = depth;
        containers++;
    }

    private void onEnd(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        containers--;
        if (frameEmit[containers])
        {
            sink.feed(control, source, event);
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
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d == Decision.KEEP_ALL;
        if (emit)
        {
            forwardPendingKey(control, sink);
            sink.feed(control, source, event);
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
                segments[depth] = null;
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
        String pathSegment = segments[pathIndex];
        return pathSegment == null
            ? WILDCARD.equals(pointerSegment) || matchesIndex(pointerSegment, indexes[pathIndex])
            : pointerSegment.equals(pathSegment);
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
    }
}
