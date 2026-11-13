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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonParserSaveRestoreTest
{
    private static final int RECORD_OFFSET = 7;
    private static final int RECORD_CAPACITY = 256;
    private static final Set<String> ESCAPED_KEYS = Set.of("payload");
    private static final int[] WINDOWS = {1, 2, 3, 4, 5, 7, 8, 11, 13, 16, 21, 33, 64, 128};

    @Test
    void shouldMatchSingleWindowAtEverySplitPoint()
    {
        for (String document : corpus())
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final List<String> expected = events(bytes, new int[] {bytes.length}, false);

            for (int split = 1; split < bytes.length; split++)
            {
                final int[] limits = {split, bytes.length};
                assertEquals(expected, events(bytes, limits, true), "split " + split + " of " + document);
            }
        }
    }

    @Test
    void shouldMatchSingleWindowAcrossWindowSizes()
    {
        for (String document : corpus())
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final List<String> expected = events(bytes, new int[] {bytes.length}, false);

            for (int window : WINDOWS)
            {
                final int[] limits = limits(bytes.length, window);
                assertEquals(expected, events(bytes, limits, true), "window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldMatchWindowedParseWithoutSaveAcrossWindowSizes()
    {
        for (String document : corpus())
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final List<String> expected = events(bytes, new int[] {bytes.length}, false);

            for (int window : WINDOWS)
            {
                final int[] limits = limits(bytes.length, window);
                assertEquals(expected, events(bytes, limits, false), "window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldMatchSingleWindowWhenAConsumerLeavesPartOfAFragment()
    {
        final List<String> documents = List.of(
            "{\"long\":\"" + "abcdefghij".repeat(40) + "\"}",
            "[\"" + "0123456789".repeat(25) + "\",\"" + "klmnopqrst".repeat(25) + "\"]");

        for (String document : documents)
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final List<String> expected = events(bytes, new int[] {bytes.length}, false, true, true, null);

            for (int window : new int[] {40, 64, 100, 128})
            {
                final int[] limits = limits(bytes.length, window);
                assertEquals(expected, events(bytes, limits, true, true, true, null), "window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldCarryOnlyALiteralAFragmentedStringAndAnEscapedScopeAcrossAWindow()
    {
        final Set<String> observed = new HashSet<>();
        for (String document : corpus())
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            for (int window : WINDOWS)
            {
                events(bytes, limits(bytes.length, window), true, true, false, observed);
            }
            for (int split = 1; split < bytes.length; split++)
            {
                events(bytes, new int[] {split, bytes.length}, true, true, false, observed);
            }
        }

        final Set<String> resumes = observed.stream().filter(o -> o.startsWith("resumeOp=")).collect(Collectors.toSet());
        assertEquals(Set.of("resumeOp=NONE", "resumeOp=VALUE_TRUE", "resumeOp=VALUE_FALSE", "resumeOp=VALUE_NULL",
            "resumeOp=VALUE_STRING"), resumes);
        assertTrue(!observed.contains("stringResumeWithoutFragmenting"), "string resume " + observed);
        assertTrue(observed.contains("fragmenting"), "fragmenting " + observed);
        assertTrue(observed.contains("lens"), "lens " + observed);
        assertTrue(observed.contains("fragmentOpen"), "fragmentOpen " + observed);
        assertTrue(observed.contains("escapeArmed"), "escapeArmed " + observed);
        assertTrue(observed.contains("memberSeparated"), "memberSeparated " + observed);
        assertEquals(Set.of(), observed.stream()
            .filter(o -> o.startsWith("unicode") || o.startsWith("escape=")).collect(Collectors.toSet()));
    }

    @Test
    void shouldDivergeWhenTheCorruptedStateIsNotRestored()
    {
        int diverged = 0;
        int total = 0;
        for (String document : corpus())
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final List<String> expected = events(bytes, new int[] {bytes.length}, false);

            for (int split = 1; split < bytes.length; split += 3)
            {
                total++;
                try
                {
                    if (!expected.equals(events(bytes, new int[] {split, bytes.length}, true, false, false, null)))
                    {
                        diverged++;
                    }
                }
                catch (RuntimeException | AssertionError ex)
                {
                    diverged++;
                }
            }
        }

        assertTrue(diverged * 10 > total * 9, "diverged " + diverged + " of " + total);
    }

    @Test
    void shouldNotSaveOrRestoreAValidatingParser()
    {
        final JsonSchema schema = JsonSchema.of("{\"type\":\"object\"}");
        final JsonParserEx validating = (JsonParserEx) schema.newParser(true, JsonEx.createParser());
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);

        assertThrows(UnsupportedOperationException.class, () -> validating.save(record, 0));
        assertThrows(UnsupportedOperationException.class, () -> validating.restore(record, 0));
    }

    @Test
    void shouldSaveWithinRecordCapacityAtDepthLimit()
    {
        final String document = "[".repeat(64) + "1";
        final byte[] bytes = document.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, false);
        while (parser.hasNextEvent())
        {
            parser.nextEvent();
        }

        final int length = parser.save(new UnsafeBufferEx(new byte[RECORD_CAPACITY]), RECORD_OFFSET);

        assertTrue(length > 0 && length <= 96, "record length " + length);
    }

    @Test
    void shouldRejectSaveWithAnEventPending()
    {
        final byte[] bytes = "{\"a\":1}".getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        parser.nextEvent();
        parser.nextEvent();
        assertTrue(parser.hasNextEvent());

        assertThrows(IllegalStateException.class, () -> parser.save(new UnsafeBufferEx(new byte[RECORD_CAPACITY]), 0));
    }

    @Test
    void shouldRejectSaveWhileASegmentIsOpen()
    {
        final byte[] bytes = "{\"a\":[1,2,{\"b\":3}]}".getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, 9, false);
        parser.nextEvent(Mode.STRUCTURED);
        parser.nextEvent(Mode.STRUCTURED);
        parser.nextEvent(Mode.SEGMENTED);
        while (parser.hasNextEvent())
        {
            parser.nextEvent(Mode.SEGMENTED);
        }

        assertThrows(IllegalStateException.class, () -> parser.save(new UnsafeBufferEx(new byte[RECORD_CAPACITY]), 0));
    }

    private static int[] limits(
        int length,
        int window)
    {
        final List<Integer> limits = new ArrayList<>();
        for (int limit = window; limit < length; limit += window)
        {
            limits.add(limit);
        }
        limits.add(length);
        return limits.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<String> events(
        byte[] bytes,
        int[] limits,
        boolean roundTrip)
    {
        return events(bytes, limits, roundTrip, true, false, null);
    }

    private static List<String> events(
        byte[] bytes,
        int[] limits,
        boolean roundTrip,
        boolean restore,
        boolean partial,
        Set<String> observed)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final JsonParserEx parser = JsonEx.createParser();
        final List<String> events = new ArrayList<>();
        final StringBuilder text = new StringBuilder();

        int progress = 0;
        for (int index = 0; index < limits.length; index++)
        {
            final int limit = limits[index];
            final boolean last = index == limits.length - 1;

            parser.wrap(input, progress, limit, last);

            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);

                switch (event)
                {
                case KEY_NAME:
                    final String key = parser.getStringView().toString();
                    events.add("KEY_NAME(" + key + ")");
                    if (ESCAPED_KEYS.contains(key))
                    {
                        parser.escaped();
                    }
                    break;
                case VALUE_STRING:
                case VALUE_NUMBER:
                    final CharSequence chars = parser.getStringView();
                    final boolean decline = partial && parser.deferredBytes() && chars.length() > 1;
                    final int taken = decline ? chars.length() / 2 : chars.length();
                    text.append(chars, 0, taken);
                    parser.consumed(taken);
                    if (!parser.deferredBytes())
                    {
                        events.add(event + "(" + text + ")");
                        text.setLength(0);
                    }
                    break;
                case START_DOCUMENT:
                    break;
                default:
                    events.add(partial ? event.name() : event.name() + located(parser, event));
                    break;
                }
            }

            progress = limit - parser.remaining();

            if (roundTrip && !last)
            {
                if (observed != null)
                {
                    observe(parser, observed);
                }

                final int length = parser.save(record, RECORD_OFFSET);
                JsonStateCorruption.corruptParser(parser);
                if (restore)
                {
                    parser.restore(record, RECORD_OFFSET);
                }
                assertTrue(length > 0);
            }
        }

        return events;
    }

    private static String located(
        JsonParserEx parser,
        JsonEvent event)
    {
        final boolean located = event == JsonEvent.START_OBJECT || event == JsonEvent.END_OBJECT ||
            event == JsonEvent.START_ARRAY || event == JsonEvent.END_ARRAY;
        return located ? "@" + parser.getLocation().getLineNumber() + ":" + parser.getLocation().getColumnNumber() : "";
    }

    private static void observe(
        JsonParserEx parser,
        Set<String> observed)
    {
        try
        {
            final Field tokenizerField = parser.getClass().getDeclaredField("tokenizer");
            tokenizerField.setAccessible(true);
            final Object tokenizer = tokenizerField.get(parser);
            final Object resumeOp = read(tokenizer, "resumeOp");
            observed.add("resumeOp=" + resumeOp);
            if ("VALUE_STRING".equals(resumeOp.toString()) && !(boolean) read(tokenizer, "fragmenting"))
            {
                observed.add("stringResumeWithoutFragmenting");
            }
            for (String flag : List.of("fragmenting", "lens", "escapeArmed", "memberSeparated", "segmenting"))
            {
                if ((boolean) read(tokenizer, flag))
                {
                    observed.add(flag);
                }
            }
            if ((boolean) read(parser, "fragmentOpen"))
            {
                observed.add("fragmentOpen");
            }
            if ((boolean) read(tokenizer, "resumeEscape"))
            {
                observed.add("escape=resumeEscape");
            }
            if ((int) read(tokenizer, "resumeUnicodePending") != 0)
            {
                observed.add("unicodePending");
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static Object read(
        Object target,
        String name) throws ReflectiveOperationException
    {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<String> corpus()
    {
        final List<String> corpus = new ArrayList<>();
        corpus.add("{\"a\":1,\"b\":[true,false,null],\"c\":{\"d\":\"e\"}}");
        corpus.add("[1,-2,3.5,-0.5e3,1E+2,0,123456789012345678]");
        corpus.add("{\"plain\":\"hello world\",\"empty\":\"\",\"key with space\":\"v\"}");
        corpus.add("{\"multibyte\":\"caf\u00e9 \u20ac \ud83d\ude00 \u4e2d\u6587\",\"k\u00e9y\":\"\u00fc\"}");
        corpus.add("{\"escapes\":\"line\\nbreak\\ttab \\\"quote\\\" back\\\\slash \\/ \\b\\f\\r\"}");
        corpus.add("{\"unicode\":\"\\u00e9\\u20ac\\ud83d\\ude00\\u0041\",\"pair\":\"\\ud83d\\ude00\\ud83d\\ude01\"}");
        corpus.add("[true,false,null,true,null,false]");
        corpus.add("{\"long\":\"" + "abcdefghij".repeat(40) + "\"}");
        corpus.add("{\"longescaped\":\"" + "ab\\n\\u00e9\\ud83d\\ude00".repeat(30) + "\"}");
        corpus.add("{\"longmultibyte\":\"" + "\u00e9\u20ac\ud83d\ude00".repeat(60) + "\"}");
        corpus.add("[" + "{\"k\":[1,2,\"three\"]},".repeat(8) + "null]");
        corpus.add("\"just a string\"");
        corpus.add("12345");
        corpus.add("true");
        corpus.add("{\"a\" : 1 ,\n\t\"b\"\r\n:\n[ 1 , 2 ]\n}");
        corpus.add("[".repeat(64) + "1" + "]".repeat(64));
        corpus.add("{\"k\":".repeat(64) + "\"v\"" + "}".repeat(64));
        corpus.add(mixedDepth(64));
        corpus.add("{\"payload\":\"{\\\"b\\\":[1,\\\"x\\\\ny\\\",true,null],\\\"c\\\":{\\\"d\\\":2.5}}\",\"after\":1}");
        corpus.add("{\"payload\":\"[1,2,3]\",\"n\":{\"payload\":\"\\\"s\\\"\"}}");
        corpus.add("{\"payload\":\" \\n {\\\"b\\\" : 1 } \\t \"}");
        corpus.add("{\"payload\":\"\\u007b\\u0022b\\u0022:\\u0031\\u007d\"}");
        corpus.add("[" + "[".repeat(60) + "{\"payload\":\"[1]\"}" + "]".repeat(60) + "]");
        return corpus;
    }

    private static String mixedDepth(
        int depth)
    {
        final StringBuilder open = new StringBuilder();
        final StringBuilder close = new StringBuilder();
        for (int level = 0; level < depth; level++)
        {
            if (level % 3 == 0)
            {
                open.append("[0,");
                close.insert(0, ",1]");
            }
            else
            {
                open.append("{\"a\":0,\"b\":");
                close.insert(0, ",\"c\":2}");
            }
        }
        return open + "null" + close;
    }
}
