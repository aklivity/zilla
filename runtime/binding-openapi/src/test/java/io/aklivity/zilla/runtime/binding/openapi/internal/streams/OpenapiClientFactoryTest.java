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
package io.aklivity.zilla.runtime.binding.openapi.internal.streams;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiConfiguration;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

public class OpenapiClientFactoryTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private BindingHandler streamFactory;

    private OpenapiClientFactory factory;

    @Before
    public void init()
    {
        when(context.writeBuffer()).thenReturn(new UnsafeBufferEx(new byte[1024]));
        when(context.streamFactory()).thenReturn(streamFactory);
        when(context.supplyTypeId(anyString())).thenReturn(1);

        factory = new OpenapiClientFactory(new OpenapiConfiguration(new Configuration()), context);
    }

    @Test
    public void shouldRoundRobinAcrossServers()
    {
        List<URI> servers = List.of(URI.create("http://localhost:8080"), URI.create("http://localhost:9090"));

        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:9090")));
        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
    }

    @Test
    public void shouldTrackRoundRobinIndependentlyPerBindingAndSpec()
    {
        List<URI> servers = List.of(URI.create("http://localhost:8080"), URI.create("http://localhost:9090"));

        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
        assertThat(factory.selectServer(2L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
        assertThat(factory.selectServer(1L, "itemstore", servers), equalTo(URI.create("http://localhost:8080")));
    }

    @Test
    public void shouldAlwaysSelectOnlyServerWhenSingular()
    {
        List<URI> servers = List.of(URI.create("http://localhost:8080"));

        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
        assertThat(factory.selectServer(1L, "petstore", servers), equalTo(URI.create("http://localhost:8080")));
    }
}
