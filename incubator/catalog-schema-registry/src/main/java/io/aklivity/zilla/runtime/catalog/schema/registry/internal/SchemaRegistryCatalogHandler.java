/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryCatalogConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class SchemaRegistryCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}/schema";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}/schema";

    private final SchemaRegistryCatalogConfig config;
    private final HttpClient client;
    private final String baseUrl;

    public SchemaRegistryCatalogHandler(
        CatalogConfig catalog)
    {
        this.config = SchemaRegistryCatalogConfig.class.cast(catalog.options);
        this.baseUrl = config.url;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public int register(
        String schema,
        String subject)
    {
        return 0;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return sendHttpRequest(MessageFormat.format(SCHEMA_PATH, schemaId));
    }

    @Override
    public String resolve(
        String subject,
        String version)
    {
        return sendHttpRequest(MessageFormat.format(SUBJECT_VERSION_PATH, subject, version));
    }

    private String sendHttpRequest(
        String path)
    {
        try
        {
            HttpRequest httpRequest = HttpRequest
                .newBuilder(toURI(baseUrl, path))
                .GET()
                .build();
            // TODO: introduce interrupt/timeout for request to schema registry
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        }
        catch (Exception e)
        {
            System.out.println("Error fetching schema");
        }
        return null;
    }

    private URI toURI(
        String baseUrl, String path)
    {
        return URI.create(baseUrl + path);
    }
}
