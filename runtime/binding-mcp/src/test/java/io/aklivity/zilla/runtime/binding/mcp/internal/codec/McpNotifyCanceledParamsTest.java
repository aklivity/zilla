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

public class McpNotifyCanceledParamsTest
{
    @Test
    public void shouldParseWellFormedParams()
    {
        McpNotifyCanceledParams params = McpNotifyCanceledParams.parse(
            parse("{\"requestId\":\"1\",\"reason\":\"cancelled by user\"}"));

        assertThat(params.requestId(), notNullValue());
        assertThat(params.reason(), equalTo("cancelled by user"));
    }

    @Test
    public void shouldParseMissingFields()
    {
        McpNotifyCanceledParams params = McpNotifyCanceledParams.parse(parse("{}"));

        assertThat(params.requestId(), nullValue());
        assertThat(params.reason(), nullValue());
    }

    @Test
    public void shouldFallBackWhenReasonIsObject()
    {
        McpNotifyCanceledParams params = McpNotifyCanceledParams.parse(
            parse("{\"requestId\":\"1\",\"reason\":{\"nested\":true}}"));

        assertThat(params.requestId(), notNullValue());
        assertThat(params.reason(), nullValue());
    }

    @Test
    public void shouldFallBackWhenReasonIsArray()
    {
        McpNotifyCanceledParams params = McpNotifyCanceledParams.parse(
            parse("{\"requestId\":\"1\",\"reason\":[1,2,3]}"));

        assertThat(params.reason(), nullValue());
    }

    private static JsonObject parse(
        String json)
    {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
