/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer.RegisterSchemaRequest;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.catalog.ParsedSchema;

public class SchemaRegistryCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}";
    private static final String REGISTER_SCHEMA_PATH = "/subjects/{0}/versions";

    private final HttpClient client;
    private final String baseUrl;
    private final RegisterSchemaRequest request;

    public SchemaRegistryCatalogHandler(
        SchemaRegistryOptionsConfig config)
    {
        this.baseUrl = config.url;
        this.client = HttpClient.newHttpClient();
        this.request = new RegisterSchemaRequest();
    }

    @Override
    public int register(
        String subject,
        String type,
        String schema)
    {
        int schemaId = 0;
        HttpRequest httpRequest = HttpRequest
            .newBuilder(toURI(baseUrl, MessageFormat.format(REGISTER_SCHEMA_PATH, subject)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.buildBody(type, schema)))
            .build();
        try
        {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            schemaId = response.statusCode() == 200 ? request.resolveResponse(response.body()) : NO_SCHEMA_ID;
        }
        catch (Exception ex)
        {
            ex.printStackTrace(System.out);
        }
        return schemaId;
    }

    @Override
    public ParsedSchema resolve(
        int schemaId)
    {
        String response =  sendHttpRequest(MessageFormat.format(SCHEMA_PATH, schemaId));
        return response != null ? request.resolveSchemaResponse(schemaId, response) : null;
    }

    @Override
    public ParsedSchema resolve(
        String subject,
        String version)
    {
        String response = sendHttpRequest(MessageFormat.format(SUBJECT_VERSION_PATH, subject, version));
        return response != null ? request.resolveSchemaResponse(response) : null;
    }

    private String sendHttpRequest(
        String path)
    {
        HttpRequest httpRequest = HttpRequest
                .newBuilder(toURI(baseUrl, path))
                .GET()
                .build();
        // TODO: introduce interrupt/timeout for request to schema registry

        try
        {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        }
        catch (Exception ex)
        {
            ex.printStackTrace(System.out);
        }
        return null;
    }

    private URI toURI(
        String baseUrl,
        String path)
    {
        return URI.create(baseUrl).resolve(path);
    }
}
