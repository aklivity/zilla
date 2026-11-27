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
package io.aklivity.zilla.specs.binding.a2a.http.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/a2a/http/schema/a2a_http.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/model/json/schema/json.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/embedding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/store/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/http/config");

    @Test
    public void shouldValidateProxyOptions()
    {
        JsonObject config = schema.validate("proxy.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCard()
    {
        JsonObject config = schema.validate("proxy.card.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateGuardedProxy()
    {
        JsonObject config = schema.validate("proxy.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCredentials()
    {
        JsonObject config = schema.validate("proxy.credentials.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyStreaming()
    {
        JsonObject config = schema.validate("proxy.sse.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithUnresolvedArgument()
    {
        JsonObject config = schema.validate("proxy.unresolved.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerKind()
    {
        schema.validate("proxy.kind.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientKind()
    {
        schema.validate("proxy.kind.client.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectUnknownOption()
    {
        schema.validate("proxy.options.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectMissingEmbedding()
    {
        schema.validate("proxy.embedding.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectMissingStore()
    {
        schema.validate("proxy.store.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectThresholdOutOfRange()
    {
        schema.validate("proxy.threshold.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectSkillWithoutDescription()
    {
        schema.validate("proxy.skill.description.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectSkillWithoutSchemas()
    {
        schema.validate("proxy.skill.schemas.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithoutWhen()
    {
        schema.validate("proxy.route.when.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenWithUnknownKey()
    {
        schema.validate("proxy.route.when.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithMultipleWhenEntries()
    {
        schema.validate("proxy.route.when.multiple.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithoutExit()
    {
        schema.validate("proxy.route.exit.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectSchemeOtherThanHttpOrHttps()
    {
        schema.validate("proxy.route.headers.scheme.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectAuthorityWithoutPort()
    {
        schema.validate("proxy.route.headers.authority.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectHeadersWithoutPath()
    {
        schema.validate("proxy.route.headers.path.invalid.yaml");
    }
}
