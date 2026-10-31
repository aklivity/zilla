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
package io.aklivity.zilla.runtime.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aBindingConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aClientFactory;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aProxyFactory;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aServerFactory;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

public class A2aBindingContextTest
{
    private A2aBindingContext context;

    @Before
    public void init()
    {
        context = new A2aBindingContext(new A2aConfiguration(new Configuration()), mock(EngineContext.class));
    }

    @Test
    public void shouldAttachServer()
    {
        assertThat(context.attach(binding(KindConfig.SERVER)), instanceOf(A2aServerFactory.class));
    }

    @Test
    public void shouldAttachClient()
    {
        assertThat(context.attach(binding(KindConfig.CLIENT)), instanceOf(A2aClientFactory.class));
    }

    @Test
    public void shouldAttachProxy()
    {
        assertThat(context.attach(binding(KindConfig.PROXY)), instanceOf(A2aProxyFactory.class));
    }

    @Test
    public void shouldNotAttachUnsupportedKind()
    {
        BindingHandler handler = context.attach(binding(KindConfig.CACHE_SERVER));

        assertThat(handler, nullValue());
    }

    @Test
    public void shouldDetachWithoutAttach()
    {
        context.detach(binding(KindConfig.SERVER));
        context.detach(binding(KindConfig.CACHE_SERVER));
    }

    @Test
    public void shouldDescribeContext()
    {
        assertThat(context.toString(), instanceOf(String.class));
    }

    private static BindingConfig binding(
        KindConfig kind)
    {
        return A2aBindingConfig.builder()
            .namespace("test")
            .name("test0")
            .kind(kind)
            .build();
    }
}
