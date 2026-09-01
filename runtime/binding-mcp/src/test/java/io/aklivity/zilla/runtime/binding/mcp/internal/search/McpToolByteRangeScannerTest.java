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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.Map;

import org.junit.Test;

public class McpToolByteRangeScannerTest
{
    @Test
    public void shouldPreserveDocumentOrderNotAlphabeticalOrder()
    {
        String json =
                """
                {"tools":[
                    {"name":"zeta","description":"third alphabetically, first in document"},
                    {"name":"alpha","description":"first alphabetically, second in document"},
                    {"name":"mid","description":"middle alphabetically, third in document"}
                ]}
                """;

        Map<CharSequence, McpToolByteRange> ranges = McpToolByteRangeScanner.scan(json.getBytes(UTF_8));

        assertThat(ranges.keySet(), contains("zeta", "alpha", "mid"));
    }

    @Test
    public void shouldReturnEmptyForNoToolsArray()
    {
        String json = "{\"resources\":[]}";

        Map<CharSequence, McpToolByteRange> ranges = McpToolByteRangeScanner.scan(json.getBytes(UTF_8));

        assertThat(ranges.entrySet(), empty());
    }
}
