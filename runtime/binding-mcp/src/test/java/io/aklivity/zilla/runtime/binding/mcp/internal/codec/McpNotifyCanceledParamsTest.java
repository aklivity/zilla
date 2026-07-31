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

public class McpNotifyCanceledParamsTest
{
    @Test
    public void shouldDeserializeWellFormedParams()
    {
        McpNotifyCanceledParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"requestId\":\"1\",\"reason\":\"cancelled by user\"}"),
            McpNotifyCanceledParams.class);

        assertThat(params.requestId, notNullValue());
        assertThat(params.reason, notNullValue());
        assertThat(params.reason.getValueType(), is(JsonValue.ValueType.STRING));
    }

    @Test
    public void shouldDeserializeMissingReason()
    {
        McpNotifyCanceledParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"requestId\":\"1\"}"),
            McpNotifyCanceledParams.class);

        assertThat(params.requestId, notNullValue());
        assertThat(params.reason, nullValue());
    }

    @Test
    public void shouldNotThrowWhenReasonIsObject()
    {
        McpNotifyCanceledParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"requestId\":\"1\",\"reason\":{\"nested\":true}}"),
            McpNotifyCanceledParams.class);

        assertThat(params.reason, notNullValue());
        assertThat(params.reason.getValueType(), is(JsonValue.ValueType.OBJECT));
    }

    @Test
    public void shouldNotThrowWhenReasonIsArray()
    {
        McpNotifyCanceledParams params = JsonbBuilder.create().fromJson(
            new StringReader("{\"requestId\":\"1\",\"reason\":[1,2,3]}"),
            McpNotifyCanceledParams.class);

        assertThat(params.reason, notNullValue());
        assertThat(params.reason.getValueType(), is(JsonValue.ValueType.ARRAY));
    }
}
