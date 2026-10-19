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
package io.aklivity.zilla.config.model.classify.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.config.model.classify.ClassifyRejectConfig;

public class ClassifyModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new ClassifyModelConfigAdapter(List.of()));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadClassifyModel()
    {
        // GIVEN
        String json = """
            {
                "model": "classify",
                "reject":
                {
                    "moderator0": [ "prompt.injection", "policy.exfiltration" ],
                    "patterns0": [ "secret.aws_key" ]
                }
            }""";

        // WHEN
        ClassifyModelConfig config = jsonb.fromJson(json, ClassifyModelConfig.class);

        // THEN
        assertThat(config, not(nullValue()));
        assertThat(config.model, equalTo("classify"));
        assertThat(config.reject, hasSize(2));

        ClassifyRejectConfig moderator = config.reject.get(0);
        assertThat(moderator.name, equalTo("moderator0"));
        assertThat(moderator.labels, contains("prompt.injection", "policy.exfiltration"));

        ClassifyRejectConfig patterns = config.reject.get(1);
        assertThat(patterns.name, equalTo("patterns0"));
        assertThat(patterns.labels, contains("secret.aws_key"));
    }

    @Test
    public void shouldReferenceEachRejectedClassifier()
    {
        // GIVEN
        String json = """
            {
                "model": "classify",
                "reject":
                {
                    "moderator0": [ "prompt.injection" ],
                    "patterns0": [ "secret.aws_key" ]
                }
            }""";

        // WHEN
        ClassifyModelConfig config = jsonb.fromJson(json, ClassifyModelConfig.class);

        // THEN
        assertThat(config.refs(), contains(config.reject.get(0), config.reject.get(1)));
    }

    @Test
    public void shouldReadEmptyRejectWhenAbsent()
    {
        // GIVEN -- reject is required by the classify model's own JSON schema, but the adapter itself
        // stays defensive about a config built or parsed without going through it
        String json = """
            {
                "model": "classify"
            }""";

        // WHEN
        ClassifyModelConfig config = jsonb.fromJson(json, ClassifyModelConfig.class);

        // THEN
        assertThat(config.reject, hasSize(0));
    }

    @Test
    public void shouldWriteClassifyModel()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"classify\"," +
                "\"reject\":" +
                "{" +
                    "\"moderator0\":[\"prompt.injection\",\"policy.exfiltration\"]," +
                    "\"patterns0\":[\"secret.aws_key\"]" +
                "}" +
            "}";
        ClassifyModelConfig config = ClassifyModelConfig.builder()
            .reject("moderator0", List.of("prompt.injection", "policy.exfiltration"))
            .reject("patterns0", List.of("secret.aws_key"))
            .build();

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
