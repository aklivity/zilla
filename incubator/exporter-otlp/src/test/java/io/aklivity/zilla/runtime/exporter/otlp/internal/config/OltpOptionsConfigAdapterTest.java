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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class OltpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new OtlpOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String text =
            "{\n" +
                "\"interval\": 30,\n" +
                "\"endpoints\":\n" +
                    "[\n" +
                        "{\n" +
                            "\"url\": \"http://localhost:4317\"\n" +
                        "}\n" +
                    "]\n" +
            "}";

        // WHEN
        OtlpOptionsConfig options = jsonb.fromJson(text, OtlpOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.interval, equalTo(30L));
        assertThat(options.endpoints[0].url, equalTo("http://localhost:4317"));
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http://localhost:4317");
        OtlpOptionsConfig config = new OtlpOptionsConfig(30, new OtlpEndpointConfig[]{endpoint});

        // WHEN
        String text = jsonb.toJson(config);

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"interval\":30,\"endpoints\":[{\"url\":\"http://localhost:4317\"}]}"));
    }
}
