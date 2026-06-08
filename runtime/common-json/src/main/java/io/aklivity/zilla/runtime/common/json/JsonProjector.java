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

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.MutableDirectBuffer;

/**
 * Projects a JSON document down to a set of retained paths, reading a streaming {@link
 * JsonParser} and writing the pruned, compact result into a {@link MutableDirectBuffer} via
 * {@link JsonGeneratorEx} — no DOM, no per-call allocation.
 * <p>
 * Retained paths are RFC 6901 JSON Pointers; a {@code -} array-index segment is a wildcard
 * matching any index. A node is emitted when it lies on a retained branch — either a retained
 * pointer is a prefix of the node's path (the node is inside a retained subtree, kept whole) or
 * the node's path is a prefix of a retained pointer (an ancestor, descended to reach retained
 * descendants). Every other subtree is consumed and discarded. Output is in source order.
 */
public final class JsonProjector
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
    private final JsonGeneratorEx generator = StreamingJson.createGenerator();

    private int depth;

    public JsonProjector(
        List<String> pointers)
    {
        List<String[]> compiled = new ArrayList<>();
        for (String pointer : pointers)
        {
            compiled.add(compile(pointer));
        }
        this.retained = compiled;
    }

    public int project(
        JsonParser parser,
        MutableDirectBuffer buffer,
        int offset)
    {
        generator.wrap(buffer, offset);
        depth = 0;
        if (parser.hasNext())
        {
            projectValue(parser, parser.next(), decide());
        }
        return generator.length();
    }

    private void projectValue(
        JsonParser parser,
        Event event,
        Decision decision)
    {
        switch (event)
        {
        case START_OBJECT:
            projectObject(parser, decision);
            break;
        case START_ARRAY:
            projectArray(parser, decision);
            break;
        case VALUE_STRING:
            if (decision == Decision.KEEP_ALL)
            {
                generator.write(parser.getString());
            }
            break;
        case VALUE_NUMBER:
            if (decision == Decision.KEEP_ALL)
            {
                generator.writeNumber(parser.getString());
            }
            break;
        case VALUE_TRUE:
            if (decision == Decision.KEEP_ALL)
            {
                generator.write(true);
            }
            break;
        case VALUE_FALSE:
            if (decision == Decision.KEEP_ALL)
            {
                generator.write(false);
            }
            break;
        case VALUE_NULL:
            if (decision == Decision.KEEP_ALL)
            {
                generator.writeNull();
            }
            break;
        default:
            break;
        }
    }

    private void projectObject(
        JsonParser parser,
        Decision decision)
    {
        boolean emit = decision != Decision.SKIP;
        if (emit)
        {
            generator.writeStartObject();
        }
        Event event = parser.next();
        while (event != END_OBJECT)
        {
            String key = parser.getString();
            segments[depth++] = key;
            Event value = parser.next();
            Decision child = decision == Decision.KEEP_ALL ? Decision.KEEP_ALL : decide();
            if (emit && keeps(child, value))
            {
                generator.writeKey(key);
                projectValue(parser, value, child);
            }
            else
            {
                consume(parser, value);
            }
            depth--;
            event = parser.next();
        }
        if (emit)
        {
            generator.writeEnd();
        }
    }

    private void projectArray(
        JsonParser parser,
        Decision decision)
    {
        boolean emit = decision != Decision.SKIP;
        if (emit)
        {
            generator.writeStartArray();
        }
        int index = 0;
        Event event = parser.next();
        while (event != END_ARRAY)
        {
            segments[depth] = null;
            indexes[depth] = index;
            depth++;
            Decision child = decision == Decision.KEEP_ALL ? Decision.KEEP_ALL : decide();
            if (emit && keeps(child, event))
            {
                projectValue(parser, event, child);
            }
            else
            {
                consume(parser, event);
            }
            depth--;
            index++;
            event = parser.next();
        }
        if (emit)
        {
            generator.writeEnd();
        }
    }

    private static boolean keeps(
        Decision decision,
        Event event)
    {
        return decision == Decision.KEEP_ALL ||
            decision == Decision.DESCEND && (event == START_OBJECT || event == START_ARRAY);
    }

    private static void consume(
        JsonParser parser,
        Event event)
    {
        if (event == START_OBJECT)
        {
            Event next = parser.next();
            while (next != END_OBJECT)
            {
                consume(parser, parser.next());
                next = parser.next();
            }
        }
        else if (event == START_ARRAY)
        {
            Event next = parser.next();
            while (next != END_ARRAY)
            {
                consume(parser, next);
                next = parser.next();
            }
        }
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
}
