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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonParserMarkRewindTest
{
    private static int marks;
    private static int rewinds;
    private static int refused;

    private static final Set<String> IDLE_FIELDS = Set.of(
        "starved", "valuePending", "pendingString", "scratchConsumed", "resumeEscape",
        "resumeUnicodePending", "resumeUnicodeValue", "resumeLiteralIndex");

    private static final String DOCUMENT =
        "{\"model\":\"gpt\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"},{\"role\":\"tool\",\"n\":[1,2.5,-3e2]}]," +
            "\"stream\":true,\"x\":null}";

    @Test
    void shouldReplayEventsAfterRewind()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 6);
        parser.mark();
        final List<String> walked = pull(parser, 5);
        parser.rewind();
        final List<String> replayed = pull(parser, 5);

        assertEquals(walked, replayed);
    }

    @Test
    void shouldRestoreRemainingAfterRewind()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 4);
        final int remaining = parser.remaining();
        parser.mark();
        pull(parser, 7);
        parser.rewind();

        assertEquals(remaining, parser.remaining());
    }

    @Test
    void shouldRewindAfterCompletingMarkedValue()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 6);
        parser.mark();
        final List<String> element = pull(parser, 6);
        parser.rewind();

        assertEquals(element, pull(parser, 6));
        assertEquals(List.of("START_OBJECT", "KEY_NAME:role"), pull(parser, 2));
    }

    @Test
    void shouldRewindAcrossWindows()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final int split = DOCUMENT.indexOf("\"tool\"") + 3;
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, split, false);

        pull(parser, 6);
        parser.mark();
        final List<String> first = pullAll(parser);
        parser.rewind();
        final int position = split - parser.remaining();

        parser.wrap(new UnsafeBufferEx(bytes), position, bytes.length, true);
        final List<String> second = pullAll(parser);

        assertTrue(second.size() >= first.size());
        assertEquals(first.subList(0, Math.max(0, first.size() - 1)), second.subList(0, Math.max(0, first.size() - 1)));
        assertEquals("END_DOCUMENT", second.get(second.size() - 1));
    }

    @Test
    void shouldRejectMarkWhileEventStaged()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        parser.nextEvent();
        assertTrue(parser.hasNextEvent());

        assertThrows(IllegalStateException.class, parser::mark);
    }

    @Test
    void shouldRejectRewindWithoutMark()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 3);

        assertThrows(IllegalStateException.class, parser::rewind);
    }

    @Test
    void shouldRejectRewindAfterReset()
    {
        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 3);
        parser.mark();
        parser.reset();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        assertThrows(IllegalStateException.class, parser::rewind);
    }

    @Test
    void shouldRejectRewindAfterLeavingMarkedContainer()
    {
        final String document = "{\"a\":[1,2],\"b\":3}";
        final byte[] bytes = document.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        pull(parser, 4);
        parser.mark();
        pull(parser, 4);

        assertThrows(IllegalStateException.class, parser::rewind);
    }

    @Test
    void shouldRewindWhenWindowStarvesMidToken()
    {
        final String document = "{\"a\":{\"key\":\"value\",\"n\":12345},\"z\":1}";
        final byte[] bytes = document.getBytes(UTF_8);
        final int split = document.indexOf("alue") + 2;
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, split, false);

        pull(parser, 3);
        final int marked = split - parser.remaining();
        parser.mark();
        pullAll(parser);
        parser.rewind();

        assertEquals(marked, split - parser.remaining());
    }

    @Test
    void shouldMatchPlainParseForRandomRewinds()
    {
        for (int seed = 0; seed < 600; seed++)
        {
            final Random random = new Random(seed);
            final String document = new Generator(random).document();
            final byte[] bytes = document.getBytes(UTF_8);
            final int[] windows = windows(random, bytes.length);

            final List<String> expected = merged(parse(bytes, windows, null));
            final List<String> actual = merged(parse(bytes, windows, new Random(seed * 31L + 7)));

            assertEquals(expected, actual, "seed " + seed + " document " + document);
        }

        assertTrue(marks > 400, "marks " + marks);
        assertTrue(rewinds > 300, "rewinds " + rewinds + " refused " + refused);
    }

    // a value that fills the room left in its window is delivered as fragments, and where a window ends differs
    // once a rewind has moved the next window back, so compare each value whole
    private static List<String> merged(
        List<String> events)
    {
        final List<String> merged = new ArrayList<>();
        final StringBuilder pending = new StringBuilder();
        String pendingType = null;
        for (String event : events)
        {
            final int colon = event.indexOf(':');
            if (colon < 0)
            {
                merged.add(event);
            }
            else
            {
                final String type = event.substring(0, colon);
                final boolean fragment = event.endsWith("~");
                final String text = event.substring(colon + 1, fragment ? event.length() - 1 : event.length());
                if (pendingType != null)
                {
                    assertEquals(pendingType, type);
                }
                pending.append(text);
                pendingType = type;
                if (!fragment)
                {
                    merged.add(type + ":" + pending);
                    pending.setLength(0);
                    pendingType = null;
                }
            }
        }
        return merged;
    }

    private static List<String> parse(
        byte[] bytes,
        int[] windows,
        Random random)
    {
        final JsonParserEx parser = JsonEx.createParser();
        final List<String> events = new ArrayList<>();
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        int position = 0;
        int window = 0;
        int depth = 0;
        boolean marked = false;
        int markedDepth = 0;
        int markedEvents = 0;
        int budget = 0;
        Map<String, Object> snapshot = null;

        boolean finished = false;
        while (!finished)
        {
            final int limit = Math.min(bytes.length, position + windows[window % windows.length]);
            window++;
            final boolean last = limit == bytes.length;
            parser.wrap(buffer, position, limit, last);

            boolean more = true;
            while (more)
            {
                if (random != null && !marked && random.nextInt(6) == 0)
                {
                    try
                    {
                        parser.mark();
                        marks++;
                        snapshot = fields(parser);
                        marked = true;
                        markedDepth = depth;
                        markedEvents = events.size();
                        budget = 1 + random.nextInt(10);
                    }
                    catch (IllegalStateException ex)
                    {
                        refused++;
                    }
                }

                if (parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();
                    events.add(signature(parser, event));
                    if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
                    {
                        depth++;
                    }
                    else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                    {
                        depth--;
                    }

                    boolean end = event == JsonEvent.END_DOCUMENT;

                    if (marked && depth < markedDepth)
                    {
                        marked = false;
                    }
                    else if (marked && --budget == 0)
                    {
                        parser.rewind();
                        rewinds++;
                        assertEquals(snapshot, fields(parser));
                        trim(events, markedEvents);
                        depth = markedDepth;
                        marked = false;
                        end = false;
                    }

                    if (end)
                    {
                        finished = true;
                        more = false;
                    }
                }
                else
                {
                    if (marked)
                    {
                        parser.rewind();
                        rewinds++;
                        assertEquals(snapshot, fields(parser));
                        trim(events, markedEvents);
                        depth = markedDepth;
                        marked = false;
                    }
                    more = false;
                }
            }

            position = limit - parser.remaining();
            finished |= last && position >= bytes.length && events.contains("END_DOCUMENT");
            if (!finished && last && position >= bytes.length)
            {
                finished = true;
            }
        }

        return events;
    }

    private static void trim(
        List<String> events,
        int size)
    {
        while (events.size() > size)
        {
            events.remove(events.size() - 1);
        }
    }

    private static String signature(
        JsonParserEx parser,
        JsonEvent event)
    {
        String signature = event.name();
        switch (event)
        {
        case KEY_NAME:
        case VALUE_STRING:
        case VALUE_NUMBER:
            final boolean deferred = parser.deferredBytes();
            signature += ":" + parser.getString() + (deferred ? "~" : "");
            if (deferred)
            {
                parser.consumed(parser.getStringView().length());
            }
            break;
        default:
            break;
        }
        return signature;
    }

    private static List<String> pull(
        JsonParserEx parser,
        int count)
    {
        final List<String> events = new ArrayList<>();
        for (int i = 0; i < count && parser.hasNextEvent(); i++)
        {
            events.add(signature(parser, parser.nextEvent()));
        }
        return events;
    }

    private static List<String> pullAll(
        JsonParserEx parser)
    {
        final List<String> events = new ArrayList<>();
        while (parser.hasNextEvent())
        {
            events.add(signature(parser, parser.nextEvent()));
        }
        return events;
    }

    private static int[] windows(
        Random random,
        int length)
    {
        final int[] windows;
        if (random.nextInt(4) == 0)
        {
            windows = new int[] {length};
        }
        else
        {
            windows = new int[8];
            for (int i = 0; i < windows.length; i++)
            {
                windows[i] = 24 + random.nextInt(160);
            }
        }
        return windows;
    }

    // every non-final field of the parser and its tokenizer, plus the stacks the mark covers, so a field the
    // mark misses shows up as a difference between the mark and the state after the rewind
    private static Map<String, Object> fields(
        JsonParserEx parser)
    {
        final Map<String, Object> fields = new LinkedHashMap<>();
        try
        {
            collect(fields, "parser.", parser);
            final Field tokenizerField = parser.getClass().getDeclaredField("tokenizer");
            tokenizerField.setAccessible(true);
            final Object tokenizer = tokenizerField.get(parser);
            collect(fields, "tokenizer.", tokenizer);

            final int depth = (int) fields.get("tokenizer.pathDepth");
            fields.put("tokenizer.stack.size", ((java.util.Collection<?>) get(tokenizer, "stack")).size());
            if (depth > 0)
            {
                fields.put("tokenizer.frame.keySet", ((boolean[]) get(tokenizer, "pathKeySet"))[depth - 1]);
                fields.put("tokenizer.frame.index", ((int[]) get(tokenizer, "pathIndex"))[depth - 1]);
                fields.put("tokenizer.frame.key", ((StringBuilder[]) get(tokenizer, "pathKeyChars"))[depth - 1].toString());
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
        return fields;
    }

    private static void collect(
        Map<String, Object> fields,
        String prefix,
        Object target) throws ReflectiveOperationException
    {
        for (Field field : target.getClass().getDeclaredFields())
        {
            final int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) &&
                !field.getName().startsWith("mark") && !IDLE_FIELDS.contains(field.getName()) &&
                !field.getType().isArray())
            {
                field.setAccessible(true);
                fields.put(prefix + field.getName(), field.get(target));
            }
        }
    }

    private static Object get(
        Object target,
        String name) throws ReflectiveOperationException
    {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class Generator
    {
        private static final String[] KEYS = {"a", "model", "role", "content", "n", "key", "x", "tool_calls"};
        private static final String[] TEXTS = {"", "hi", "he said \\\"yes\\\"", "line\\nbreak", "café", "😀 ok",
            "\\u00e9\\ud83d\\ude00", "tab\\there", "plain text value"};
        private static final String[] NUMBERS = {"0", "7", "-12", "3.25", "-0.5e3", "1E+2", "123456789012"};

        private final Random random;
        private final StringBuilder out = new StringBuilder();

        Generator(
            Random random)
        {
            this.random = random;
        }

        String document()
        {
            value(0);
            return out.toString();
        }

        private void value(
            int depth)
        {
            final int kind = random.nextInt(depth >= 4 ? 5 : 8);
            switch (kind)
            {
            case 0:
                out.append('"').append(TEXTS[random.nextInt(TEXTS.length)]).append('"');
                break;
            case 1:
                out.append(NUMBERS[random.nextInt(NUMBERS.length)]);
                break;
            case 2:
                out.append("true");
                break;
            case 3:
                out.append("false");
                break;
            case 4:
                out.append("null");
                break;
            case 5:
            case 6:
                object(depth);
                break;
            default:
                array(depth);
                break;
            }
        }

        private void object(
            int depth)
        {
            out.append('{');
            final int members = random.nextInt(5);
            for (int i = 0; i < members; i++)
            {
                if (i > 0)
                {
                    out.append(',');
                }
                space();
                out.append('"').append(KEYS[random.nextInt(KEYS.length)]).append('"');
                space();
                out.append(':');
                space();
                value(depth + 1);
            }
            space();
            out.append('}');
        }

        private void array(
            int depth)
        {
            out.append('[');
            final int items = random.nextInt(5);
            for (int i = 0; i < items; i++)
            {
                if (i > 0)
                {
                    out.append(',');
                }
                space();
                value(depth + 1);
            }
            space();
            out.append(']');
        }

        private void space()
        {
            if (random.nextInt(4) == 0)
            {
                out.append(random.nextBoolean() ? " " : "\n ");
            }
        }
    }

    @Test
    void shouldGenerateNonTrivialDocuments()
    {
        int containers = 0;
        for (int seed = 0; seed < 50; seed++)
        {
            final String document = new Generator(new Random(seed)).document();
            containers += document.startsWith("{") || document.startsWith("[") ? 1 : 0;
        }

        assertTrue(containers > 10);
        assertFalse(new Generator(new Random(1)).document().isEmpty());
    }
}
