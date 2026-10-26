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

public class LlmOpenaiResponsesCitationsToCanonicalTest
{
    private final LlmOpenaiResponsesCitationsToCanonical array = LlmOpenaiResponsesCitationsToCanonical.array();
    private final LlmOpenaiResponsesCitationsToCanonical object = LlmOpenaiResponsesCitationsToCanonical.object();

    @Test
    public void shouldNestUrlCitation()
    {
        assertTransforms(array,
            json("[{'type':'url_citation','end_index':4,'start_index':0,'title':'Example','url':'https://example.com'}]"),
            json("[{'type':'url_citation','url_citation':{'end_index':4,'start_index':0,'title':'Example'," +
                "'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldNestUrlCitationObject()
    {
        assertTransforms(object,
            json("{'type':'url_citation','end_index':4,'start_index':0,'title':'Example','url':'https://example.com'}"),
            json("{'type':'url_citation','url_citation':{'end_index':4,'start_index':0,'title':'Example'," +
                "'url':'https://example.com'}}"));
    }

    @Test
    public void shouldDropNullTitle()
    {
        assertTransforms(array,
            json("[{'type':'url_citation','url':'https://example.com','title':null}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'}}]"));
    }

    @Test
    public void shouldPrefixMembersAfterNested()
    {
        assertTransforms(array,
            json("[{'type':'url_citation','url':'https://example.com','extra':true}]"),
            json("[{'type':'url_citation','url_citation':{'url':'https://example.com'},'openai/rs:extra':true}]"));
    }

    @Test
    public void shouldPrefixEveryMemberOfOtherCitation()
    {
        assertTransforms(array,
            json("[{'type':'file_citation','file_id':'file_1','filename':'a.pdf','index':0}," +
                "{'type':'file_path','file_id':'file_2','index':3}]"),
            json("[{'openai/rs:type':'file_citation','openai/rs:file_id':'file_1','openai/rs:filename':'a.pdf'," +
                "'openai/rs:index':0}," +
                "{'openai/rs:type':'file_path','openai/rs:file_id':'file_2','openai/rs:index':3}]"));
    }

    @Test
    public void shouldPrefixOtherCitationObject()
    {
        assertTransforms(object,
            json("{'type':'container_file_citation','container_id':'c','end_index':5,'file_id':'f','start_index':1}"),
            json("{'openai/rs:type':'container_file_citation','openai/rs:container_id':'c','openai/rs:end_index':5," +
                "'openai/rs:file_id':'f','openai/rs:start_index':1}"));
    }

    @Test
    public void shouldConvertEmptyCitations()
    {
        assertTransforms(array, json("[]"), json("[]"));
    }

    @Test
    public void shouldConvertTypeOnlyCitation()
    {
        assertTransforms(array, json("[{'type':'url_citation'}]"), json("[{'type':'url_citation'}]"));
    }

    @Test
    public void shouldRejectCitationWithoutLeadingType()
    {
        assertRejects(array, json("[{'url':'https://example.com','type':'url_citation'}]"));
    }

    @Test
    public void shouldRejectUrlMembersSplitByOtherMember()
    {
        assertRejects(array, json("[{'type':'url_citation','url':'https://example.com','extra':1,'title':'T'}]"));
    }
}
