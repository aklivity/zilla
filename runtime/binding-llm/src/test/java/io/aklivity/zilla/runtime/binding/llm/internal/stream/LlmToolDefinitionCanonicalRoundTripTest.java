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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmToolDefinitionCanonicalRoundTripTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmAnthropicToolDefinitionToCanonical toCanonical = new LlmAnthropicToolDefinitionToCanonical();
    private final LlmAnthropicToolDefinitionFromCanonical fromCanonical = new LlmAnthropicToolDefinitionFromCanonical(true);

    @Test
    public void shouldRoundTripAnthropicToolDefinition()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'name':'get_weather','description':'Get the weather'," +
                "'input_schema':{'type':'object','properties':{'city':{'type':'string'}}}}"));
    }

    @Test
    public void shouldRoundTripCacheControlBeforeCanonicalMembers()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'cache_control':{'type':'ephemeral'},'name':'ping','description':'Ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldRoundTripCacheControlBetweenCanonicalMembers()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'name':'ping','cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'description':'Ping'," +
                "'input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldRoundTripCacheControlAfterCanonicalMembers()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'name':'ping','description':'Ping','input_schema':{'type':'object'},'cache_control':{'type':'ephemeral'}}"));
    }

    @Test
    public void shouldRoundTripNameThatFollowsSchema()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'description':'Ping','input_schema':{'type':'object'},'name':'ping'}"));
    }

    @Test
    public void shouldRoundTripLongValuesAndManyNativeOnlyMembers()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'name':'ping','note':'" + LONG + "','count':12345,'tags':[1,2],'strict':true,'gap':null," +
                "'description':'" + LONG + "','input_schema':{'type':'object','properties':{'a':{'type':'string'," +
                "'description':'" + LONG + "'}}}}"));
    }
}
