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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbBuilder;

import org.junit.Test;

public class McpInitializeParamsTest
{
    @Test
    public void shouldDeserializeWellFormedParams()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}"),
            McpInitializeParams.class);

        assertThat(params.protocolVersion, notNullValue());
        assertThat(params.protocolVersion.getValueType(), is(JsonValue.ValueType.STRING));
        assertThat(params.capabilities, notNullValue());
        assertThat(params.capabilities.getValueType(), is(JsonValue.ValueType.OBJECT));
    }

    @Test
    public void shouldDeserializeMissingFields()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{}"),
            McpInitializeParams.class);

        assertThat(params.protocolVersion, nullValue());
        assertThat(params.capabilities, nullValue());
    }

    @Test
    public void shouldNotThrowWhenProtocolVersionIsObject()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":{\"nested\":true},\"capabilities\":{}}"),
            McpInitializeParams.class);

        assertThat(params.protocolVersion, notNullValue());
        assertThat(params.protocolVersion.getValueType(), is(JsonValue.ValueType.OBJECT));
    }

    @Test
    public void shouldNotThrowWhenProtocolVersionIsArray()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":[1,2],\"capabilities\":{}}"),
            McpInitializeParams.class);

        assertThat(params.protocolVersion, notNullValue());
        assertThat(params.protocolVersion.getValueType(), is(JsonValue.ValueType.ARRAY));
    }

    @Test
    public void shouldNotThrowWhenCapabilitiesIsString()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":\"not-an-object\"}"),
            McpInitializeParams.class);

        assertThat(params.capabilities, notNullValue());
        assertThat(params.capabilities.getValueType(), is(JsonValue.ValueType.STRING));
    }

    @Test
    public void shouldNotThrowWhenCapabilitiesIsArray()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":[1,2,3]}"),
            McpInitializeParams.class);

        assertThat(params.capabilities, notNullValue());
        assertThat(params.capabilities.getValueType(), is(JsonValue.ValueType.ARRAY));
    }

    @Test
    public void shouldNotThrowWhenCapabilitiesIsNumber()
    {
        McpInitializeParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"protocolVersion\":\"2025-11-25\",\"capabilities\":42}"),
            McpInitializeParams.class);

        assertThat(params.capabilities, notNullValue());
        assertThat(params.capabilities.getValueType(), is(JsonValue.ValueType.NUMBER));
    }
}
