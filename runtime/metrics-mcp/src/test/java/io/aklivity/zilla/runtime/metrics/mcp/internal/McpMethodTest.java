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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.function.Consumer;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW;

public class McpMethodTest
{
    @Test
    public void shouldResolveToolsCallFields()
    {
        McpBeginExFW beginEx = beginEx(b -> b.toolsCall(t -> t.sessionId("s1").name("weather").contentLength(-1)));

        assertThat(McpMethod.TOOLS_CALL.resolve("tool", beginEx), equalTo("weather"));
        assertThat(McpMethod.TOOLS_CALL.resolve("session", beginEx), equalTo("s1"));
        assertThat(McpMethod.TOOLS_CALL.resolve("method", beginEx), equalTo("tools.call"));
        assertThat(McpMethod.TOOLS_CALL.resolve("resource", beginEx), nullValue());
        assertThat(McpMethod.TOOLS_CALL.resolve("prompt", beginEx), nullValue());
        assertThat(McpMethod.TOOLS_CALL.resolve("unknown", beginEx), nullValue());
    }

    @Test
    public void shouldResolveResourcesReadFields()
    {
        McpBeginExFW beginEx = beginEx(b -> b.resourcesRead(r -> r.sessionId("s2").uri("config://app").contentLength(-1)));

        assertThat(McpMethod.RESOURCES_READ.resolve("resource", beginEx), equalTo("config://app"));
        assertThat(McpMethod.RESOURCES_READ.resolve("session", beginEx), equalTo("s2"));
        assertThat(McpMethod.RESOURCES_READ.resolve("tool", beginEx), nullValue());
    }

    @Test
    public void shouldResolvePromptsGetFields()
    {
        McpBeginExFW beginEx = beginEx(b -> b.promptsGet(p -> p.sessionId("s3").name("greet").contentLength(-1)));

        assertThat(McpMethod.PROMPTS_GET.resolve("prompt", beginEx), equalTo("greet"));
        assertThat(McpMethod.PROMPTS_GET.resolve("session", beginEx), equalTo("s3"));
    }

    @Test
    public void shouldResolveSessionForListAndLifecycleMethods()
    {
        assertThat(McpMethod.INITIALIZE.resolve("session",
            beginEx(b -> b.lifecycle(l -> l.sessionId("s0").capabilities(0)))), equalTo("s0"));
        assertThat(McpMethod.TOOLS_LIST.resolve("session",
            beginEx(b -> b.toolsList(t -> t.sessionId("s4")))), equalTo("s4"));
        assertThat(McpMethod.PROMPTS_LIST.resolve("session",
            beginEx(b -> b.promptsList(p -> p.sessionId("s5")))), equalTo("s5"));
        assertThat(McpMethod.RESOURCES_LIST.resolve("session",
            beginEx(b -> b.resourcesList(r -> r.sessionId("s6")))), equalTo("s6"));
    }

    @Test
    public void shouldReturnMetadata()
    {
        assertThat(McpMethod.TOOLS_CALL.kind(), equalTo(McpBeginExFW.KIND_TOOLS_CALL));
        assertThat(McpMethod.TOOLS_CALL.segment(), equalTo("tools.call"));
        assertThat(McpMethod.TOOLS_CALL.summary(), equalTo("tool invocations"));
    }

    private static McpBeginExFW beginEx(
        Consumer<McpBeginExFW.Builder> mutator)
    {
        McpBeginExFW.Builder builder = new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256)
            .typeId(0);
        mutator.accept(builder);
        return builder.build();
    }
}
