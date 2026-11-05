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
package io.aklivity.zilla.specs.metrics.model.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/model/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/metrics/model/schema/model.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/metrics/model/config");

    @Test
    public void shouldValidateRejectedInvalid()
    {
        JsonObject config = schema.validate("server.rejected.invalid.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateRejectedWithheld()
    {
        JsonObject config = schema.validate("server.rejected.withheld.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateTransformDuration()
    {
        JsonObject config = schema.validate("server.transform.duration.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateTransformDurationSuspended()
    {
        JsonObject config = schema.validate("server.transform.duration.suspended.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateUnselected()
    {
        JsonObject config = schema.validate("server.unselected.yaml");

        assertThat(config, not(nullValue()));
    }
}
