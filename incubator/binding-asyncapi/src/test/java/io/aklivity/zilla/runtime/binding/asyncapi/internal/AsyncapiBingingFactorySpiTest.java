/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.config.AsyncapiOptionsConfigAdapterTest;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.internal.config.OptionsAdapter;

public class AsyncapiBingingFactorySpiTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ConfigAdapterContext context;
    private OptionsAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        String content;
        try (InputStream resource = AsyncapiOptionsConfigAdapterTest.class
            .getResourceAsStream("../../../../../../specs/binding/asyncapi/config/files/mqtt/asyncapi.yaml"))
        {
            content = new String(resource.readAllBytes(), UTF_8);
        }
        Mockito.doReturn(content).when(context).readURL("mqtt/asyncapi.yaml");

        adapter = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldAddCompositeBinding()
    {
        String text =
            "{" +
                "\"specs\":" +
                "[" +
                    "\"mqtt/asyncapi.yaml\"" +
                "]" +
            "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        BindingConfig config = BindingConfig.builder()
            .namespace("example")
            .name("asyncapi0")
            .type("asyncapi")
            .kind(KindConfig.SERVER)
            .options(options)
            .build();

        AsyncapiCompositeBindingAdapter asyncapiCompositeSpi = new AsyncapiCompositeBindingAdapter();
        Assert.assertEquals(0, config.composites.size());
        BindingConfig newConfig = asyncapiCompositeSpi.adapt(config);
        Assert.assertEquals(1, newConfig.composites.size());
        Assert.assertEquals(2, newConfig.composites.get(0).bindings.size());
    }
}
