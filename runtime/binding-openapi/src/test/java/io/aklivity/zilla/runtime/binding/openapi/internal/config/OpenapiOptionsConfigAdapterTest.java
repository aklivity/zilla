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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class OpenapiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING);
        adapter.adaptType("openapi");
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
              petstore:
                catalog:
                  catalog0:
                    subject: petstore
                    version: latest
                security:
                  bearerAuth: test0
            """;

        OpenapiOptionsConfig options = jsonb.fromJson(text, OpenapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
    }

    @Test
    public void shouldReadOptionsWithOverlay()
    {
        String text =
            """
            specs:
              petstore:
                catalog:
                  catalog0:
                    subject: petstore
                    version: latest
                overlay:
                  catalog0:
                    subject: petstore-overlay
                    version: latest
            """;

        OpenapiOptionsConfig options = jsonb.fromJson(text, OpenapiOptionsConfig.class);

        OpenapiSpecificationConfig spec = options.specs.get(0);
        assertThat(spec.overlay, not(nullValue()));
        assertEquals("catalog0", spec.overlay.name);
        assertEquals("petstore-overlay", spec.overlay.subject);
        assertEquals("latest", spec.overlay.version);
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected =
            """
            specs:
              test:
                catalog:
                  catalog0:
                    subject: petstore
                    version: latest
            """;

        OpenapiOptionsConfig options = OpenapiOptionsConfig.builder()
            .spec(new OpenapiSpecificationConfig("test",
                of(new OpenapiCatalogConfig("catalog0", "petstore", "latest"))))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }

    @Test
    public void shouldWriteOptionsWithOverlay()
    {
        String expected =
            """
            specs:
              test:
                catalog:
                  catalog0:
                    subject: petstore
                    version: latest
                overlay:
                  catalog0:
                    subject: petstore-overlay
                    version: latest
            """;

        OpenapiOptionsConfig options = OpenapiOptionsConfig.builder()
            .spec(new OpenapiSpecificationConfig(
                "test",
                null,
                of(new OpenapiCatalogConfig("catalog0", "petstore", "latest")),
                null,
                new OpenapiCatalogConfig("catalog0", "petstore-overlay", "latest")))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
