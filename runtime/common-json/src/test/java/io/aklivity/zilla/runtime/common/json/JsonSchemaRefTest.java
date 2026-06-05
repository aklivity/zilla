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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaRefTest
{
    @Test
    void shouldResolveLocalDefsRef()
    {
        String schema = "{\"$defs\":{\"pos\":{\"type\":\"integer\",\"minimum\":0}},\"$ref\":\"#/$defs/pos\"}";
        assertTrue(valid(schema, "5"));
        assertFalse(valid(schema, "-1"));
        assertFalse(valid(schema, "\"x\""));
    }

    @Test
    void shouldResolveLegacyDefinitionsRef()
    {
        String schema = "{\"definitions\":{\"s\":{\"type\":\"string\"}},\"$ref\":\"#/definitions/s\"}";
        assertTrue(valid(schema, "\"x\""));
        assertFalse(valid(schema, "5"));
    }

    @Test
    void shouldResolveRefInsideProperty()
    {
        String schema = "{\"$defs\":{\"pos\":{\"type\":\"integer\",\"minimum\":0}}," +
            "\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\"#/$defs/pos\"}}}";
        assertTrue(valid(schema, "{\"a\":5}"));
        assertFalse(valid(schema, "{\"a\":-1}"));
    }

    @Test
    void shouldResolveRefInsideItems()
    {
        String schema = "{\"$defs\":{\"s\":{\"type\":\"string\"}}," +
            "\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/s\"}}";
        assertTrue(valid(schema, "[\"x\",\"y\"]"));
        assertFalse(valid(schema, "[\"x\",1]"));
    }

    @Test
    void shouldResolveRecursiveRef()
    {
        String schema = "{\"$defs\":{\"node\":{\"type\":\"object\"," +
            "\"properties\":{\"v\":{\"type\":\"integer\"},\"next\":{\"$ref\":\"#/$defs/node\"}}," +
            "\"required\":[\"v\"]}},\"$ref\":\"#/$defs/node\"}";
        assertTrue(valid(schema, "{\"v\":1,\"next\":{\"v\":2}}"));
        assertTrue(valid(schema, "{\"v\":1,\"next\":{\"v\":2,\"next\":{\"v\":3}}}"));
        assertFalse(valid(schema, "{\"v\":1,\"next\":{\"x\":2}}"));
        assertFalse(valid(schema, "{\"v\":\"x\"}"));
    }

    @Test
    void shouldResolveNonLocalRefViaResolver()
    {
        RefResolver resolver = ref -> "urn:pos".equals(ref) ? "{\"type\":\"integer\",\"minimum\":0}" : null;
        JsonSchema schema = JsonSchema.of("{\"$ref\":\"urn:pos\"}", resolver);
        assertTrue(schema.validate(parserFor("5 ")));
        assertFalse(schema.validate(parserFor("-1 ")));
    }

    @Test
    void shouldCollectRefs()
    {
        String schema = "{\"properties\":{\"a\":{\"$ref\":\"#/$defs/x\"}," +
            "\"b\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/y\"}}}}";
        assertEquals(List.of("#/$defs/x", "#/$defs/y"), List.copyOf(refs(schema)));
    }

    private static boolean valid(
        String schema,
        String instance)
    {
        return JsonSchema.of(schema).validate(parserFor(instance + " "));
    }

    private static Set<String> refs(
        String schema)
    {
        return JsonSchema.collectRefs(schema);
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
