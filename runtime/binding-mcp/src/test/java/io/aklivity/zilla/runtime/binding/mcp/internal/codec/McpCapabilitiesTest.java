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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.Optional;

import jakarta.json.Json;

import org.junit.Test;

public class McpCapabilitiesTest
{
    @Test
    public void shouldCreateClientCapabilities()
    {
        McpClientCapabilities capabilities = new McpClientCapabilities();
        capabilities.experimental = Json.createObjectBuilder()
            .add("feature", true)
            .build();

        capabilities.roots = capabilities.new Roots();
        capabilities.roots.listChanged = true;

        capabilities.sampling = capabilities.new Sampling();
        capabilities.sampling.context = new McpClientCapabilities.Sampling.Context();
        capabilities.sampling.tools = new McpClientCapabilities.Sampling.Tools();

        capabilities.elicitation = capabilities.new Elicitation();
        capabilities.elicitation.form = new McpClientCapabilities.Elicitation.Form();
        capabilities.elicitation.url = new McpClientCapabilities.Elicitation.Url();

        capabilities.tasks = capabilities.new Tasks();
        capabilities.tasks.list = new McpClientCapabilities.Tasks.List();
        capabilities.tasks.cancel = new McpClientCapabilities.Tasks.Cancel();
        capabilities.tasks.requests = capabilities.tasks.new Requests();
        capabilities.tasks.requests.sampling = capabilities.tasks.requests.new Sampling();
        capabilities.tasks.requests.sampling.createMessage =
            new McpClientCapabilities.Tasks.Requests.Sampling.CreateMessage();
        capabilities.tasks.requests.elicitation = capabilities.tasks.requests.new Elicitation();
        capabilities.tasks.requests.elicitation.create =
            new McpClientCapabilities.Tasks.Requests.Elicitation.Create();

        assertThat(capabilities.experimental.getBoolean("feature"), is(true));
        assertThat(capabilities.roots.listChanged, is(true));
        assertThat(capabilities.sampling.context, notNullValue());
        assertThat(capabilities.sampling.tools, notNullValue());
        assertThat(capabilities.elicitation.form, notNullValue());
        assertThat(capabilities.elicitation.url, notNullValue());
        assertThat(capabilities.tasks.list, notNullValue());
        assertThat(capabilities.tasks.cancel, notNullValue());
        assertThat(capabilities.tasks.requests.sampling.createMessage, notNullValue());
        assertThat(capabilities.tasks.requests.elicitation.create, notNullValue());
    }

    @Test
    public void shouldCreateInitializeRequestParams()
    {
        McpImplementationInfo implementation = new McpImplementationInfo();
        implementation.name = "zilla";
        implementation.version = "1.0";

        McpInitializeRequestParams params = new McpInitializeRequestParams();
        params.protocolVersion = "2025-03-26";
        params.capabilities = new McpClientCapabilities();
        params.clientInfo = implementation;

        assertThat(params.protocolVersion, equalTo("2025-03-26"));
        assertThat(params.capabilities, notNullValue());
        assertThat(params.clientInfo.name, equalTo("zilla"));
        assertThat(params.clientInfo.version, equalTo("1.0"));
    }

    @Test
    public void shouldCreateServerCapabilities()
    {
        McpServerCapabilities.Logging logging = new McpServerCapabilities.Logging();
        McpServerCapabilities.Completions completions = new McpServerCapabilities.Completions();
        McpServerCapabilities.Prompts prompts = new McpServerCapabilities.Prompts(true);
        McpServerCapabilities.Resources resources = new McpServerCapabilities.Resources(true, true);
        McpServerCapabilities.Tools tools = new McpServerCapabilities.Tools(true);

        McpServerCapabilities capabilities = new McpServerCapabilities(
            Optional.of(Map.of("feature", "enabled")),
            Optional.of(logging),
            Optional.of(completions),
            Optional.of(prompts),
            Optional.of(resources),
            Optional.of(tools));

        assertThat(capabilities.experimental().orElseThrow().get("feature"), equalTo("enabled"));
        assertThat(capabilities.logging().orElseThrow(), equalTo(logging));
        assertThat(capabilities.completions().orElseThrow(), equalTo(completions));
        assertThat(capabilities.prompts().orElseThrow().listChanged(), is(true));
        assertThat(capabilities.resources().orElseThrow().subscribe(), is(true));
        assertThat(capabilities.resources().orElseThrow().listChanged(), is(true));
        assertThat(capabilities.tools().orElseThrow().listChanged(), is(true));
    }

    @Test
    public void shouldCreateServerTaskCapabilities()
    {
        McpServerCapabilities.Tasks.List list = new McpServerCapabilities.Tasks.List();
        McpServerCapabilities.Tasks.Cancel cancel = new McpServerCapabilities.Tasks.Cancel();
        McpServerCapabilities.Tasks.Requests.Tools.Call call =
            new McpServerCapabilities.Tasks.Requests.Tools.Call();
        McpServerCapabilities.Tasks.Requests.Tools tools =
            new McpServerCapabilities.Tasks.Requests.Tools(Optional.of(call));
        McpServerCapabilities.Tasks.Requests requests =
            new McpServerCapabilities.Tasks.Requests(Optional.of(tools));
        McpServerCapabilities.Tasks tasks =
            new McpServerCapabilities.Tasks(Optional.of(list), Optional.of(cancel), Optional.of(requests));

        assertThat(tasks.list().orElseThrow(), equalTo(list));
        assertThat(tasks.cancel().orElseThrow(), equalTo(cancel));
        assertThat(tasks.requests().orElseThrow().tools().orElseThrow().call().orElseThrow(), equalTo(call));
    }
}
