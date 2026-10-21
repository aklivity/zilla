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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicCitationsToCanonicalTest
{
    private final LlmAnthropicCitationsToCanonical transform = new LlmAnthropicCitationsToCanonical();

    @Test
    public void shouldConvertUrlCitation()
    {
        assertTransforms(transform,
            json("[{'type':'web_search_result_location','url':'https://example.com','title':'Example'," +
                "'encrypted_index':'idx','cited_text':'blue'}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com','title':'Example'}," +
                "'anthropic:encrypted_index':'idx','anthropic:cited_text':'blue'}]"));
    }

    @Test
    public void shouldConvertUrlCitationWithNativeMemberFirst()
    {
        assertTransforms(transform,
            json("[{'type':'web_search_result_location','cited_text':'blue','url':'https://example.com'," +
                "'title':'Example'}]"),
            json("[{'type':'url_citation','anthropic:cited_text':'blue'," +
                "'url_citation':{'url':'https://example.com','title':'Example'}}]"));
    }

    @Test
    public void shouldConvertUrlCitationWithNullTitle()
    {
        assertTransforms(transform,
            json("[{'type':'web_search_result_location','url':'https://example.com','title':null}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldPrefixEveryMemberOfOtherCitation()
    {
        assertTransforms(transform,
            json("[{'type':'char_location','cited_text':'blue','document_index':0,'start_char_index':0," +
                "'end_char_index':4}]"),
            json("[{'anthropic:type':'char_location','anthropic:cited_text':'blue','anthropic:document_index':0," +
                "'anthropic:start_char_index':0,'anthropic:end_char_index':4}]"));
    }

    @Test
    public void shouldConvertMixedCitations()
    {
        assertTransforms(transform,
            json("[{'type':'char_location','cited_text':'a'},{'type':'web_search_result_location'," +
                "'url':'https://example.com'}]"),
            json("[{'anthropic:type':'char_location','anthropic:cited_text':'a'}," +
                "{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldConvertEmptyCitations()
    {
        assertTransforms(transform, json("[]"), json("[]"));
    }

    @Test
    public void shouldRejectCitationWithoutLeadingType()
    {
        assertRejects(transform, json("[{'url':'https://example.com','type':'web_search_result_location'}]"));
    }

    @Test
    public void shouldRejectUrlMembersSplitByNativeMember()
    {
        assertRejects(transform,
            json("[{'type':'web_search_result_location','url':'https://example.com','cited_text':'blue'," +
                "'title':'Example'}]"));
    }

    @Test
    public void shouldRoundTripUrlAndOtherCitations()
    {
        assertRoundTrips(transform, new LlmAnthropicCitationsFromCanonical(true),
            json("[{'type':'web_search_result_location','url':'https://example.com','title':'Example'," +
                "'encrypted_index':'idx','cited_text':'blue'},{'type':'char_location','cited_text':'a'," +
                "'document_index':0}]"));
    }
}
