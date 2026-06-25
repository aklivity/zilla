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

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonSchemaUniqueItemsTest
{
    @Test
    void shouldValidateUniqueScalars()
    {
        String schema = "{\"uniqueItems\":true}";
        assertTrue(valid(schema, "[1,2,3]"));
        assertFalse(valid(schema, "[1,2,1]"));
        assertTrue(valid(schema, "[]"));
    }

    @Test
    void shouldDistinguishNumberFromString()
    {
        assertTrue(valid("{\"uniqueItems\":true}", "[1,\"1\"]"));
    }

    @Test
    void shouldTreatEqualNumbersAsDuplicates()
    {
        assertFalse(valid("{\"uniqueItems\":true}", "[1,1.0]"));
    }

    @Test
    void shouldCompareObjectsIndependentOfKeyOrder()
    {
        String schema = "{\"uniqueItems\":true}";
        assertFalse(valid(schema, "[{\"a\":1,\"b\":2},{\"b\":2,\"a\":1}]"));
        assertTrue(valid(schema, "[{\"a\":1},{\"a\":2}]"));
    }

    @Test
    void shouldNotEnforceWhenUniqueItemsFalse()
    {
        assertTrue(valid("{\"uniqueItems\":false}", "[1,1]"));
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
        return StreamingJson.createParser(in);
    }
}
