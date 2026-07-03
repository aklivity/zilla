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
import io.aklivity.zilla.runtime.common.agrona.io.DirectBufferInputStreamEx;

class JsonSchemaCombinatorTest
{
    @Test
    void shouldValidateAllOf()
    {
        String schema = "{\"allOf\":[" +
            "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"}},\"required\":[\"a\"]}," +
            "{\"properties\":{\"b\":{\"type\":\"string\"}},\"required\":[\"b\"]}]}";
        assertTrue(valid(schema, "{\"a\":1,\"b\":\"x\"}"));
        assertFalse(valid(schema, "{\"a\":1}"));
    }

    @Test
    void shouldValidateAnyOf()
    {
        String schema = "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}";
        assertTrue(valid(schema, "\"x\""));
        assertTrue(valid(schema, "5"));
        assertFalse(valid(schema, "true"));
    }

    @Test
    void shouldValidateOneOfExactlyOne()
    {
        String schema = "{\"oneOf\":[{\"type\":\"integer\"},{\"type\":\"string\"}]}";
        assertTrue(valid(schema, "5"));
        assertTrue(valid(schema, "\"x\""));
        assertFalse(valid(schema, "true"));
    }

    @Test
    void shouldRejectOneOfWhenMultipleMatch()
    {
        String schema = "{\"oneOf\":[{\"minimum\":0},{\"maximum\":100}]}";
        assertTrue(valid(schema, "-5"));
        assertTrue(valid(schema, "150"));
        assertFalse(valid(schema, "50"));
    }

    @Test
    void shouldValidateNot()
    {
        String schema = "{\"not\":{\"type\":\"string\"}}";
        assertTrue(valid(schema, "5"));
        assertFalse(valid(schema, "\"x\""));
    }

    @Test
    void shouldValidateIfThenElse()
    {
        String schema = "{\"if\":{\"required\":[\"kind\"],\"properties\":{\"kind\":{\"const\":\"a\"}}}," +
            "\"then\":{\"required\":[\"x\"]},\"else\":{\"required\":[\"y\"]}}";
        assertTrue(valid(schema, "{\"kind\":\"a\",\"x\":1}"));
        assertFalse(valid(schema, "{\"kind\":\"a\"}"));
        assertTrue(valid(schema, "{\"kind\":\"b\",\"y\":1}"));
        assertFalse(valid(schema, "{\"kind\":\"b\"}"));
    }

    @Test
    void shouldValidateIfThenWithoutElse()
    {
        String schema = "{\"if\":{\"required\":[\"k\"],\"properties\":{\"k\":{\"const\":1}}},\"then\":{\"required\":[\"x\"]}}";
        assertTrue(valid(schema, "{\"k\":1,\"x\":2}"));
        assertFalse(valid(schema, "{\"k\":1}"));
        assertTrue(valid(schema, "{\"k\":2}"));
        assertTrue(valid(schema, "{}"));
    }

    @Test
    void shouldValidateCombinatorWithDirectKeywords()
    {
        String schema = "{\"type\":\"object\",\"required\":[\"id\"],\"anyOf\":[" +
            "{\"properties\":{\"a\":{\"type\":\"integer\"}},\"required\":[\"a\"]}," +
            "{\"properties\":{\"b\":{\"type\":\"string\"}},\"required\":[\"b\"]}]}";
        assertTrue(valid(schema, "{\"id\":1,\"a\":2}"));
        assertFalse(valid(schema, "{\"a\":2}"));
        assertFalse(valid(schema, "{\"id\":1}"));
    }

    @Test
    void shouldValidateCombinatorInsideProperty()
    {
        String schema = "{\"properties\":{\"val\":{\"oneOf\":[{\"type\":\"integer\"},{\"type\":\"string\"}]}}}";
        assertTrue(valid(schema, "{\"val\":1}"));
        assertTrue(valid(schema, "{\"val\":\"x\"}"));
        assertFalse(valid(schema, "{\"val\":true}"));
    }

    @Test
    void shouldValidateCombinatorInsideItems()
    {
        String schema = "{\"type\":\"array\",\"items\":{\"anyOf\":[{\"type\":\"integer\"},{\"type\":\"null\"}]}}";
        assertTrue(valid(schema, "[1,null,2]"));
        assertFalse(valid(schema, "[1,\"x\"]"));
    }

    @Test
    void shouldValidateNestedCombinators()
    {
        String schema = "{\"allOf\":[{\"anyOf\":[{\"type\":\"integer\"},{\"type\":\"string\"}]}]}";
        assertTrue(valid(schema, "5"));
        assertTrue(valid(schema, "\"x\""));
        assertFalse(valid(schema, "true"));
    }

    @Test
    void shouldValidateBooleanSubschemasUnderNot()
    {
        assertFalse(valid("{\"not\":{}}", "5"));
        assertTrue(valid("{\"not\":false}", "5"));
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
