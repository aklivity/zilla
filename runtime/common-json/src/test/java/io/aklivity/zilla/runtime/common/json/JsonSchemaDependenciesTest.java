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

class JsonSchemaDependenciesTest
{
    @Test
    void shouldValidateDependentRequired()
    {
        String schema = "{\"dependencies\":{\"a\":[\"b\"]}}";
        assertTrue(valid(schema, "{\"a\":1,\"b\":2}"));
        assertFalse(valid(schema, "{\"a\":1}"));
        assertTrue(valid(schema, "{\"b\":2}"));
        assertTrue(valid(schema, "{}"));
    }

    @Test
    void shouldValidateDependentSchema()
    {
        String schema = "{\"dependencies\":{\"cc\":{\"required\":[\"addr\"]}}}";
        assertTrue(valid(schema, "{\"cc\":1,\"addr\":\"x\"}"));
        assertFalse(valid(schema, "{\"cc\":1}"));
        assertTrue(valid(schema, "{\"x\":1}"));
    }

    @Test
    void shouldValidateMultipleDependencies()
    {
        String schema = "{\"dependencies\":{\"a\":[\"b\"],\"c\":[\"d\"]}}";
        assertTrue(valid(schema, "{\"a\":1,\"b\":2,\"c\":3,\"d\":4}"));
        assertFalse(valid(schema, "{\"a\":1,\"b\":2,\"c\":3}"));
    }

    @Test
    void shouldIgnoreDependenciesForNonObject()
    {
        assertTrue(valid("{\"dependencies\":{\"a\":[\"b\"]}}", "5"));
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
