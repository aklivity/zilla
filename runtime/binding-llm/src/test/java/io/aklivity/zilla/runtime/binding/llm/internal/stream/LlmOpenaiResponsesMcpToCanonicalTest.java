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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmOpenaiResponsesMcpToCanonicalTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmOpenaiResponsesMcpToCanonical transform = new LlmOpenaiResponsesMcpToCanonical();

    @Test
    public void shouldTransformServerToCanonicalMembers()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp'," +
                "'authorization':'token-1','allowed_tools':['ask_question','read_wiki']}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'authorization':'token-1','tools':['ask_question','read_wiki']}"));
    }

    @Test
    public void shouldTransformTypeThatFollowsMembers()
    {
        assertTransforms(transform,
            json("{'server_url':'https://mcp.example/mcp','server_label':'deepwiki','type':'mcp'}"),
            json("{'type':'mcp','url':'https://mcp.example/mcp','label':'deepwiki'}"));
    }

    @Test
    public void shouldTransformEmptyAllowedTools()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp','allowed_tools':[]}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','tools':[]}"));
    }

    @Test
    public void shouldPrefixAllowedToolsFilter()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp'," +
                "'allowed_tools':{'tool_names':['ask_question'],'read_only':true}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'openai/rs:allowed_tools':{'tool_names':['ask_question'],'read_only':true}}"));
    }

    @Test
    public void shouldPrefixNativeOnlyMembersAtTheirPosition()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','connector_id':'connector_dropbox','require_approval':'never'," +
                "'server_description':'Docs','defer_loading':true,'headers':{'X-Org':'acme'},'extra':[1,{'a':null}]}"),
            json("{'type':'mcp','label':'deepwiki','openai/rs:connector_id':'connector_dropbox'," +
                "'openai/rs:require_approval':'never','openai/rs:server_description':'Docs'," +
                "'openai/rs:defer_loading':true,'openai/rs:headers':{'X-Org':'acme'},'openai/rs:extra':[1,{'a':null}]}"));
    }

    @Test
    public void shouldPrefixRequireApprovalObject()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp'," +
                "'require_approval':{'never':{'tool_names':['read_wiki']}}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'openai/rs:require_approval':{'never':{'tool_names':['read_wiki']}}}"));
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/" + LONG +
                "','server_description':'" + LONG + "'}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/" + LONG +
                "','openai/rs:server_description':'" + LONG + "'}"));
    }

    @Test
    public void shouldRejectOtherToolType()
    {
        assertRejects(transform, json("{'type':'web_search','server_label':'deepwiki'}"));
        assertRejects(transform, json("{'server_label':'deepwiki','type':'web_search'}"));
    }

    @Test
    public void shouldRejectMissingType()
    {
        assertRejects(transform, json("{'server_label':'deepwiki','server_url':'https://mcp.example/mcp'}"));
    }
}
