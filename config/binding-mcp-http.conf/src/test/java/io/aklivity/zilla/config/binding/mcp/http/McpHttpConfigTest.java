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
package io.aklivity.zilla.config.binding.mcp.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.Test;

public class McpHttpConfigTest
{
    @Test
    public void shouldBuildBodyWithTemplate()
    {
        McpHttpBodyConfig body = McpHttpBodyConfig.builder()
            .template(Map.of("title", "${args.title}"))
            .build();

        assertThat(body.template.get("title"), equalTo("${args.title}"));
        assertThat(body.model, nullValue());
    }

    @Test
    public void shouldMapBody()
    {
        Map<String, String> template = McpHttpBodyConfig.<Map<String, String>>builder(b -> b.template)
            .template(Map.of("title", "${args.title}"))
            .build();

        assertThat(template.get("title"), equalTo("${args.title}"));
    }
}
