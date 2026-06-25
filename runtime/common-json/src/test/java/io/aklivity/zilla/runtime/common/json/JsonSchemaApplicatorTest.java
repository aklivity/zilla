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
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonSchemaApplicatorTest
{
    @Test
    void shouldValidatePatternProperties()
    {
        String schema = "{\"patternProperties\":{\"^x\":{\"type\":\"integer\"}}}";
        assertTrue(valid(schema, "{\"x1\":5}"));
        assertFalse(valid(schema, "{\"x1\":\"a\"}"));
        assertTrue(valid(schema, "{\"y\":\"a\"}"));
    }

    @Test
    void shouldApplyPropertiesAndPatternPropertiesTogether()
    {
        String schema = "{\"properties\":{\"xa\":{\"maximum\":10}},\"patternProperties\":{\"^x\":{\"type\":\"integer\"}}}";
        assertTrue(valid(schema, "{\"xa\":5}"));
        assertFalse(valid(schema, "{\"xa\":50}"));
        assertFalse(valid(schema, "{\"xa\":1.5}"));
    }

    @Test
    void shouldHonourAdditionalPropertiesWithPatternProperties()
    {
        String schema = "{\"patternProperties\":{\"^x\":{}},\"additionalProperties\":false}";
        assertTrue(valid(schema, "{\"x1\":1}"));
        assertFalse(valid(schema, "{\"y\":1}"));
    }

    @Test
    void shouldValidatePropertyNamesPattern()
    {
        String schema = "{\"propertyNames\":{\"pattern\":\"^[a-z]+$\"}}";
        assertTrue(valid(schema, "{\"abc\":1}"));
        assertFalse(valid(schema, "{\"Abc\":1}"));
    }

    @Test
    void shouldValidatePropertyNamesMaxLength()
    {
        String schema = "{\"propertyNames\":{\"maxLength\":3}}";
        assertTrue(valid(schema, "{\"ab\":1}"));
        assertFalse(valid(schema, "{\"abcd\":1}"));
    }

    private static boolean valid(
        String schema,
        String instance)
    {
        return JsonSchema.of(schema).validate(parserFor(instance + " "));
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
