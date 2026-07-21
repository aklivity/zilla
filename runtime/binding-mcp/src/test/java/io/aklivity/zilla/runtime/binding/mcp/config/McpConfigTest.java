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
package io.aklivity.zilla.runtime.binding.mcp.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import org.junit.Test;

public class McpConfigTest
{
    @Test
    public void shouldBuildElicitationWithDefaultCallback()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .build();

        assertThat(elicitation.callback, equalTo(McpElicitationConfig.DEFAULT_CALLBACK_PATH));
    }

    @Test
    public void shouldBuildElicitationWithTimeout()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .timeout(Duration.ofSeconds(30))
            .build();

        assertThat(elicitation.timeout, equalTo(Duration.ofSeconds(30)));
    }

    @Test
    public void shouldBuildElicitationWithCallback()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .callback("oauth/callback")
            .build();

        assertThat(elicitation.callback, equalTo("oauth/callback"));
    }

    @Test
    public void shouldMapElicitation()
    {
        String callback = McpElicitationConfig.<String>builder(e -> e.callback)
            .callback("auth/complete")
            .build();

        assertThat(callback, equalTo("auth/complete"));
    }
}
