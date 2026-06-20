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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaArrayTest
{
    @Test
    void shouldValidateTupleItems()
    {
        String schema = "{\"type\":\"array\",\"items\":[{\"type\":\"integer\"},{\"type\":\"string\"}]}";
        assertTrue(valid(schema, "[1,\"a\"]"));
        assertFalse(valid(schema, "[1,2]"));
        assertTrue(valid(schema, "[1]"));
        assertTrue(valid(schema, "[1,\"a\",true]"));
    }

    @Test
    void shouldRejectExtraTupleItemsWhenAdditionalItemsFalse()
    {
        String schema = "{\"items\":[{\"type\":\"integer\"}],\"additionalItems\":false}";
        assertTrue(valid(schema, "[1]"));
        assertFalse(valid(schema, "[1,2]"));
    }

    @Test
    void shouldValidateAdditionalItemsAgainstSchema()
    {
        String schema = "{\"items\":[{\"type\":\"integer\"}],\"additionalItems\":{\"type\":\"string\"}}";
        assertTrue(valid(schema, "[1,\"a\",\"b\"]"));
        assertFalse(valid(schema, "[1,2]"));
    }

    @Test
    void shouldValidateContains()
    {
        String schema = "{\"type\":\"array\",\"contains\":{\"type\":\"integer\"}}";
        assertTrue(valid(schema, "[\"a\",1,\"b\"]"));
        assertFalse(valid(schema, "[\"a\",\"b\"]"));
        assertFalse(valid(schema, "[]"));
    }

    @Test
    void shouldValidateContainsConst()
    {
        String schema = "{\"contains\":{\"const\":5}}";
        assertTrue(valid(schema, "[1,5,2]"));
        assertFalse(valid(schema, "[1,2]"));
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
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return JsonEx.createParser(in);
    }
}
