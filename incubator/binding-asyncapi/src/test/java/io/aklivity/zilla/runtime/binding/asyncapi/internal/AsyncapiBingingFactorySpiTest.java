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


import java.net.URL;

import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class AsyncapiBingingFactorySpiTest
{
    @Mock
    public EngineContext context;


    @Before
    public void initProperties()
    {
        //Mockito.doReturn(context).when().readURL();
    }

    @Test
    public void shouldAddCompositeBinding() throws Exception
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "configure-expression-invalid");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;

        AsyncapiBindingFactorySpi factorySpi = new AsyncapiBindingFactorySpi();
        AsyncapiBinding binding = factorySpi.create(new Configuration());

        AsyncapiBindingContext asyncContext = binding.supply(context);

        asyncContext.attach()
    }
}
