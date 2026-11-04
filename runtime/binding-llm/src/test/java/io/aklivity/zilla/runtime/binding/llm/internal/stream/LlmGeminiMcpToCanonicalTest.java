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

public class LlmGeminiMcpToCanonicalTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmGeminiMcpToCanonical transform = new LlmGeminiMcpToCanonical();

    @Test
    public void shouldTransformServerToCanonicalMembers()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Bearer token-1'}}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp','authorization':'token-1'}"));
    }

    @Test
    public void shouldTransformServerWithoutHeaders()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'}"));
    }

    @Test
    public void shouldTransformNameThatFollowsTransport()
    {
        assertTransforms(transform,
            json("{'streamableHttpTransport':{'url':'https://mcp.example/mcp'},'name':'deepwiki'}"),
            json("{'type':'mcp','url':'https://mcp.example/mcp','label':'deepwiki'}"));
    }

    @Test
    public void shouldWriteAuthorizationAfterOtherMembers()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'headers':{'Authorization':'Bearer token-1','X-Org':'acme'}," +
                "'url':'https://mcp.example/mcp','timeout':'30s','sseReadTimeout':'300s','terminateOnClose':true}}"),
            json("{'type':'mcp','label':'deepwiki','gemini:headers':{'X-Org':'acme'},'url':'https://mcp.example/mcp'," +
                "'gemini:timeout':'30s','gemini:sseReadTimeout':'300s','gemini:terminateOnClose':true," +
                "'authorization':'token-1'}"));
    }

    @Test
    public void shouldKeepAuthorizationHeaderThatIsNotBearer()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'headers':{'Authorization':'Basic dXNlcjpwYXNz','X-Org':'acme'}}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/mcp'," +
                "'gemini:headers':{'Authorization':'Basic dXNlcjpwYXNz','X-Org':'acme'}}"));
    }

    @Test
    public void shouldPrefixNativeOnlyMembers()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','note':[1,{'a':null}],'streamableHttpTransport':{'url':'https://mcp.example/mcp'," +
                "'extra':true}}"),
            json("{'type':'mcp','label':'deepwiki','gemini:note':[1,{'a':null}],'url':'https://mcp.example/mcp'," +
                "'gemini:extra':true}"));
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'name':'deepwiki','streamableHttpTransport':{'url':'https://mcp.example/" + LONG +
                "','headers':{'Authorization':'Bearer " + LONG + "','X-Org':'" + LONG + "'}}}"),
            json("{'type':'mcp','label':'deepwiki','url':'https://mcp.example/" + LONG +
                "','gemini:headers':{'X-Org':'" + LONG + "'},'authorization':'" + LONG + "'}"));
    }

    @Test
    public void shouldRejectServerWithoutTransport()
    {
        assertRejects(transform, json("{'name':'deepwiki'}"));
    }
}
