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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicCitationsFromCanonicalTest
{
    private final LlmAnthropicCitationsFromCanonical transform = new LlmAnthropicCitationsFromCanonical(true);
    private final LlmAnthropicCitationsFromCanonical foreign = new LlmAnthropicCitationsFromCanonical(false);

    @Test
    public void shouldConvertUrlCitation()
    {
        assertTransforms(transform,
            json("[{'type':'url_citation','url_citation':{'start_index':0,'end_index':4," +
                "'url':'https://example.com','title':'Example'}}]"),
            json("[{'type':'web_search_result_location','url':'https://example.com','title':'Example'}]"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMembers()
    {
        assertTransforms(transform,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}," +
                "'anthropic:cited_text':'blue','openai:extra':1}]"),
            json("[{'type':'web_search_result_location','url':'https://example.com','cited_text':'blue'}]"));
    }

    @Test
    public void shouldRestoreOtherCitation()
    {
        assertTransforms(transform,
            json("[{'anthropic:type':'char_location','anthropic:cited_text':'blue','anthropic:document_index':0}]"),
            json("[{'type':'char_location','cited_text':'blue','document_index':0}]"));
    }

    @Test
    public void shouldSkipCitationOfUnknownType()
    {
        assertTransforms(transform,
            json("[{'type':'file_citation','file_citation':{'file_id':'f1'}}," +
                "{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"),
            json("[{'type':'web_search_result_location','url':'https://example.com'}]"));
    }

    @Test
    public void shouldSkipCitationOfOtherDialect()
    {
        assertTransforms(transform,
            json("[{'openai:type':'x','openai:value':1}]"),
            json("[]"));
    }

    @Test
    public void shouldDropOwnPrefixedMemberAcrossDialects()
    {
        assertTransforms(foreign,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'},'anthropic:cited_text':'blue'}]"),
            json("[{'type':'web_search_result_location','url':'https://example.com'}]"));
    }

    @Test
    public void shouldPassUnprefixedMemberAcrossDialects()
    {
        assertTransforms(foreign,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'},'kept':true}]"),
            json("[{'type':'web_search_result_location','url':'https://example.com','kept':true}]"));
    }
}
