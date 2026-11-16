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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

class JsonGeneratorMarkRewindTest
{
    private static final List<Supplier<JsonGeneratorEx>> GENERATORS = List.of(
        JsonEx::createGenerator,
        () -> JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true)));

    private static final List<Consumer<JsonGeneratorEx>> NESTED = List.of(
        g -> g.writeStartObject(),
        g -> g.writeKey("a"),
        g -> g.writeStartArray(),
        g -> g.write(1),
        g -> g.write("x"),
        g -> g.writeStartObject(),
        g -> g.writeKey("k"),
        g -> g.write(true),
        g -> g.writeEnd(),
        g -> g.writeNull(),
        g -> g.writeEnd(),
        g -> g.writeKey("s"),
        g -> g.write("tail"),
        g -> g.writeEnd());

    private static final List<Consumer<JsonGeneratorEx>> ESCAPED = List.of(
        g -> g.writeStartObject(),
        g -> g.writeKey("e"),
        g -> g.writeStartEscaped(),
        g -> g.writeStartObject(),
        g -> g.writeKey("q"),
        g -> g.write("a\"b"),
        g -> g.writeKey("r"),
        g -> g.writeStartEscaped(),
        g -> g.write("inner\"quote"),
        g -> g.writeEndEscaped(),
        g -> g.writeEnd(),
        g -> g.writeEndEscaped(),
        g -> g.writeKey("f"),
        g -> g.write(2),
        g -> g.writeEnd());

    private static final List<Consumer<JsonGeneratorEx>> FRAGMENTS = List.of(
        g -> g.writeStartObject(),
        g -> g.writeKey("s"),
        g -> g.writeSegment(buffer("\"ab"), 0, 3, Completion.INCOMPLETE),
        g -> g.writeSegment(buffer("cd"), 0, 2, Completion.INCOMPLETE),
        g -> g.writeSegment(buffer("ef\""), 0, 3, Completion.COMPLETE),
        g -> g.writeKey("n"),
        g -> g.writeNumber("12", Completion.INCOMPLETE),
        g -> g.writeNumber("34", Completion.COMPLETE),
        g -> g.writeKey("l"),
        g -> g.writeStartArray(),
        g -> g.write(1),
        g -> g.write(2),
        g -> g.writeEnd(),
        g -> g.writeEnd());

    @Test
    void shouldGenerateTheSameAfterRewindAtEveryPosition()
    {
        for (Supplier<JsonGeneratorEx> generators : GENERATORS)
        {
            for (List<Consumer<JsonGeneratorEx>> document : List.of(NESTED, ESCAPED, FRAGMENTS))
            {
                final String expected = generate(generators, document, -1, -1);

                for (int markAt = 0; markAt <= document.size(); markAt++)
                {
                    for (int walkTo = markAt; walkTo <= document.size(); walkTo++)
                    {
                        assertEquals(expected, generate(generators, document, markAt, walkTo),
                            "mark " + markAt + " walk " + walkTo);
                    }
                }
            }
        }
    }

    @Test
    void shouldRestoreEveryFieldOfTheGenerator()
    {
        for (Supplier<JsonGeneratorEx> generators : GENERATORS)
        {
            for (List<Consumer<JsonGeneratorEx>> document : List.of(NESTED, ESCAPED, FRAGMENTS))
            {
                for (int markAt = 0; markAt <= document.size(); markAt++)
                {
                    final JsonGeneratorEx generator = generators.get();
                    generator.wrap(new UnsafeBufferEx(new byte[256]), 0, 256);

                    document.subList(0, markAt).forEach(write -> write.accept(generator));
                    generator.mark();

                    final Map<String, Object> marked = fields(generator);

                    document.subList(markAt, document.size()).forEach(write -> write.accept(generator));
                    generator.rewind();

                    assertEquals(marked, fields(generator), "mark " + markAt);
                }
            }
        }
    }

    @Test
    void shouldRestoreLengthRemainingAndConsumed()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(new UnsafeBufferEx(new byte[64]), 0, 64);

        generator.writeStartObject().writeKey("s");
        generator.writeSegment(buffer("\"abc"), 0, 4, Completion.INCOMPLETE);

        final int length = generator.length();
        final int remaining = generator.remaining();
        final int consumed = generator.consumed();

        generator.mark();
        generator.writeSegment(buffer("defg\""), 0, 5, Completion.COMPLETE);
        generator.rewind();

        assertEquals(length, generator.length());
        assertEquals(remaining, generator.remaining());
        assertEquals(consumed, generator.consumed());
    }

    @Test
    void shouldReplaceTheMarkWhenMarkingAgain()
    {
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[64]);
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(output, 0, 64);

        generator.writeStartArray().write(1);
        generator.mark();
        generator.write(2);
        generator.mark();
        generator.write(3);
        generator.rewind();
        generator.writeEnd();

        assertEquals("[1,2]", output.getStringWithoutLengthUtf8(0, generator.length()));
    }

    @Test
    void shouldRefuseRewindWithoutMark()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(new UnsafeBufferEx(new byte[16]), 0, 16);

        assertThrows(IllegalStateException.class, generator::rewind);
    }

    @Test
    void shouldRefuseRewindAfterWrap()
    {
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[16]);
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(output, 0, 16);
        generator.writeStartArray();
        generator.mark();
        generator.wrap(output, 0, 16);

        assertThrows(IllegalStateException.class, generator::rewind);
    }

    @Test
    void shouldRefuseRewindAfterReset()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(new UnsafeBufferEx(new byte[16]), 0, 16);
        generator.writeStartArray();
        generator.mark();
        generator.reset();

        assertThrows(IllegalStateException.class, generator::rewind);
    }

    private static String generate(
        Supplier<JsonGeneratorEx> generators,
        List<Consumer<JsonGeneratorEx>> document,
        int markAt,
        int walkTo)
    {
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[256]);
        final JsonGeneratorEx generator = generators.get();
        generator.wrap(output, 0, output.capacity());

        if (markAt < 0)
        {
            document.forEach(write -> write.accept(generator));
        }
        else
        {
            document.subList(0, markAt).forEach(write -> write.accept(generator));
            generator.mark();
            document.subList(markAt, walkTo).forEach(write -> write.accept(generator));
            generator.rewind();
            document.subList(markAt, document.size()).forEach(write -> write.accept(generator));
        }

        return output.getStringWithoutLengthUtf8(0, generator.length());
    }

    // every non-final field of the generator, so a field the mark misses shows up as a difference between the
    // mark and the state after the rewind; the two output paths are chosen by the escape depth, so they are compared
    // through it
    private static Map<String, Object> fields(
        JsonGeneratorEx generator)
    {
        final Map<String, Object> fields = new LinkedHashMap<>();

        try
        {
            for (Field field : generator.getClass().getDeclaredFields())
            {
                final int modifiers = field.getModifiers();

                if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) &&
                    !field.getName().startsWith("mark") && !field.getType().isArray() &&
                    !field.getName().equals("putByte") && !field.getName().equals("writeSegment"))
                {
                    field.setAccessible(true);
                    fields.put(field.getName(), field.get(generator));
                }
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }

        return fields;
    }

    private static DirectBufferEx buffer(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
