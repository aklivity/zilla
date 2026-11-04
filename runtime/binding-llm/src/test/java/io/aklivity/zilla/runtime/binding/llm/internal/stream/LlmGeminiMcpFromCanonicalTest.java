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

public class LlmGeminiMcpFromCanonicalTest
{
    private final LlmGeminiMcpFromCanonical transform = new LlmGeminiMcpFromCanonical(true);

    @Test
    public void shouldWriteCanonicalMembersAsServer()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','authorization':'token-1'}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Bearer token-1'}}}"));
    }

    @Test
    public void shouldWriteServerWithoutAuthorization()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'}}"));
    }

    @Test
    public void shouldWriteServerWhateverTheOrderOfMembers()
    {
        assertTransforms(transform,
            json("{'authorization':'token-1','url':'https://mcp.example/mcp','type':'mcp','label':'deepwiki'}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Bearer token-1'}}}"));
    }

    @Test
    public void shouldWriteNativeMembersWithoutPrefix()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','authorization':'token-1'," +
                "'gemini:headers':{'X-Org':'acme'},'gemini:timeout':'30s','gemini:sseReadTimeout':'300s'," +
                "'gemini:terminateOnClose':true}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Bearer token-1','X-Org':'acme'},'timeout':'30s','sseReadTimeout':'300s'," +
                "'terminateOnClose':true}}"));
    }

    @Test
    public void shouldWriteAuthorizationHeaderAndFormat()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','authorization':'token-1'," +
                "'gemini:authorization_header':'X-Api-Key','gemini:authorization_format':'%s'}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'X-Api-Key':'token-1'}}}"));
    }

    @Test
    public void shouldDropMembersOfAnotherDialect()
    {
        assertTransforms(transform,
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','openai/rs:server_description':'Docs'," +
                "'anthropic:defer_loading':true}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'}}"));
    }

    @Test
    public void shouldDropNativeMembersWhenAnotherDialectWrites()
    {
        assertTransforms(new LlmGeminiMcpFromCanonical(false),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','gemini:timeout':'30s'}"),
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'}}"));
    }

    @Test
    public void shouldRoundTripServer()
    {
        assertRoundTrips(new LlmGeminiMcpToCanonical(), transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Bearer token-1','X-Org':'acme'},'timeout':'30s'}}"));
    }
}
