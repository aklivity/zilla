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

public class LlmOpenaiResponsesCitationsFromCanonicalTest
{
    private final LlmOpenaiResponsesCitationsFromCanonical same = LlmOpenaiResponsesCitationsFromCanonical.array(true);
    private final LlmOpenaiResponsesCitationsFromCanonical foreign = LlmOpenaiResponsesCitationsFromCanonical.array(false);
    private final LlmOpenaiResponsesCitationsFromCanonical sameObject = LlmOpenaiResponsesCitationsFromCanonical.object(true);

    @Test
    public void shouldFlattenUrlCitation()
    {
        assertTransforms(same,
            json("[{'type':'url_citation','url_citation':{'start_index':0,'end_index':4,'url':'https://example.com'," +
                "'title':'Example'}}]"),
            json("[{'type':'url_citation','start_index':0,'end_index':4,'url':'https://example.com','title':'Example'}]"));
    }

    @Test
    public void shouldFlattenUrlCitationObject()
    {
        assertTransforms(sameObject,
            json("{'type':'url_citation','url_citation':{'url':'https://example.com'}}"),
            json("{'type':'url_citation','url':'https://example.com'}"));
    }

    @Test
    public void shouldRestoreOtherCitations()
    {
        assertTransforms(same,
            json("[{'openai:type':'file_citation','openai:file_id':'file_1','openai:filename':'a.pdf','openai:index':0}]"),
            json("[{'type':'file_citation','file_id':'file_1','filename':'a.pdf','index':0}]"));
    }

    @Test
    public void shouldRestoreExtraMembers()
    {
        assertTransforms(same,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'},'openai:extra':true}]"),
            json("[{'type':'url_citation','url':'https://example.com','extra':true}]"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(same,
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}," +
                "'anthropic:cited_text':'blue','anthropic:encrypted_index':'idx'}]"),
            json("[{'type':'url_citation','url':'https://example.com'}]"));
    }

    @Test
    public void shouldSkipCitationsWithoutHome()
    {
        assertTransforms(foreign,
            json("[{'openai:type':'file_citation','openai:file_id':'file_1'}," +
                "{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"),
            json("[{'type':'url_citation','url':'https://example.com'}]"));
    }

    @Test
    public void shouldRoundTripCitations()
    {
        assertRoundTrips(LlmOpenaiResponsesCitationsToCanonical.array(), same,
            json("[{'type':'url_citation','end_index':247,'start_index':143,'title':'T','url':'https://example.com'}," +
                "{'type':'file_citation','file_id':'file_1','filename':'a.pdf','index':0}," +
                "{'type':'file_path','file_id':'file_2','index':3}]"));
    }
}
