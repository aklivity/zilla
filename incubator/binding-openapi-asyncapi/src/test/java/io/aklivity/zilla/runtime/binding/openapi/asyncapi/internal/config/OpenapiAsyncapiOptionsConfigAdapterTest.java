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

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

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
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("openapi-asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
                "  \"specs\": {" +
                "    \"openapi\": {" +
                "      \"openapi-id\": {" +
                "        \"catalog\": {" +
                "          \"catalog0\": {" +
                "            \"subject\": \"petstore\"," +
                "            \"version\": \"latest\"" +
                "          }" +
                "        }" +
                "      }" +
                "    }," +
                "    \"asyncapi\": {" +
                "      \"asyncapi-id\": {" +
                "        \"catalog\": {" +
                "          \"catalog0\": {" +
                "            \"subject\": \"petstore\"," +
                "            \"version\": \"latest\"" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}";

        OpenapiAsyncapiOptionsConfig options = jsonb.fromJson(text, OpenapiAsyncapiOptionsConfig.class);
        assertThat(options, not(nullValue()));
        OpenapiConfig openapi = options.specs.openapi.stream().findFirst().get();
        assertEquals("openapi-id", openapi.apiLabel);
        assertThat(openapi.apiLabel, not(nullValue()));
        AsyncapiConfig asyncapi = options.specs.asyncapi.stream().findFirst().get();
        assertEquals("asyncapi-id", asyncapi.apiLabel);
        assertThat(asyncapi.apiLabel, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected = "{\"specs\":{\"openapi\":{\"openapi-id\":{\"catalog\":{\"catalog0\":{\"subject\":\"petstore\"," +
            "\"version\":\"latest\"}}}},\"asyncapi\":{\"asyncapi-id\":{\"catalog\":" +
            "{\"catalog0\":{\"subject\":\"petstore\",\"version\":\"latest\"}}}}}}";

        Set<OpenapiConfig> openapiConfigs = new HashSet<>();
        openapiConfigs.add(new OpenapiConfig("openapi-id",
            emptyList(), List.of(new OpenapiCatalogConfig("catalog0", "petstore", "latest"))));

        Set<AsyncapiConfig> asyncapiConfigs = new HashSet<>();
        asyncapiConfigs.add(new AsyncapiConfig("asyncapi-id", emptyList(),
            List.of(new AsyncapiCatalogConfig("catalog0", "petstore", "latest"))));

        final OpenapiAsyncapiOptionsConfig options = new OpenapiAsyncapiOptionsConfig(
            new OpenapiAsyncapiSpecConfig(openapiConfigs, asyncapiConfigs));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
