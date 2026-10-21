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

public class LlmOpenaiCitationsFromCanonicalTest
{
    private final LlmOpenaiCitationsFromCanonical transform = new LlmOpenaiCitationsFromCanonical(true);
    private final LlmOpenaiCitationsFromCanonical foreign = new LlmOpenaiCitationsFromCanonical(false);

    @Test
    public void shouldKeepUrlCitation()
    {
        assertTransforms(transform,
            json("[{'type':'url_citation','url_citation':{'start_index':0,'end_index':4," +
                "'url':'https://example.com','title':'Example'}}]"),
            json("[{'type':'url_citation','url_citation':{'start_index':0,'end_index':4," +
                "'url':'https://example.com','title':'Example'}}]"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(transform,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}," +
                "'anthropic:cited_text':'blue','anthropic:encrypted_index':'idx'}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldSkipCitationWithoutUrlType()
    {
        assertTransforms(transform,
            json("[{'anthropic:type':'char_location','anthropic:cited_text':'blue'}," +
                "{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldSkipEveryCitationWithoutUrlType()
    {
        assertTransforms(transform,
            json("[{'anthropic:type':'char_location','anthropic:cited_text':'blue'}]"),
            json("[]"));
    }

    @Test
    public void shouldDropOwnPrefixedMemberAcrossDialects()
    {
        assertTransforms(foreign,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com','openai:extra':1}}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldPassUnprefixedMemberAcrossDialects()
    {
        assertTransforms(foreign,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com','start_index':0}}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com','start_index':0}}]"));
    }
}
