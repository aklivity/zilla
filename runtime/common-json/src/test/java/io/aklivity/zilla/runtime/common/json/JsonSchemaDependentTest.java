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

class JsonSchemaDependentTest
{
    @Test
    void shouldValidateDependentRequiredKeyword()
    {
        String schema = "{\"dependentRequired\":{\"credit_card\":[\"billing_address\"]}}";
        assertTrue(valid(schema, "{}"));
        assertTrue(valid(schema, "{\"billing_address\":\"x\"}"));
        assertTrue(valid(schema, "{\"credit_card\":1,\"billing_address\":\"x\"}"));
        assertFalse(valid(schema, "{\"credit_card\":1}"));
    }

    @Test
    void shouldValidateDependentSchemasKeyword()
    {
        String schema = "{\"dependentSchemas\":{\"credit_card\":" +
            "{\"properties\":{\"billing_address\":{\"type\":\"string\"}}," +
            "\"required\":[\"billing_address\"]}}}";
        assertTrue(valid(schema, "{}"));
        assertTrue(valid(schema, "{\"credit_card\":1,\"billing_address\":\"x\"}"));
        assertFalse(valid(schema, "{\"credit_card\":1}"));
        assertFalse(valid(schema, "{\"credit_card\":1,\"billing_address\":5}"));
    }

    @Test
    void shouldMergeDependenciesWithStandaloneKeywords()
    {
        String schema = "{" +
            "\"dependencies\":{\"a\":[\"b\"]}," +
            "\"dependentRequired\":{\"c\":[\"d\"]}," +
            "\"dependentSchemas\":{\"e\":{\"required\":[\"f\"]}}}";
        assertTrue(valid(schema, "{\"a\":1,\"b\":1}"));
        assertFalse(valid(schema, "{\"a\":1}"));
        assertFalse(valid(schema, "{\"c\":1}"));
        assertFalse(valid(schema, "{\"e\":1}"));
        assertTrue(valid(schema, "{\"c\":1,\"d\":1,\"e\":1,\"f\":1}"));
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
