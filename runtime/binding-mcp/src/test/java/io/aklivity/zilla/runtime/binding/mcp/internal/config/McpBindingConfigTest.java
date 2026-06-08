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

import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.authorityOf;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.naturalAuthority;
import static io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig.pathOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;

import org.junit.Test;

public class McpBindingConfigTest
{
    @Test
    public void shouldKeepExplicitPortInAuthority()
    {
        assertThat(authorityOf(URI.create("http://localhost:8080/mcp")), equalTo("localhost:8080"));
    }

    @Test
    public void shouldDefaultHttpPortInAuthority()
    {
        assertThat(authorityOf(URI.create("http://localhost/mcp")), equalTo("localhost:80"));
    }

    @Test
    public void shouldDefaultHttpsPortInAuthority()
    {
        assertThat(authorityOf(URI.create("https://localhost/mcp")), equalTo("localhost:443"));
    }

    @Test
    public void shouldResolvePath()
    {
        assertThat(pathOf(URI.create("http://localhost:8080/mcp")), equalTo("/mcp"));
    }

    @Test
    public void shouldDefaultRootPath()
    {
        assertThat(pathOf(URI.create("http://localhost:8080")), equalTo("/"));
    }

    @Test
    public void shouldStripDefaultHttpsPortFromAuthority()
    {
        assertThat(naturalAuthority("localhost:443", "https"), equalTo("localhost"));
    }

    @Test
    public void shouldKeepNonDefaultPortInAuthority()
    {
        assertThat(naturalAuthority("localhost:8080", "https"), equalTo("localhost:8080"));
    }

    @Test
    public void shouldKeepAuthorityWithoutPort()
    {
        assertThat(naturalAuthority("localhost", "https"), equalTo("localhost"));
    }
}
