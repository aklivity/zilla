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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class AguiA2aBindingFactorySpiTest
{
    private final BindingFactorySpi factory = new AguiA2aBindingFactorySpi();

    private static BindingConfig binding(
        KindConfig kind)
    {
        RouteConfig route = RouteConfig.builder().order(0).exit("a2a0").build();
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("agui-a2a")
            .kind(kind)
            .routes(List.of(route))
            .build();
        binding.resolveId = name -> 1L;
        binding.id = 1L;
        return binding;
    }

    @Test
    public void shouldCreateBinding()
    {
        assertThat(factory.type(), equalTo("agui-a2a"));
        assertThat(factory.create(new Configuration()), instanceOf(AguiA2aBinding.class));
        assertThat(factory.create(new Configuration()).name(), equalTo("agui-a2a"));
    }

    @Test
    public void shouldAttachAndDetachProxy()
    {
        BindingContext context = factory.create(new Configuration()).supply(mock(EngineContext.class));
        BindingConfig binding = binding(KindConfig.PROXY);

        BindingHandler handler = context.attach(binding);

        assertThat(handler, not(nullValue()));
        assertThat(handler.newStream(0, null, 0, 0, mock(MessageConsumer.class)), nullValue());
        assertThat(context.toString(), containsString("AguiA2aBindingContext"));

        context.detach(binding);
    }

    @Test
    public void shouldNotAttachOtherKinds()
    {
        BindingContext context = factory.create(new Configuration()).supply(mock(EngineContext.class));
        BindingConfig binding = binding(KindConfig.SERVER);

        assertThat(context.attach(binding), nullValue());

        context.detach(binding);
    }
}
