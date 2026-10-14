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
package io.aklivity.zilla.specs.classifier.patterns.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/embedding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/classifier/patterns/schema/patterns.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/classifier/patterns/config");

    @Test
    public void shouldValidateClassifier()
    {
        JsonObject config = schema.validate("classifier.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClassifierWithoutEntropy()
    {
        JsonObject config = schema.validate("classifier.without.entropy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierMissingLabels()
    {
        schema.validate("classifier.missing.labels.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierEmptyLabels()
    {
        schema.validate("classifier.empty.labels.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierEmptyPatterns()
    {
        schema.validate("classifier.empty.patterns.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierEmptyPattern()
    {
        schema.validate("classifier.empty.pattern.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierNegativeEntropy()
    {
        schema.validate("classifier.negative.entropy.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierUnknownOption()
    {
        schema.validate("classifier.unknown.option.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierUnexpectedEmbedding()
    {
        schema.validate("classifier.unexpected.embedding.invalid.yaml");
    }
}
