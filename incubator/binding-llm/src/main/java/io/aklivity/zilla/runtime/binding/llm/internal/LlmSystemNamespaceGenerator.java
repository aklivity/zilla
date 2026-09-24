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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect.Kind;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;

/**
 * Builds the {@code sys:} namespace patch contributing one shared {@code inline} catalog, populated from
 * every registered {@link LlmDialectFactorySpi}'s own request/response schema, so any {@code llm} binding
 * can enforce a dialect's schema without any operator-visible catalog configuration of its own.
 * <p>
 * The dialect set is fixed for the JVM's lifetime (it is {@code ServiceLoader}-discovered off the
 * classpath), so this is generated once and shared, the same way {@code sys:} already shares a binding
 * (e.g. {@code http_client}) across every binding that references it.
 * </p>
 */
final class LlmSystemNamespaceGenerator
{
    static final String CATALOG_NAME = "llm_dialects";

    private static final String SUBJECT_REQUEST_SUFFIX = ".request";
    private static final String SUBJECT_RESPONSE_SUFFIX = ".response";
    private static final String VERSION_LATEST = "latest";

    URL generate(
        Iterable<LlmDialectFactorySpi> dialects)
    {
        JsonObjectBuilder subjects = Json.createObjectBuilder();
        boolean contributed = false;

        for (LlmDialectFactorySpi dialect : dialects)
        {
            contributed |= addSubject(subjects, dialect, Kind.REQUEST, SUBJECT_REQUEST_SUFFIX);
            contributed |= addSubject(subjects, dialect, Kind.RESPONSE, SUBJECT_RESPONSE_SUFFIX);
        }

        return contributed ? patch(subjects.build()) : null;
    }

    private static boolean addSubject(
        JsonObjectBuilder subjects,
        LlmDialectFactorySpi dialect,
        Kind kind,
        String suffix)
    {
        URL schema = dialect.schema(kind);
        boolean added = schema != null;

        if (added)
        {
            subjects.add(dialect.name() + suffix, Json.createObjectBuilder()
                .add("version", VERSION_LATEST)
                .add("schema", readSchema(schema)));
        }

        return added;
    }

    private static String readSchema(
        URL schema)
    {
        String text;
        try (InputStream input = schema.openStream())
        {
            text = new String(input.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }

        return text;
    }

    private static URL patch(
        JsonObject subjects)
    {
        JsonObject catalog = Json.createObjectBuilder()
            .add("type", "inline")
            .add("options", Json.createObjectBuilder()
                .add("subjects", subjects))
            .build();

        JsonArray patch = Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("op", "add")
                .add("path", "/catalogs/" + CATALOG_NAME)
                .add("value", catalog))
            .build();

        String encoded = Base64.getEncoder().encodeToString(patch.toString().getBytes(UTF_8));

        URL url;
        try
        {
            url = URL.of(URI.create("data:application/json;base64," + encoded), new LlmDataUrlStreamHandler());
        }
        catch (MalformedURLException ex)
        {
            throw new IllegalStateException(ex);
        }

        return url;
    }
}
