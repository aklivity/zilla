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
package io.aklivity.zilla.config.classifier.presidio.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;

public class PresidioClassifierOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new PresidioClassifierOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String json = """
            {
                "endpoint": "http://presidio-analyzer:3000",
                "credentials":
                {
                    "authorization": "Bearer secret"
                },
                "language": "es",
                "threshold": 0.6,
                "timeout": "PT10S",
                "labels":
                {
                    "person": "PERSON",
                    "email": "EMAIL_ADDRESS"
                }
            }""";

        PresidioClassifierOptionsConfig options = jsonb.fromJson(json, PresidioClassifierOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.endpoint, equalTo("http://presidio-analyzer:3000"));
        assertThat(options.authorization, equalTo("Bearer secret"));
        assertThat(options.language, equalTo("es"));
        assertThat(options.threshold, closeTo(0.6, 0.0001));
        assertThat(options.timeout, equalTo(Duration.ofSeconds(10)));
        assertThat(options.labels.keySet(), contains("person", "email"));
        assertThat(options.labels.get("person"), equalTo("PERSON"));
        assertThat(options.labels.get("email"), equalTo("EMAIL_ADDRESS"));
    }

    @Test
    public void shouldReadOptionsWithDefaults()
    {
        String json = """
            {
                "endpoint": "http://presidio-analyzer:3000"
            }""";

        PresidioClassifierOptionsConfig options = jsonb.fromJson(json, PresidioClassifierOptionsConfig.class);

        assertThat(options.endpoint, equalTo("http://presidio-analyzer:3000"));
        assertThat(options.authorization, nullValue());
        assertThat(options.language, equalTo("en"));
        assertThat(options.threshold, equalTo(0.0));
        assertThat(options.timeout, equalTo(Duration.ofSeconds(5)));
        assertThat(options.labels, aMapWithSize(0));
    }

    @Test
    public void shouldReadNegativeTimeoutAsZero()
    {
        String json = """
            {
                "endpoint": "http://presidio-analyzer:3000",
                "timeout": "-PT1S"
            }""";

        PresidioClassifierOptionsConfig options = jsonb.fromJson(json, PresidioClassifierOptionsConfig.class);

        assertThat(options.timeout, equalTo(Duration.ZERO));
    }

    @Test
    public void shouldReadZeroTimeout()
    {
        String json = """
            {
                "endpoint": "http://presidio-analyzer:3000",
                "timeout": "PT0S"
            }""";

        PresidioClassifierOptionsConfig options = jsonb.fromJson(json, PresidioClassifierOptionsConfig.class);

        assertThat(options.timeout, equalTo(Duration.ZERO));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expectedJson =
            "{" +
                "\"endpoint\":\"http://presidio-analyzer:3000\"," +
                "\"credentials\":{\"authorization\":\"Bearer secret\"}," +
                "\"language\":\"es\"," +
                "\"threshold\":0.6," +
                "\"timeout\":\"PT10S\"," +
                "\"labels\":" +
                "{" +
                    "\"person\":\"PERSON\"," +
                    "\"email\":\"EMAIL_ADDRESS\"" +
                "}" +
            "}";

        PresidioClassifierOptionsConfig options = PresidioClassifierOptionsConfig.builder()
            .endpoint("http://presidio-analyzer:3000")
            .authorization("Bearer secret")
            .language("es")
            .threshold(0.6)
            .timeout(Duration.ofSeconds(10))
            .label("person", "PERSON")
            .label("email", "EMAIL_ADDRESS")
            .build();

        String json = jsonb.toJson(options);

        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteOptionsWithDefaults()
    {
        String expectedJson = "{\"endpoint\":\"http://presidio-analyzer:3000\"}";

        PresidioClassifierOptionsConfig options = PresidioClassifierOptionsConfig.builder()
            .endpoint("http://presidio-analyzer:3000")
            .build();

        String json = jsonb.toJson(options);

        assertThat(json, equalTo(expectedJson));
    }
}
