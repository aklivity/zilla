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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AsyncapiFragmentTest
{
    @Test
    public void shouldParseObjectPreservingKeyOrder()
    {
        String json = "{\"type\":\"object\",\"description\":\"Order event\"," +
            "\"properties\":{\"id\":{\"type\":\"string\"},\"timestamp\":{\"type\":\"integer\"}," +
            "\"amount\":{\"type\":\"number\"}}}";

        Object parsed = AsyncapiFragment.parse(json);

        assertTrue(parsed instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) parsed;
        assertEquals(List.of("type", "description", "properties"), List.copyOf(object.keySet()));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) object.get("properties");
        assertEquals(List.of("id", "timestamp", "amount"), List.copyOf(properties.keySet()));
    }

    @Test
    public void shouldParseArray()
    {
        Object parsed = AsyncapiFragment.parse("[\"delete\",\"compact\"]");

        assertEquals(List.of("delete", "compact"), parsed);
    }

    @Test
    public void shouldParsePrimitives()
    {
        assertEquals("hello", AsyncapiFragment.parse("\"hello\""));
        assertEquals(42, AsyncapiFragment.parse("42"));
        assertEquals(Boolean.TRUE, AsyncapiFragment.parse("true"));
    }

    @Test
    public void shouldRoundTripNestedMapUnchanged()
    {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        nested.put("b", 2);

        Object parsed = AsyncapiFragment.parse("{\"a\":1,\"b\":2}");

        assertEquals(nested, parsed);
    }
}
