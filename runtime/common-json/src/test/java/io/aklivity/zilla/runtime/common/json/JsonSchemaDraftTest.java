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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaDraftTest
{
    @Test
    void shouldUseBooleanExclusiveMinimumInDraft04()
    {
        String schema = "{\"minimum\":5,\"exclusiveMinimum\":true}";
        assertFalse(valid(schema, "5", JsonSchema.Draft.DRAFT_04));
        assertTrue(valid(schema, "6", JsonSchema.Draft.DRAFT_04));
    }

    @Test
    void shouldRespectBooleanExclusiveMinimumFalseInDraft04()
    {
        String schema = "{\"minimum\":5,\"exclusiveMinimum\":false}";
        assertTrue(valid(schema, "5", JsonSchema.Draft.DRAFT_04));
        assertFalse(valid(schema, "4", JsonSchema.Draft.DRAFT_04));
    }

    @Test
    void shouldUseBooleanExclusiveMaximumInDraft04()
    {
        String schema = "{\"maximum\":10,\"exclusiveMaximum\":true}";
        assertFalse(valid(schema, "10", JsonSchema.Draft.DRAFT_04));
        assertTrue(valid(schema, "9", JsonSchema.Draft.DRAFT_04));
    }

    @Test
    void shouldRejectNumericExclusiveMinimumInDraft04()
    {
        assertThrows(UnsupportedOperationException.class,
            () -> JsonSchema.of("{\"exclusiveMinimum\":5}", JsonSchema.Draft.DRAFT_04));
    }

    @Test
    void shouldRejectBooleanExclusiveMinimumInDraft06()
    {
        assertThrows(UnsupportedOperationException.class,
            () -> JsonSchema.of("{\"exclusiveMinimum\":true}", JsonSchema.Draft.DRAFT_06));
    }

    @Test
    void shouldAcceptNumericExclusiveMinimumInDraft06()
    {
        String schema = "{\"exclusiveMinimum\":5}";
        assertFalse(valid(schema, "5", JsonSchema.Draft.DRAFT_06));
        assertTrue(valid(schema, "6", JsonSchema.Draft.DRAFT_06));
    }

    @Test
    void shouldDetectDraft04FromMetaSchemaUri()
    {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-04/schema#\"," +
            "\"minimum\":5,\"exclusiveMinimum\":true}";
        assertFalse(valid(schema, "5", null));
        assertTrue(valid(schema, "6", null));
    }

    @Test
    void shouldDetectDraft06FromMetaSchemaUri()
    {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-06/schema#\"," +
            "\"exclusiveMinimum\":5}";
        assertFalse(valid(schema, "5", null));
        assertTrue(valid(schema, "6", null));
    }

    @Test
    void shouldDefaultToDraft07ForNumericExclusive()
    {
        String schema = "{\"exclusiveMinimum\":5}";
        assertFalse(valid(schema, "5", null));
        assertTrue(valid(schema, "6", null));
    }

    @Test
    void shouldSupportDraft04DefinitionsRef()
    {
        String schema = "{\"definitions\":{\"int\":{\"type\":\"integer\"}}," +
            "\"$ref\":\"#/definitions/int\"}";
        assertTrue(valid(schema, "42", JsonSchema.Draft.DRAFT_04));
        assertFalse(valid(schema, "\"x\"", JsonSchema.Draft.DRAFT_04));
    }

    @Test
    void shouldSupportDraft07DefsRef()
    {
        String schema = "{\"$defs\":{\"int\":{\"type\":\"integer\"}}," +
            "\"$ref\":\"#/$defs/int\"}";
        assertTrue(valid(schema, "42", JsonSchema.Draft.DRAFT_07));
        assertFalse(valid(schema, "\"x\"", JsonSchema.Draft.DRAFT_07));
    }

    private static boolean valid(
        String schema,
        String instance,
        JsonSchema.Draft draft)
    {
        JsonSchema compiled = draft != null
            ? JsonSchema.of(schema, draft)
            : JsonSchema.of(schema);
        return compiled.validate(parserFor(instance + " "));
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
