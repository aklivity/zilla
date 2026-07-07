/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;

public class McpConditionMatcherTest
{
    @Test
    public void shouldMatchToolWithinAllowSet()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("create_*", "get_*"))
            .resources(asList("repo://*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertEquals("create_pr", matcher.match("tools", "github__create_pr"));
        assertEquals("get_user", matcher.match("tools", "github__get_user"));
        assertEquals("repo://aklivity/zilla", matcher.match("resources", "github+repo://aklivity/zilla"));
    }

    @Test
    public void shouldNotMatchToolOutsideAllowSet()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("create_*", "get_*"))
            .resources(asList("repo://*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertNull(matcher.match("tools", "github__delete_repo"));
        assertNull(matcher.match("resources", "github+issue://1"));
    }

    @Test
    public void shouldMatchAnyToolWhenWildcardGiven()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertEquals("create_pr", matcher.match("tools", "github__create_pr"));
        assertEquals("delete_repo", matcher.match("tools", "github__delete_repo"));
    }

    @Test
    public void shouldMatchNoToolWhenAllowSetEmpty()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("slack")
            .tools(emptyList())
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertNull(matcher.match("tools", "slack__post_message"));
    }

    @Test
    public void shouldServeCapabilityIndependentOfAllowSet()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("slack")
            .tools(emptyList())
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertTrue(matcher.serves("tools"));
        assertFalse(matcher.serves("prompts"));
        assertFalse(matcher.serves("resources"));
    }

    @Test
    public void shouldServeAllCapabilitiesWhenNoAllowSetsGiven()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertTrue(matcher.serves("tools"));
        assertTrue(matcher.serves("prompts"));
        assertTrue(matcher.serves("resources"));
        assertEquals("create_pr", matcher.match("tools", "github__create_pr"));
    }

    @Test
    public void shouldAdmitNamesByAllowSet()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("get_*"))
            .resources(asList("*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertTrue(matcher.admits("tools", "get_user"));
        assertFalse(matcher.admits("tools", "delete_repo"));
        assertTrue(matcher.admits("resources", "anything"));
        assertFalse(matcher.admits("prompts", "anything"));
    }

    @Test
    public void shouldReportFilteringByAllowSet()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("get_*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertTrue(matcher.filters("tools"));
        assertFalse(matcher.filters("resources"));
        assertFalse(matcher.filters("prompts"));
    }

    @Test
    public void shouldReportFilteringWhenWildcardGiven()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .tools(asList("*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertTrue(matcher.filters("tools"));
    }

    @Test
    public void shouldNotMatchUnservedCapability()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
            .toolkit("github")
            .resources(asList("*"))
            .build();
        McpConditionMatcher matcher = new McpConditionMatcher(condition);

        assertNull(matcher.match("tools", "github__create_pr"));
        assertFalse(matcher.serves("tools"));
    }
}
