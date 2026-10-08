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
package io.aklivity.zilla.config.classifier.patterns.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.patterns.PatternsClassifierOptionsConfig;

public class PatternsClassifierOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new PatternsClassifierOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String json = """
            {
                "entropy": 4.5,
                "labels":
                {
                    "secret.aws_key": "AKIA[0-9A-Z]{16}",
                    "secret.github_token": [ "ghp_[A-Za-z0-9]{36}", "gho_[A-Za-z0-9]{36}" ]
                }
            }""";

        PatternsClassifierOptionsConfig options = jsonb.fromJson(json, PatternsClassifierOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.entropy, closeTo(4.5, 0.0001));
        assertThat(options.labels.keySet(), contains("secret.aws_key", "secret.github_token"));
        assertThat(patterns(options, "secret.aws_key"), contains("AKIA[0-9A-Z]{16}"));
        assertThat(patterns(options, "secret.github_token"), contains("ghp_[A-Za-z0-9]{36}", "gho_[A-Za-z0-9]{36}"));
    }

    @Test
    public void shouldReadOptionsWithoutEntropy()
    {
        String json = """
            {
                "labels":
                {
                    "secret.aws_key": "AKIA[0-9A-Z]{16}"
                }
            }""";

        PatternsClassifierOptionsConfig options = jsonb.fromJson(json, PatternsClassifierOptionsConfig.class);

        assertThat(options.entropy, equalTo(0.0));
    }

    @Test
    public void shouldReadInlineFlags()
    {
        String json = """
            {
                "labels":
                {
                    "greeting": "(?i)hello"
                }
            }""";

        PatternsClassifierOptionsConfig options = jsonb.fromJson(json, PatternsClassifierOptionsConfig.class);

        assertThat(options.labels.get("greeting").get(0).matcher("HELLO").find(), equalTo(true));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expectedJson =
            "{" +
                "\"entropy\":4.5," +
                "\"labels\":" +
                "{" +
                    "\"secret.aws_key\":\"AKIA[0-9A-Z]{16}\"," +
                    "\"secret.github_token\":[\"ghp_[A-Za-z0-9]{36}\",\"gho_[A-Za-z0-9]{36}\"]" +
                "}" +
            "}";

        PatternsClassifierOptionsConfig options = PatternsClassifierOptionsConfig.builder()
            .entropy(4.5)
            .label("secret.aws_key", List.of("AKIA[0-9A-Z]{16}"))
            .label("secret.github_token", List.of("ghp_[A-Za-z0-9]{36}", "gho_[A-Za-z0-9]{36}"))
            .build();

        String json = jsonb.toJson(options);

        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteOptionsWithoutEntropy()
    {
        String expectedJson = "{\"labels\":{\"secret.aws_key\":\"AKIA[0-9A-Z]{16}\"}}";

        PatternsClassifierOptionsConfig options = PatternsClassifierOptionsConfig.builder()
            .label("secret.aws_key", List.of("AKIA[0-9A-Z]{16}"))
            .build();

        String json = jsonb.toJson(options);

        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldReadEmptyLabelsWhenAbsent()
    {
        String json = """
            {
                "entropy": 3.0
            }""";

        PatternsClassifierOptionsConfig options = jsonb.fromJson(json, PatternsClassifierOptionsConfig.class);

        assertThat(options.labels.isEmpty(), equalTo(true));
    }

    @Test(expected = JsonbException.class)
    public void shouldRejectInvalidExpression()
    {
        String json = """
            {
                "labels":
                {
                    "secret.aws_key": "AKIA[0-9A-Z"
                }
            }""";

        jsonb.fromJson(json, PatternsClassifierOptionsConfig.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidExpressionFromBuilder()
    {
        PatternsClassifierOptionsConfig.builder()
            .label("secret.aws_key", List.of("(unclosed"))
            .build();
    }

    private static List<String> patterns(
        PatternsClassifierOptionsConfig options,
        String label)
    {
        return options.labels.get(label).stream().map(Pattern::pattern).toList();
    }
}
