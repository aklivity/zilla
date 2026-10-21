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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmAnthropicBlockMembersTest
{
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(UNLIMITED, "5m", "1h");

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("toolu_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);
    private final Consumer<LlmSectionAttributes> reference = attributes ->
        attributes.source(LlmSectionAttributes.SOURCE_REFERENCE);

    @Test
    public void shouldDecodeToolCallCacheControlAsBreakpoint()
    {
        assertTransforms(new LlmAnthropicToolCallToCanonical(),
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'a':1}," +
                "'cache_control':{'type':'ephemeral','ttl':'1h'}}"),
            json("{'arguments':{'a':1},'prompt_cache_breakpoint':{'mode':'explicit','ttl':'1h'}}"));
    }

    @Test
    public void shouldDecodeCacheControlWithoutTtlAndDropUnknownMembers()
    {
        assertTransforms(new LlmAnthropicToolCallToCanonical(),
            json("{'cache_control':{'x':[1,{'a':null}],'type':'ephemeral'},'type':'tool_use','id':'toolu_1','name':'ping'," +
                "'input':{'a':1}}"),
            json("{'prompt_cache_breakpoint':{'mode':'explicit'},'arguments':{'a':1}}"));
    }

    @Test
    public void shouldDropCacheControlThatIsNotEphemeral()
    {
        assertTransforms(new LlmAnthropicToolCallToCanonical(),
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'a':1},'cache_control':{'type':'persistent'}}"),
            json("{'arguments':{'a':1}}"));
        assertTransforms(new LlmAnthropicToolCallToCanonical(),
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'a':1},'cache_control':null}"),
            json("{'arguments':{'a':1}}"));
    }

    @Test
    public void shouldDecodeToolDefinitionCacheControlAndStrict()
    {
        assertTransforms(new LlmAnthropicToolDefinitionToCanonical(),
            json("{'name':'ping','strict':true,'cache_control':{'type':'ephemeral','ttl':'5m'}," +
                "'input_schema':{'type':'object'}}"),
            json("{'type':'function','function':{'name':'ping','strict':true," +
                "'prompt_cache_breakpoint':{'mode':'explicit','ttl':'5m'},'parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldEncodeToolDefinitionStrictAndBreakpoint()
    {
        assertTransforms(new LlmAnthropicToolDefinitionFromCanonical(true, breakpoints),
            json("{'type':'function','function':{'name':'ping','strict':false," +
                "'prompt_cache_breakpoint':{'mode':'explicit','ttl':'30m'},'parameters':{'type':'object'}}}"),
            json("{'name':'ping','strict':false,'cache_control':{'type':'ephemeral','ttl':'1h'}," +
                "'input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldRoundTripToolDefinitionStrict()
    {
        assertRoundTrips(new LlmAnthropicToolDefinitionToCanonical(),
            new LlmAnthropicToolDefinitionFromCanonical(false, breakpoints),
            json("{'name':'ping','description':'Ping','input_schema':{'type':'object'},'strict':true}"));
    }

    @Test
    public void shouldEncodeToolCallBreakpointWithClampedTtl()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("tool_use", false, breakpoints), both, null,
            json("{'prompt_cache_breakpoint':{'mode':'explicit','ttl':'24h'},'arguments':{'a':1}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','cache_control':{'type':'ephemeral','ttl':'1h'}," +
                "'input':{'a':1}}"));
    }

    @Test
    public void shouldEncodeToolCallBreakpointWithoutTtl()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("tool_use", false, breakpoints), both, null,
            json("{'arguments':{'a':1},'prompt_cache_breakpoint':{'mode':'explicit'}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'a':1}," +
                "'cache_control':{'type':'ephemeral'}}"));
    }

    @Test
    public void shouldDropBreakpointThatIsNotExplicit()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("tool_use", false, breakpoints), both, null,
            json("{'arguments':{'a':1},'prompt_cache_breakpoint':{'mode':'auto','ttl':'1h'}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'a':1}}"));
    }

    @Test
    public void shouldDropBreakpointWithoutBreakpoints()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("tool_use", false), both, null,
            json("{'arguments':{'a':1},'prompt_cache_breakpoint':{'mode':'explicit'}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'a':1}}"));
        assertTransforms(new LlmAnthropicToolDefinitionFromCanonical(false),
            json("{'type':'function','function':{'name':'ping','prompt_cache_breakpoint':{'mode':'explicit'}}}"),
            json("{'name':'ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldDecodeDocumentCitationsAndBreakpoint()
    {
        assertTransforms(LlmAnthropicMediaToCanonical.document(),
            json("{'type':'document','citations':{'enabled':true},'source':{'type':'file','file_id':'file_1'}," +
                "'cache_control':{'type':'ephemeral','ttl':'1h'}}"),
            json("{'type':'file','x:citations':{'enabled':true},'file':{'file_id':'file_1'}," +
                "'prompt_cache_breakpoint':{'mode':'explicit','ttl':'1h'}}"));
    }

    @Test
    public void shouldNotCarryCitationsThatAreNotEnabled()
    {
        assertTransforms(LlmAnthropicMediaToCanonical.document(),
            json("{'type':'document','citations':{'enabled':false},'source':{'type':'file','file_id':'file_1'}}"),
            json("{'type':'file','file':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldEncodeDocumentCitationsAndBreakpoint()
    {
        final LlmAnthropicMediaFromCanonical document = LlmAnthropicMediaFromCanonical.document(false, breakpoints);

        assertTransforms(document, reference, null,
            json("{'type':'file','x:citations':{'enabled':true},'file':{'file_id':'file_1'}," +
                "'prompt_cache_breakpoint':{'mode':'explicit','ttl':'1h'}}"),
            json("{'type':'document','citations':{'enabled':true},'source':{'type':'file','file_id':'file_1'}," +
                "'cache_control':{'type':'ephemeral','ttl':'1h'}}"));
        assertThat(document.cited(), is(true));
    }

    @Test
    public void shouldNotEncodeCitationsThatAreNotEnabled()
    {
        final LlmAnthropicMediaFromCanonical document = LlmAnthropicMediaFromCanonical.document(false, breakpoints);

        assertTransforms(document, reference, null,
            json("{'type':'file','x:citations':{'enabled':false},'file':{'file_id':'file_1'}}"),
            json("{'type':'document','source':{'type':'file','file_id':'file_1'}}"));
        assertThat(document.cited(), is(false));
    }

    @Test
    public void shouldDropCitationsFromImage()
    {
        final LlmAnthropicMediaFromCanonical image = LlmAnthropicMediaFromCanonical.image(false, breakpoints);

        assertTransforms(image, reference, null,
            json("{'type':'image_url','x:citations':{'enabled':true},'image_url':{'file_id':'file_1'}," +
                "'prompt_cache_breakpoint':{'mode':'explicit'}}"),
            json("{'type':'image','source':{'type':'file','file_id':'file_1'},'cache_control':{'type':'ephemeral'}}"));
        assertThat(image.cited(), is(false));
    }

    @Test
    public void shouldRoundTripDocumentCitations()
    {
        assertRoundTrips(LlmAnthropicMediaToCanonical.document(),
            LlmAnthropicMediaFromCanonical.document(true, breakpoints), reference,
            json("{'type':'document','source':{'type':'file','file_id':'file_1'},'citations':{'enabled':true}," +
                "'cache_control':{'type':'ephemeral'}}"));
    }

    @Test
    public void shouldDecodeThinkingSignatureAndRedactedData()
    {
        assertTransforms(new LlmAnthropicReasoningToCanonical(),
            json("{'type':'thinking','thinking':'t','signature':'sig'}"),
            json("{'text':'t','x:signature':'sig'}"));
        assertTransforms(new LlmAnthropicReasoningToCanonical(),
            json("{'type':'redacted_thinking','data':'opaque'}"),
            json("{'x:redacted_data':'opaque'}"));
    }

    @Test
    public void shouldEncodeSignatureAndRedactedDataAcrossDialects()
    {
        assertTransforms(new LlmAnthropicReasoningFromCanonical(false),
            json("{'text':'t','x:signature':'sig'}"),
            json("{'type':'thinking','thinking':'t','signature':'sig'}"));
        assertTransforms(new LlmAnthropicReasoningFromCanonical(false),
            json("{'x:redacted_data':'opaque'}"),
            json("{'type':'redacted_thinking','data':'opaque'}"));
    }

    @Test
    public void shouldDropOtherSharedMembersFromReasoning()
    {
        assertTransforms(new LlmAnthropicReasoningFromCanonical(false),
            json("{'text':'t','x:citations':{'enabled':true},'x:signature':'sig','x:other':1}"),
            json("{'type':'thinking','thinking':'t','signature':'sig'}"));
    }
}
