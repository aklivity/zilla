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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class McpToolTitleDisambiguatorTest
{
    @Test
    public void shouldLeaveTitlesUnchangedWhenNoCollision()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__get_time\",\"title\":\"Get Time\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}", fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__get_time\",\"title\":\"Get Time\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldDisambiguateTitlesCollidingAcrossToolkits()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"bluesky__send_message\",\"title\":\"Send Message (bluesky)\"}", fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message (quartz)\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldOnlyRewriteItemsWithCollidingTitleWithinAFragment()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__",
            "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}," +
            "{\"name\":\"bluesky__get_time\",\"title\":\"Get Time\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals(
            "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message (bluesky)\"}," +
            "{\"name\":\"bluesky__get_time\",\"title\":\"Get Time\"}",
            fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message (quartz)\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldNotDisambiguateTitleCollidingOnlyWithinSameToolkit()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__",
            "{\"name\":\"bluesky__send_message_a\",\"title\":\"Send Message\"}," +
            "{\"name\":\"bluesky__send_message_b\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals(
            "{\"name\":\"bluesky__send_message_a\",\"title\":\"Send Message\"}," +
            "{\"name\":\"bluesky__send_message_b\",\"title\":\"Send Message\"}",
            fragments.get("bluesky__"));
    }

    @Test
    public void shouldLeaveTitlesUnchangedWhenOnlyOneRouteHasAToolkit()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("", "{\"name\":\"send_message\",\"title\":\"Send Message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = new LinkedHashMap<>();
        toolkits.put("", null);
        toolkits.put("quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"send_message\",\"title\":\"Send Message\"}", fragments.get(""));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldLeaveUnprefixedRouteTitleUnchangedWhileDisambiguatingToolkitCollision()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("", "{\"name\":\"send_message\",\"title\":\"Send Message\"}");
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = new LinkedHashMap<>();
        toolkits.put("", null);
        toolkits.put("bluesky__", "bluesky");
        toolkits.put("quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"send_message\",\"title\":\"Send Message\"}", fragments.get(""));
        assertEquals("{\"name\":\"bluesky__send_message\",\"title\":\"Send Message (bluesky)\"}", fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message (quartz)\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldLeaveItemsWithoutTitleUnchanged()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"bluesky__send_message\"}", fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldFallBackToAnnotationsTitleWhenTopLevelTitleAbsent()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__",
            "{\"name\":\"bluesky__send_message\",\"annotations\":{\"title\":\"Send Message\",\"readOnlyHint\":true}}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message\"}}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals(
            "{\"name\":\"bluesky__send_message\"," +
            "\"annotations\":{\"title\":\"Send Message (bluesky)\",\"readOnlyHint\":true}}",
            fragments.get("bluesky__"));
        assertEquals(
            "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message (quartz)\"}}",
            fragments.get("quartz__"));
    }

    @Test
    public void shouldPreferTopLevelTitleOverAnnotationsTitleForCollisionAndRewrite()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__",
            "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"," +
            "\"annotations\":{\"title\":\"Different Title\"}}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"title\":\"Send Message\"}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals(
            "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message (bluesky)\"," +
            "\"annotations\":{\"title\":\"Different Title\"}}",
            fragments.get("bluesky__"));
        assertEquals("{\"name\":\"quartz__send_message\",\"title\":\"Send Message (quartz)\"}", fragments.get("quartz__"));
    }

    @Test
    public void shouldDisambiguateAcrossTopLevelTitleAndAnnotationsTitleFallback()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\",\"title\":\"Send Message\"}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message\"}}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"bluesky__send_message\",\"title\":\"Send Message (bluesky)\"}", fragments.get("bluesky__"));
        assertEquals(
            "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message (quartz)\"}}",
            fragments.get("quartz__"));
    }

    @Test
    public void shouldLeaveItemsWithoutTitleOrAnnotationsTitleUnchanged()
    {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("bluesky__", "{\"name\":\"bluesky__send_message\",\"annotations\":{\"readOnlyHint\":true}}");
        fragments.put("quartz__", "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message\"}}");
        final Map<String, String> toolkits = Map.of("bluesky__", "bluesky", "quartz__", "quartz");

        McpToolTitleDisambiguator.disambiguate(fragments, toolkits);

        assertEquals("{\"name\":\"bluesky__send_message\",\"annotations\":{\"readOnlyHint\":true}}", fragments.get("bluesky__"));
        assertEquals(
            "{\"name\":\"quartz__send_message\",\"annotations\":{\"title\":\"Send Message\"}}",
            fragments.get("quartz__"));
    }
}
