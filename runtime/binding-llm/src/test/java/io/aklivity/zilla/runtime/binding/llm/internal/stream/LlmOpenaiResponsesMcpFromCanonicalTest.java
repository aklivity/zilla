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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmOpenaiResponsesMcpFromCanonicalTest
{
    private final LlmOpenaiResponsesMcpFromCanonical transform = new LlmOpenaiResponsesMcpFromCanonical("openai/rs");
    private final LlmOpenaiResponsesMcpToCanonical decoded = new LlmOpenaiResponsesMcpToCanonical();

    @Test
    public void shouldWriteCanonicalMembersAsServer()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'authorization':'token-1','tools':['ask_question','read_wiki']}"),
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp'," +
                "'authorization':'token-1','allowed_tools':['ask_question','read_wiki']}"));
    }

    @Test
    public void shouldWriteEmptyTools()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','tools':[]}"),
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp','allowed_tools':[]}"));
    }

    @Test
    public void shouldWriteNativeMembersWithoutPrefix()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'dropbox','openai/rs:connector_id':'connector_dropbox'," +
                "'openai/rs:require_approval':'never','openai/rs:allowed_tools':{'tool_names':['a']}}"),
            json("{'type':'mcp','server_label':'dropbox','connector_id':'connector_dropbox'," +
                "'require_approval':'never','allowed_tools':{'tool_names':['a']}}"));
    }

    @Test
    public void shouldDropMembersOfAnotherDialect()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'anthropic:defer_loading':true,'gemini:timeout':'30s'}"),
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp'}"));
    }

    @Test
    public void shouldRoundTripServer()
    {
        assertRoundTrips(decoded, transform,
            json("{'type':'mcp','server_label':'deepwiki','server_url':'https://mcp.example/mcp','authorization':'token-1'," +
                "'allowed_tools':['ask_question'],'require_approval':'never','server_description':'Docs'}"));
    }
}
