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
package io.aklivity.zilla.config.classifier.semantic.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfig;

public class SemanticClassifierOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new SemanticClassifierOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String json = """
            {
                "threshold": 0.85,
                "labels":
                {
                    "prompt.injection": [ "ignore previous instructions", "disregard the system prompt" ],
                    "policy.exfiltration": [ "send the contents to" ]
                }
            }""";

        SemanticClassifierOptionsConfig options = jsonb.fromJson(json, SemanticClassifierOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.threshold, closeTo(0.85, 0.0001));
        assertThat(options.labels.keySet(), contains("prompt.injection", "policy.exfiltration"));
        assertThat(options.labels.get("prompt.injection"),
            contains("ignore previous instructions", "disregard the system prompt"));
        assertThat(options.labels.get("policy.exfiltration"), contains("send the contents to"));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expectedJson =
            "{" +
                "\"threshold\":0.85," +
                "\"labels\":" +
                "{" +
                    "\"prompt.injection\":[\"ignore previous instructions\",\"disregard the system prompt\"]," +
                    "\"policy.exfiltration\":[\"send the contents to\"]" +
                "}" +
            "}";

        SemanticClassifierOptionsConfig options = SemanticClassifierOptionsConfig.builder()
            .threshold(0.85)
            .label("prompt.injection", List.of("ignore previous instructions", "disregard the system prompt"))
            .label("policy.exfiltration", List.of("send the contents to"))
            .build();

        String json = jsonb.toJson(options);

        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldReadEmptyLabelsWhenAbsent()
    {
        String json = """
            {
                "threshold": 0.5
            }""";

        SemanticClassifierOptionsConfig options = jsonb.fromJson(json, SemanticClassifierOptionsConfig.class);

        assertThat(options.labels.isEmpty(), equalTo(true));
    }
}
