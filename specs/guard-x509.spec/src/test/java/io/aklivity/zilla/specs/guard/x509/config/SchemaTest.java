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
package io.aklivity.zilla.specs.guard.x509.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/guard/x509/schema/x509.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/guard/x509/config");

    @Test
    public void shouldValidateGuard()
    {
        JsonObject config = schema.validate("guard.yaml");

        assertThat(config, not(nullValue()));

        JsonObject options = config.getJsonObject("guards").getJsonObject("x509_0").getJsonObject("options");
        assertThat(options.getString("identity"), equalTo("subject.cn"));
        assertThat(options.getJsonObject("attributes").getString("organization"), equalTo("subject.o"));

        JsonArray internal = options.getJsonObject("roles").getJsonArray("internal");
        assertThat(internal.size(), equalTo(2));
        assertThat(internal.getJsonObject(0).getString("subject.ou"), equalTo("Platform"));
    }

    @Test
    public void shouldValidateGuardWithDefaults()
    {
        JsonObject config = schema.validate("guard-defaults.yaml");

        assertThat(config, not(nullValue()));
        assertThat(config.getJsonObject("guards").getJsonObject("x509_0").containsKey("options"), is(false));
    }

    @Test
    public void shouldValidateGuardedRoute()
    {
        JsonObject config = schema.validate("zilla.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateEvent()
    {
        JsonObject config = schema.validate("event.yaml");

        assertThat(config, not(nullValue()));
    }
}
