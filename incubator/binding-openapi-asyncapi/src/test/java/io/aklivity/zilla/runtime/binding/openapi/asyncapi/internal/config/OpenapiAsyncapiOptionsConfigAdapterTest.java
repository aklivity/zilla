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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.specs.binding.openapi.asyncapi.OpenapiAsyncapiSpecs;

public class OpenapiAsyncapiOptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ConfigAdapterContext context;

    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        initSpec("openapi/petstore.yaml");
        initSpec("asyncapi/petstore.yaml");
    }

    public void initSpec(
        String specConfig) throws IOException
    {
        try (InputStream resource = OpenapiAsyncapiSpecs.class
            .getResourceAsStream("config/" + specConfig))
        {
            String content = new String(resource.readAllBytes(), UTF_8);
            Mockito.doReturn(content).when(context).readURL(specConfig);

            OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
            adapter.adaptType("openapi-asyncapi");
            JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
            jsonb = JsonbBuilder.create(config);
        }
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
            "  \"specs\": {" +
            "    \"openapi\": {" +
            "      \"openapi-id\": \"openapi/petstore.yaml\"" +
            "    }," +
            "    \"asyncapi\": {" +
            "      \"asyncapi-id\": \"asyncapi/petstore.yaml\"" +
            "    }" +
            "  }" +
            "}";

        OpenapiAsyncapiOptionsConfig options = jsonb.fromJson(text, OpenapiAsyncapiOptionsConfig.class);
        assertThat(options, not(nullValue()));
        OpenapiConfig openapi = options.specs.openapi.stream().findFirst().get();
        assertEquals("openapi-id", openapi.apiLabel);
        assertThat(openapi.openapi, not(nullValue()));
        AsyncapiConfig asyncapi = options.specs.asyncapi.stream().findFirst().get();
        assertEquals("asyncapi-id", asyncapi.apiLabel);
        assertThat(asyncapi.asyncapi, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected = "{\"specs\":{\"openapi\":{\"openapi-id\":\"openapi/petstore.yaml\"},\"asyncapi\":" +
            "{\"asyncapi-id\":\"asyncapi/petstore.yaml\"}}}";

        OpenapiConfig openapi = new OpenapiConfig("openapi-id", 0L,
            "openapi/petstore.yaml", new Openapi());
        AsyncapiConfig asyncapi = new AsyncapiConfig("asyncapi-id", 0L,
            "asyncapi/petstore.yaml", new Asyncapi());

        final OpenapiAsyncapiOptionsConfig options = new OpenapiAsyncapiOptionsConfig(
            new OpenapiAsyncapiSpecConfig(singleton(openapi),
                singleton(asyncapi)));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
