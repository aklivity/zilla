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

import static jakarta.json.stream.JsonParser.Event.START_ARRAY;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEventConsumer;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonProjector;
import io.aklivity.zilla.runtime.common.json.StreamingJson;

/**
 * A resumable, event-driven {@link JsonEventConsumer} that projects a JSON document down to a set
 * of retained paths and forwards the kept events to a downstream sink.
 * <p>
 * Retained paths are RFC 6901 JSON Pointers; a {@code -} array-index segment is a wildcard
 * matching any index. A node is forwarded when it lies on a retained branch — either a retained
 * pointer is a prefix of the node's path (the node is inside a retained subtree, kept whole) or
 * the node's path is a prefix of a retained pointer (an ancestor, descended to reach retained
 * descendants). Every other subtree is dropped. Output is in source order.
 * <p>
 * The projector is fed one parser event at a time via {@link #feed(Event, JsonParser)} and holds
 * no reference to the parser between feeds, so it can be paused at any event boundary while the
 * upstream slot-fragmented parser awaits more bytes. The convenience {@link
 * #project(JsonParser, MutableDirectBuffer, int)} method drives an internal generator-sink in one
 * shot for the simple, complete-buffer case.
 */
public final class StreamingJsonProjector implements JsonProjector
{
    private static final int MAX_DEPTH = 64;
    private static final String WILDCARD = "-";

    private enum Decision
    {
        KEEP_ALL, DESCEND, SKIP
    }

    private final List<String[]> retained;
    private final JsonEventConsumer sink;
    private final JsonGeneratorEx ownedGenerator;

    private final String[] segments = new String[MAX_DEPTH];
    private final int[] indexes = new int[MAX_DEPTH];

    private final boolean[] frameInArray = new boolean[MAX_DEPTH];
    private final boolean[] frameEmit = new boolean[MAX_DEPTH];
    private final boolean[] frameKeepAll = new boolean[MAX_DEPTH];
    private final int[] frameNextIndex = new int[MAX_DEPTH];
    private final int[] frameDepthAtOpen = new int[MAX_DEPTH];

    private final KeyParser keyParser = new KeyParser();

    private int depth;
    private int containers;
    private Decision keyDecision;
    private String pendingKey;
    private boolean rootDone;

    public StreamingJsonProjector(
        List<String> pointers)
    {
        this(pointers, null);
    }

    public StreamingJsonProjector(
        List<String> pointers,
        JsonEventConsumer sink)
    {
        this.retained = compileAll(pointers);
        if (sink == null)
        {
            this.ownedGenerator = StreamingJson.createGenerator();
            this.sink = JsonEventConsumer.of(ownedGenerator);
        }
        else
        {
            this.ownedGenerator = null;
            this.sink = sink;
        }
    }

    @Override
    public int project(
        JsonParser parser,
        MutableDirectBuffer buffer,
        int offset)
    {
        if (ownedGenerator == null)
        {
            throw new IllegalStateException("project requires the no-sink constructor");
        }
        reset();
        ownedGenerator.wrap(buffer, offset);
        Status status = Status.PENDING;
        while (status == Status.PENDING && parser.hasNext())
        {
            status = feed(parser.next(), parser);
        }
        return ownedGenerator.length();
    }

    @Override
    public void reset()
    {
        depth = 0;
        containers = 0;
        keyDecision = null;
        pendingKey = null;
        rootDone = false;
        sink.reset();
    }

    @Override
    public Status feed(
        Event event,
        JsonParser parser)
    {
        switch (event)
        {
        case KEY_NAME:
            onKey(parser);
            break;
        case START_OBJECT:
        case START_ARRAY:
            onStart(event, parser);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onEnd(event, parser);
            break;
        default:
            onScalar(event, parser);
            break;
        }
        return rootDone ? Status.COMPLETE : Status.PENDING;
    }

    private void onKey(
        JsonParser parser)
    {
        String key = parser.getString();
        segments[depth] = key;
        indexes[depth] = -1;
        depth++;
        boolean parentKeepAll = containers > 0 && frameKeepAll[containers - 1];
        Decision d = parentKeepAll ? Decision.KEEP_ALL : decide();
        keyDecision = d;
        pendingKey = key;
    }

    private void forwardPendingKey(
        JsonParser parser)
    {
        if (pendingKey != null)
        {
            sink.feed(Event.KEY_NAME, keyParser.with(parser, pendingKey));
            pendingKey = null;
        }
    }

    private void onStart(
        Event event,
        JsonParser parser)
    {
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d != Decision.SKIP;
        if (emit)
        {
            forwardPendingKey(parser);
            sink.feed(event, parser);
        }
        else
        {
            pendingKey = null;
        }
        frameInArray[containers] = event == START_ARRAY;
        frameEmit[containers] = emit;
        frameKeepAll[containers] = d == Decision.KEEP_ALL;
        frameNextIndex[containers] = 0;
        frameDepthAtOpen[containers] = depth;
        containers++;
    }

    private void onEnd(
        Event event,
        JsonParser parser)
    {
        containers--;
        if (frameEmit[containers])
        {
            sink.feed(event, parser);
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
        Event event,
        JsonParser parser)
    {
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d == Decision.KEEP_ALL;
        if (emit)
        {
            forwardPendingKey(parser);
            sink.feed(event, parser);
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

    private static final class KeyParser implements JsonParser
    {
        private JsonParser delegate;
        private String keyOverride;

        KeyParser with(
            JsonParser delegate,
            String keyOverride)
        {
            this.delegate = delegate;
            this.keyOverride = keyOverride;
            return this;
        }

        @Override
        public String getString()
        {
            return keyOverride != null ? keyOverride : delegate.getString();
        }

        @Override
        public JsonLocation getLocation()
        {
            return delegate.getLocation();
        }

        @Override
        public boolean hasNext()
        {
            return delegate.hasNext();
        }

        @Override
        public Event next()
        {
            return delegate.next();
        }

        @Override
        public boolean isIntegralNumber()
        {
            return delegate.isIntegralNumber();
        }

        @Override
        public int getInt()
        {
            return delegate.getInt();
        }

        @Override
        public long getLong()
        {
            return delegate.getLong();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return delegate.getBigDecimal();
        }

        @Override
        public JsonObject getObject()
        {
            return delegate.getObject();
        }

        @Override
        public JsonValue getValue()
        {
            return delegate.getValue();
        }

        @Override
        public JsonArray getArray()
        {
            return delegate.getArray();
        }

        @Override
        public Stream<JsonValue> getArrayStream()
        {
            return delegate.getArrayStream();
        }

        @Override
        public Stream<Map.Entry<String, JsonValue>> getObjectStream()
        {
            return delegate.getObjectStream();
        }

        @Override
        public Stream<JsonValue> getValueStream()
        {
            return delegate.getValueStream();
        }

        @Override
        public void skipObject()
        {
            delegate.skipObject();
        }

        @Override
        public void skipArray()
        {
            delegate.skipArray();
        }

        @Override
        public void close()
        {
        }
    }
}
