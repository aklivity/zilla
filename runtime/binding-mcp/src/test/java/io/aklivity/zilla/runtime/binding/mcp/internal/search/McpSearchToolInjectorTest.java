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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class McpSearchToolInjectorTest
{
    @Test
    public void shouldInjectIntoEmptyArray()
    {
        byte[] json = "{\"tools\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] tool = "{\"name\":\"zilla__search_tools\"}".getBytes(StandardCharsets.UTF_8);

        byte[] result = McpSearchToolInjector.inject(json, tool);

        assertThat(new String(result, StandardCharsets.UTF_8),
            equalTo("{\"tools\":[{\"name\":\"zilla__search_tools\"}]}"));
    }

    @Test
    public void shouldInjectIntoNonEmptyArrayWithComma()
    {
        byte[] json = "{\"tools\":[{\"name\":\"get_weather\"}]}".getBytes(StandardCharsets.UTF_8);
        byte[] tool = "{\"name\":\"zilla__search_tools\"}".getBytes(StandardCharsets.UTF_8);

        byte[] result = McpSearchToolInjector.inject(json, tool);

        assertThat(new String(result, StandardCharsets.UTF_8),
            equalTo("{\"tools\":[{\"name\":\"get_weather\"},{\"name\":\"zilla__search_tools\"}]}"));
    }

    @Test
    public void shouldInjectAfterMultipleExistingTools()
    {
        byte[] json = "{\"tools\":[{\"name\":\"a\"},{\"name\":\"b\"}]}".getBytes(StandardCharsets.UTF_8);
        byte[] tool = "{\"name\":\"c\"}".getBytes(StandardCharsets.UTF_8);

        byte[] result = McpSearchToolInjector.inject(json, tool);

        assertThat(new String(result, StandardCharsets.UTF_8),
            equalTo("{\"tools\":[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]}"));
    }

    @Test
    public void shouldReturnOriginalWhenToolBytesNull()
    {
        byte[] json = "{\"tools\":[]}".getBytes(StandardCharsets.UTF_8);

        byte[] result = McpSearchToolInjector.inject(json, null);

        assertThat(result, sameInstance(json));
    }
}
