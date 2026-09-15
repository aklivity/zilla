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
package io.aklivity.zilla.runtime.binding.llm.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect.Kind;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;

public class LlmSystemNamespaceGeneratorTest
{
    private final LlmSystemNamespaceGenerator generator = new LlmSystemNamespaceGenerator();

    @Test
    public void shouldReturnNullWhenNoDialectContributesSchema()
    {
        URL system = generator.generate(of(new LlmTestSchemalessDialectFactorySpi()));

        assertThat(system, nullValue());
    }

    @Test
    public void shouldReturnNullWhenNoDialectsRegistered()
    {
        URL system = generator.generate(emptyList());

        assertThat(system, nullValue());
    }

    @Test
    public void shouldGenerateCatalogPatchFromRegisteredDialectSchemas() throws IOException
    {
        URL system = generator.generate(of(new LlmTestSchemaDialectFactorySpi()));

        JsonObject patch;
        try (InputStream input = system.openStream())
        {
            patch = Json.createReader(input).readArray().getJsonObject(0);
        }

        assertThat(patch.getString("op"), equalTo("add"));
        assertThat(patch.getString("path"), equalTo("/catalogs/" + LlmSystemNamespaceGenerator.CATALOG_NAME));

        JsonObject catalog = patch.getJsonObject("value");
        assertThat(catalog.getString("type"), equalTo("inline"));

        JsonObject subjects = catalog.getJsonObject("options").getJsonObject("subjects");
        assertThat(subjects.getJsonObject("test.request").getString("schema"), equalTo("{\"request\":true}"));
        assertThat(subjects.getJsonObject("test.request").getString("version"), equalTo("latest"));
        assertThat(subjects.getJsonObject("test.response").getString("schema"), equalTo("{\"response\":true}"));
    }

    private static final class LlmTestSchemalessDialectFactorySpi implements LlmDialectFactorySpi
    {
        @Override
        public String name()
        {
            return "test";
        }

        @Override
        public LlmDialect create()
        {
            throw new UnsupportedOperationException();
        }
    }

    private static final class LlmTestSchemaDialectFactorySpi implements LlmDialectFactorySpi
    {
        @Override
        public String name()
        {
            return "test";
        }

        @Override
        public LlmDialect create()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL schema(
            Kind kind)
        {
            return getClass().getResource(kind == Kind.REQUEST ? "test.request.schema.json" : "test.response.schema.json");
        }
    }
}
