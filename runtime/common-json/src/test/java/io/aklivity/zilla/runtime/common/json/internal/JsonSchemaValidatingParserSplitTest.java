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
package io.aklivity.zilla.runtime.common.json.internal;

import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT4_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT6_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT7_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.ROOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonValidationException;

/**
 * Validates every case of the vendored JSON Schema Test Suite in two windows, split at every byte, once on one
 * parser and once saving the state of a validating parser after the first window and restoring it into a new
 * parser for the second, and requires the same outcome and the same diagnostics, locations and pointers. A split
 * where the parser holds state that cannot be saved is refused, and is not compared.
 */
class JsonSchemaValidatingParserSplitTest
{
    private static final int RECORD_CAPACITY = 4096;

    @Test
    @Disabled("a validating parser saves and restores its state by a later change")
    void shouldMatchUnsplitValidationAtEverySplitOfDraft4()
    {
        verify(Draft.DRAFT_04, "draft4", DRAFT4_FILES);
    }

    @Test
    @Disabled("a validating parser saves and restores its state by a later change")
    void shouldMatchUnsplitValidationAtEverySplitOfDraft6()
    {
        verify(Draft.DRAFT_06, "draft6", DRAFT6_FILES);
    }

    @Test
    @Disabled("a validating parser saves and restores its state by a later change")
    void shouldMatchUnsplitValidationAtEverySplitOfDraft7()
    {
        verify(Draft.DRAFT_07, "draft7", DRAFT7_FILES);
    }

    private static void verify(
        Draft draft,
        String dir,
        String[] files)
    {
        int saved = 0;
        int refused = 0;
        for (String file : files)
        {
            final JsonNode root = JsonNode.parse(JsonSchemaConformanceTest.read(ROOT + dir + "/" + file + ".json"));
            for (JsonNode group : root.elements())
            {
                final JsonSchema schema = compile(draft, group);
                if (schema != null)
                {
                    for (JsonNode test : group.get("tests").elements())
                    {
                        final byte[] bytes = (JsonSchemaConformanceTest.toJson(test.get("data")) + " ").getBytes(UTF_8);
                        for (int split = 1; split < bytes.length; split++)
                        {
                            final List<String> unsplit = outcome(schema, bytes, split, false);
                            final List<String> restored = outcome(schema, bytes, split, true);
                            if (restored == null)
                            {
                                refused++;
                            }
                            else
                            {
                                saved++;
                                assertEquals(unsplit, restored, file + " " + group.get("description").string() + " " +
                                    test.get("description").string() + " split " + split);
                            }
                        }
                    }
                }
            }
        }

        assertTrue(saved > 0, "no split could be saved, " + refused + " refused");
    }

    private static JsonSchema compile(
        Draft draft,
        JsonNode group)
    {
        JsonSchema schema;
        try
        {
            schema = JsonSchema.of(JsonSchemaConformanceTest.toJson(group.get("schema")),
                JsonSchemaConformanceTest::resolveRemote, draft);
        }
        catch (UnsupportedOperationException | IllegalArgumentException ex)
        {
            schema = null;
        }
        return schema;
    }

    // the outcome and every diagnostic of validating the bytes in two windows, or null when the state could not be saved
    private static List<String> outcome(
        JsonSchema schema,
        byte[] bytes,
        int split,
        boolean save)
    {
        final List<String> result = new ArrayList<>();
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);

        JsonParserEx parser = schema.newParser(false, JsonEx.createParser(), diagnostic -> result.add(render(diagnostic)));
        parser.wrap(input, 0, split, false);
        pull(parser);
        final int progress = split - parser.remaining();

        boolean saved = true;
        if (save)
        {
            try
            {
                parser.save(record, 0);
                parser = schema.newParser(false, JsonEx.createParser(), diagnostic -> result.add(render(diagnostic)));
                parser.restore(record, 0);
            }
            catch (IllegalStateException ex)
            {
                saved = false;
            }
        }

        if (saved)
        {
            parser.wrap(input, progress, bytes.length, true);
            pull(parser);
        }

        return saved ? result : null;
    }

    private static void pull(
        JsonParserEx parser)
    {
        try
        {
            while (parser.hasNextEvent())
            {
                switch (parser.nextEvent(JsonParserEx.Mode.STRUCTURED))
                {
                case VALUE_STRING:
                case VALUE_NUMBER:
                case KEY_NAME:
                    parser.consumed(parser.getStringView().length());
                    break;
                default:
                    break;
                }
            }
        }
        catch (JsonValidationException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static String render(
        JsonSchemaDiagnostic diagnostic)
    {
        return diagnostic.line() + "," + diagnostic.column() + " " + diagnostic.pointer() + " " + diagnostic.keyword() + ": " +
            diagnostic.message();
    }
}
