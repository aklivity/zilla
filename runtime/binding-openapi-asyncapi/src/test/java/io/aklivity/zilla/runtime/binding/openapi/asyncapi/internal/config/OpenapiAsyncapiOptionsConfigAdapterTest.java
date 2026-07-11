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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class OpenapiAsyncapiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING);
        adapter.adaptType("openapi-asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            """
            specs:
              openapi:
                openapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
              asyncapi:
                asyncapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
            """;

        OpenapiAsyncapiOptionsConfig options = jsonb.fromJson(text, OpenapiAsyncapiOptionsConfig.class);
        assertThat(options, not(nullValue()));
        OpenapiSpecificationConfig openapi = options.specs.openapi.stream().findFirst().get();
        assertEquals("openapi-id", openapi.label);
        assertThat(openapi.label, not(nullValue()));
        AsyncapiSpecificationConfig asyncapi = options.specs.asyncapi.stream().findFirst().get();
        assertEquals("asyncapi-id", asyncapi.label);
        assertThat(asyncapi.label, not(nullValue()));
    }

    @Test
    public void shouldReadOptionsWithOverlay()
    {
        String text =
            """
            specs:
              openapi:
                openapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
                  overlay:
                    overlay0:
                      subject: petstore-overlay
                      version: latest
              asyncapi:
                asyncapi-id:
                  catalog:
                    catalog1:
                      subject: petstore
                      version: latest
                  overlay:
                    overlay1:
                      subject: petstore-overlay
                      version: latest
            """;

        OpenapiAsyncapiOptionsConfig options = jsonb.fromJson(text, OpenapiAsyncapiOptionsConfig.class);
        OpenapiSpecificationConfig openapi = options.specs.openapi.stream().findFirst().get();
        assertThat(openapi.overlay, not(nullValue()));
        assertEquals("overlay0", openapi.overlay.name);
        assertEquals("petstore-overlay", openapi.overlay.subject);

        AsyncapiSpecificationConfig asyncapi = options.specs.asyncapi.stream().findFirst().get();
        assertThat(asyncapi.overlay, not(nullValue()));
        assertEquals("overlay1", asyncapi.overlay.name);
        assertEquals("petstore-overlay", asyncapi.overlay.subject);
    }

    @Test
    public void shouldWriteOptionsWithOverlay()
    {
        String expected =
            """
            specs:
              openapi:
                openapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
                  overlay:
                    overlay0:
                      subject: petstore-overlay
                      version: latest
              asyncapi:
                asyncapi-id:
                  catalog:
                    catalog1:
                      subject: petstore
                      version: latest
                  overlay:
                    overlay1:
                      subject: petstore-overlay
                      version: latest
            """;

        Set<OpenapiSpecificationConfig> openapiConfigs = new HashSet<>();
        openapiConfigs.add(new OpenapiSpecificationConfig(
            "openapi-id",
            null,
            List.of(),
            List.of(new OpenapiCatalogConfig("catalog0", "petstore", "latest")),
            null,
            new OpenapiCatalogConfig("overlay0", "petstore-overlay", "latest")));

        Set<AsyncapiSpecificationConfig> asyncapiConfigs = new HashSet<>();
        asyncapiConfigs.add(AsyncapiSpecificationConfig.builder()
            .label("asyncapi-id")
            .catalog()
                .name("catalog1")
                .subject("petstore")
                .version("latest")
                .build()
            .overlay()
                .name("overlay1")
                .subject("petstore-overlay")
                .version("latest")
                .build()
            .build());

        final OpenapiAsyncapiOptionsConfig options = new OpenapiAsyncapiOptionsConfig(
            new OpenapiAsyncapiSpecConfig(openapiConfigs, asyncapiConfigs));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected =
            """
            specs:
              openapi:
                openapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
              asyncapi:
                asyncapi-id:
                  catalog:
                    catalog0:
                      subject: petstore
                      version: latest
            """;

        Set<OpenapiSpecificationConfig> openapiConfigs = new HashSet<>();
        openapiConfigs.add(new OpenapiSpecificationConfig("openapi-id",
            List.of(new OpenapiCatalogConfig("catalog0", "petstore", "latest"))));

        Set<AsyncapiSpecificationConfig> asyncapiConfigs = new HashSet<>();
        asyncapiConfigs.add(AsyncapiSpecificationConfig.builder()
            .label("asyncapi-id")
            .catalog()
                .name("catalog0")
                .subject("petstore")
                .version("latest")
                .build()
            .build());

        final OpenapiAsyncapiOptionsConfig options = new OpenapiAsyncapiOptionsConfig(
            new OpenapiAsyncapiSpecConfig(openapiConfigs, asyncapiConfigs));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
