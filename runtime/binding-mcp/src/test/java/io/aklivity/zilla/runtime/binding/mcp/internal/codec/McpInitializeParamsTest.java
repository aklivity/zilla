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
package io.aklivity.zilla.runtime.binding.mcp.internal.codec;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

public class McpInitializeParamsTest
{
    @Test
    public void shouldParseWellFormedParams()
    {
        McpInitializeParams params = McpInitializeParams.parse(parse("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}"));

        assertThat(params, notNullValue());
        assertThat(params.protocolVersion(), equalTo("2025-11-25"));
        assertThat(params.capabilities(), notNullValue());
    }

    @Test
    public void shouldParseMissingFields()
    {
        McpInitializeParams params = McpInitializeParams.parse(parse("{}"));

        assertThat(params, notNullValue());
        assertThat(params.protocolVersion(), nullValue());
        assertThat(params.capabilities(), nullValue());
    }

    @Test
    public void shouldFallBackWhenProtocolVersionIsObject()
    {
        McpInitializeParams params = McpInitializeParams.parse(
            parse("{\"protocolVersion\":{\"nested\":true},\"capabilities\":{}}"));

        assertThat(params, notNullValue());
        assertThat(params.protocolVersion(), nullValue());
    }

    @Test
    public void shouldFallBackWhenProtocolVersionIsArray()
    {
        McpInitializeParams params = McpInitializeParams.parse(parse("{\"protocolVersion\":[1,2],\"capabilities\":{}}"));

        assertThat(params, notNullValue());
        assertThat(params.protocolVersion(), nullValue());
    }

    @Test
    public void shouldFallBackWhenProtocolVersionIsNumber()
    {
        McpInitializeParams params = McpInitializeParams.parse(parse("{\"protocolVersion\":42,\"capabilities\":{}}"));

        assertThat(params, notNullValue());
        assertThat(params.protocolVersion(), nullValue());
    }

    @Test
    public void shouldRejectWhenCapabilitiesIsString()
    {
        McpInitializeParams params = McpInitializeParams.parse(
            parse("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":\"not-an-object\"}"));

        assertThat(params, nullValue());
    }

    @Test
    public void shouldRejectWhenCapabilitiesIsArray()
    {
        McpInitializeParams params = McpInitializeParams.parse(
            parse("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":[1,2,3]}"));

        assertThat(params, nullValue());
    }

    @Test
    public void shouldRejectWhenCapabilitiesIsNumber()
    {
        McpInitializeParams params = McpInitializeParams.parse(
            parse("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":42}"));

        assertThat(params, nullValue());
    }

    private static JsonObject parse(
        String json)
    {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
