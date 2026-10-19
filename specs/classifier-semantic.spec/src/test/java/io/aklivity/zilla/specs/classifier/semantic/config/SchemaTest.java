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
package io.aklivity.zilla.specs.classifier.semantic.config;

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
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/store/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/classifier/semantic/schema/semantic.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/classifier/semantic/config");

    @Test
    public void shouldValidateClassifier()
    {
        JsonObject config = schema.validate("classifier.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierMissingEmbedding()
    {
        schema.validate("classifier.missing.embedding.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierMissingStore()
    {
        schema.validate("classifier.missing.store.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierMissingThreshold()
    {
        schema.validate("classifier.missing.threshold.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierEmptyLabels()
    {
        schema.validate("classifier.empty.labels.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierThresholdOutOfRange()
    {
        schema.validate("classifier.threshold.out.of.range.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClassifierUnknownOption()
    {
        schema.validate("classifier.unknown.option.invalid.yaml");
    }
}
