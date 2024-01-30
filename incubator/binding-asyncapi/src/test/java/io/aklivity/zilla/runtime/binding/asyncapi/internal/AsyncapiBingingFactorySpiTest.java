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


import static org.mockito.ArgumentMatchers.any;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class AsyncapiBingingFactorySpiTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    public EngineContext context;


    @Before
    public void init()
    {
        Mockito.doReturn(1).when(context).supplyTypeId(any());
    }

    @Test
    public void shouldAddCompositeBinding() throws URISyntaxException
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "asyncapi");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;

        AsyncapiBindingFactorySpi factorySpi = new AsyncapiBindingFactorySpi();
        AsyncapiBinding binding = factorySpi.create(new Configuration());

        AsyncapiBindingContext asyncContext = binding.supply(context);

        BindingConfig config = BindingConfig.builder()
            .namespace("example")
            .name("asyncapi0")
            .type("asyncapi")
            .kind(KindConfig.SERVER)
            .options(new AsyncapiOptionsConfig(Collections.singletonList(configURL.toURI())))
            .build();

        asyncContext.attach(config);
        Assert.assertNotNull(config.composites);
        Assert.assertEquals(4, config.composites.get(0).bindings.size());
    }
}
