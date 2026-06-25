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

import static io.aklivity.zilla.runtime.common.json.JsonSchema.Draft.DRAFT_2020_12;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
class JsonSchemaRefResolutionTest
{
    @Test
    void shouldResolveAnchor()
    {
        String schema = "{" +
            "\"$defs\":{\"int\":{\"$anchor\":\"intDef\",\"type\":\"integer\"}}," +
            "\"$ref\":\"#intDef\"}";
        assertTrue(valid(schema, "5", DRAFT_2020_12));
        assertFalse(valid(schema, "\"x\"", DRAFT_2020_12));
    }

    @Test
    void shouldResolveRefAgainstIdBase()
    {
        String schema = "{" +
            "\"$id\":\"https://example.com/root.json\"," +
            "\"$defs\":{\"str\":{\"$id\":\"strings.json\",\"type\":\"string\"}}," +
            "\"$ref\":\"strings.json\"}";
        assertTrue(valid(schema, "\"hi\"", DRAFT_2020_12));
        assertFalse(valid(schema, "5", DRAFT_2020_12));
    }

    @Test
    void shouldResolveAbsoluteRefToSiblingIdDocument()
    {
        // AsyncAPI-style: an absolute $ref to a subschema that declares the same absolute $id
        String schema = "{" +
            "\"$id\":\"http://example.com/schema.json\"," +
            "\"properties\":{\"a\":{\"$ref\":\"http://example.com/defs.json\"}}," +
            "\"$defs\":{\"a\":{\"$id\":\"http://example.com/defs.json\",\"type\":\"integer\"}}}";
        assertTrue(valid(schema, "{\"a\":5}", DRAFT_2020_12));
        assertFalse(valid(schema, "{\"a\":\"x\"}", DRAFT_2020_12));
    }

    @Test
    void shouldResolveRemoteRefViaResolver()
    {
        JsonRefResolver resolver = uri -> "http://remote/int".equals(uri)
            ? "{\"type\":\"integer\"}"
            : null;
        JsonSchema schema = JsonSchema.of("{\"$ref\":\"http://remote/int\"}", resolver, DRAFT_2020_12);
        assertTrue(schema.validate(parserFor("7 ")));
        assertFalse(schema.validate(parserFor("\"x\" ")));
    }

    @Test
    void shouldApplyRefAlongsideSiblingsInDraft2019Plus()
    {
        // draft 2019+: $ref no longer suppresses sibling keywords
        String schema = "{\"$defs\":{\"num\":{\"type\":\"number\"}}," +
            "\"$ref\":\"#/$defs/num\",\"minimum\":10}";
        assertTrue(valid(schema, "12", DRAFT_2020_12));
        assertFalse(valid(schema, "5", DRAFT_2020_12));
        assertFalse(valid(schema, "\"x\"", DRAFT_2020_12));
    }

    @Test
    void shouldIgnoreSiblingsOfRefInDraft07()
    {
        // draft-07: $ref ignores siblings, so minimum is not applied
        String schema = "{\"definitions\":{\"num\":{\"type\":\"number\"}}," +
            "\"$ref\":\"#/definitions/num\",\"minimum\":10}";
        assertTrue(valid(schema, "5", JsonSchema.Draft.DRAFT_07));
    }

    @Test
    void shouldFailWhenRemoteRefUnresolvable()
    {
        JsonSchema schema = JsonSchema.of("{\"$ref\":\"http://nowhere/x\"}", DRAFT_2020_12);
        assertThrows(IllegalArgumentException.class, () -> schema.validate(parserFor("1 ")));
    }

    private static boolean valid(
        String schema,
        String instance,
        JsonSchema.Draft draft)
    {
        return JsonSchema.of(schema, draft).validate(parserFor(instance + " "));
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        return JsonEx.createParser(in);
    }
}
