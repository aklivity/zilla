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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class FilesystemOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new FilesystemOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text = "{" +
                "\"subjects\":" +
                    "{" +
                    "\"subject1\":" +
                        "{" +
                            "\"url\":\"asyncapi/mqtt.yaml\"," +
                            "\"version\": \"latest\"" +
                        "}" +
                    "}" +
                "}";

        FilesystemOptionsConfig catalog = jsonb.fromJson(text, FilesystemOptionsConfig.class);

        assertThat(catalog, not(nullValue()));
        FilesystemSchemaConfig schema = catalog.subjects.get(0);
        assertThat(schema.subject, equalTo("subject1"));
        assertThat(schema.url, equalTo("asyncapi/mqtt.yaml"));
        assertThat(schema.version, equalTo("latest"));
    }

    @Test
    public void shouldWriteCondition()
    {
        String expectedJson = "{" +
            "\"subjects\":" +
                "{" +
                    "\"subject1\":" +
                        "{" +
                            "\"url\":\"asyncapi/mqtt.yaml\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "}" +
                "}";

        FilesystemOptionsConfig catalog = (FilesystemOptionsConfig) new FilesystemOptionsConfigBuilder<>(identity())
                .subjects()
                    .subject("subject1")
                        .url("asyncapi/mqtt.yaml")
                        .version("latest")
                        .build()
                .build();

        String json = jsonb.toJson(catalog);

        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
