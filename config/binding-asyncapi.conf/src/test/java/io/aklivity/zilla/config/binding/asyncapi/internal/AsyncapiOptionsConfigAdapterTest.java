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
package io.aklivity.zilla.config.binding.asyncapi.internal;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.function.Function;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.asyncapi.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AsyncapiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AsyncapiOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptionsMqtt() throws IOException
    {
        String yaml =
                """
                specs:
                  mqtt-api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                    servers:
                      - test.mosquitto.org:1883
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        AsyncapiSpecificationConfig asyncapi = options.specs.get(0);
        assertThat(asyncapi.servers, equalTo(asList("test.mosquitto.org:1883")));
    }

    @Test
    public void shouldWriteOptionsMqtt() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .spec()
                .label("mqtt-api")
                .catalog()
                    .name("catalog0")
                    .subject("smartylighting")
                    .version("latest")
                    .build()
                .serverOverride("test.mosquitto.org:1883")
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            specs:
              mqtt-api:
                servers:
                  - "test.mosquitto.org:1883"
                catalog:
                  catalog0:
                    subject: smartylighting
                    version: latest
            """));
    }

    @Test
    public void shouldReadOptionsWithOverlay() throws IOException
    {
        String yaml =
                """
                specs:
                  mqtt-api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                        overlay:
                          catalog1:
                            subject: smartylighting-overlay
                            version: latest
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        AsyncapiSpecificationConfig spec = options.specs.get(0);
        AsyncapiCatalogConfig catalog = spec.catalogs.get(0);
        assertThat(catalog.overlay, not(nullValue()));
        assertThat(catalog.overlay.name, equalTo("catalog1"));
        assertThat(catalog.overlay.schema.subject, equalTo("smartylighting-overlay"));
        assertThat(catalog.overlay.schema.version, equalTo("latest"));
    }

    @Test
    public void shouldReadOptionsWithDeprecatedOverlay() throws IOException
    {
        String yaml =
                """
                specs:
                  mqtt-api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                    overlay:
                      catalog1:
                        subject: smartylighting-overlay
                        version: latest
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        AsyncapiSpecificationConfig spec = options.specs.get(0);
        AsyncapiCatalogConfig catalog = spec.catalogs.get(0);
        assertThat(catalog.overlay, not(nullValue()));
        assertThat(catalog.overlay.name, equalTo("catalog1"));
        assertThat(catalog.overlay.schema.subject, equalTo("smartylighting-overlay"));
        assertThat(catalog.overlay.schema.version, equalTo("latest"));
    }

    @Test
    public void shouldWriteOptionsWithOverlay() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .spec()
                .label("mqtt-api")
                .catalog(AsyncapiCatalogConfig.builder()
                    .name("catalog0")
                    .subject("smartylighting")
                    .version("latest")
                    .overlay()
                        .name("catalog1")
                        .subject("smartylighting-overlay")
                        .version("latest")
                        .build()
                    .build())
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            specs:
              mqtt-api:
                catalog:
                  catalog0:
                    subject: smartylighting
                    version: latest
                    overlay:
                      catalog1:
                        subject: smartylighting-overlay
                        version: latest
            """));
    }

}
