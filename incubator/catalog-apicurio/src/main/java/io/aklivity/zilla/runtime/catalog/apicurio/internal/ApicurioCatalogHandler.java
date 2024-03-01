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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteOrder;
import java.text.MessageFormat;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.apicurio.internal.config.ApicurioOptionsConfig;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.serializer.RegisterSchemaRequest;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class ApicurioCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}";
    private static final String REGISTER_SCHEMA_PATH = "/subjects/{0}/versions";

    private final HttpClient client;
    private final String baseUrl;
    private final CRC32C crc32c;
    private final Int2ObjectCache<String> specs;
    private final Int2ObjectCache<CachedSchemaId> specIds;
    private final long maxAgeMillis;
    private final ApicurioEventContext event;
    private final long catalogId;

    public ApicurioCatalogHandler(
        ApicurioOptionsConfig config,
        EngineContext context,
        long catalogId)
    {
        this.baseUrl = config.url;
        this.client = HttpClient.newHttpClient();
        //this.request = new RegisterSchemaRequest();
        this.crc32c = new CRC32C();
        this.specs = new Int2ObjectCache<>(1, 1024, i -> {});
        this.specIds = new Int2ObjectCache<>(1, 1024, i -> {});
        this.maxAgeMillis = config.maxAge.toMillis();
        this.event = new ApicurioEventContext(context);
        this.catalogId = catalogId;
    }

    @Override
    public int register(
        String subject,
        String type,
        String schema)
    {
        return -1;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        String schema = null;
        if (specs.containsKey(schemaId))
        {
            schema = specs.get(schemaId);
        }
        else
        {
            String response = sendHttpRequest(MessageFormat.format(SCHEMA_PATH, schemaId));
            //            schema = response != null ? request.resolveSchemaResponse(response) : null;
            //            if (schema != null)
            //            {
            //                specs.put(schemaId, schema);
            //            }
        }
        return schema;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        int schemaId;

        int checkSum = generateCRC32C(subject, version);
        if (specIds.containsKey(checkSum) &&
            (System.currentTimeMillis() - specIds.get(checkSum).timestamp) < maxAgeMillis)
        {
            schemaId = specIds.get(checkSum).id;
        }
        else
        {
            String response = sendHttpRequest(MessageFormat.format(SUBJECT_VERSION_PATH, subject, version));
            schemaId = response != null ? request.resolveResponse(response) : NO_SCHEMA_ID;
            if (schemaId != NO_SCHEMA_ID)
            {
                specIds.put(checkSum, new CachedSchemaId(System.currentTimeMillis(), schemaId));
            }
        }
        return schemaId;
    }

    @Override
    public int resolve(
        DirectBuffer data,
        int index,
        int length)
    {
        int schemaId = NO_SCHEMA_ID;
        if (data.getByte(index) == MAGIC_BYTE)
        {
            schemaId = data.getInt(index + BitUtil.SIZE_OF_BYTE, ByteOrder.BIG_ENDIAN);
        }
        return schemaId;
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
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            boolean success = httpResponse.statusCode() == 200;
            String responseBody = success ? httpResponse.body() : null;
            if (!success)
            {
                event.remoteAccessRejected(catalogId, httpRequest, httpResponse.statusCode());
            }
            return responseBody;
        }
        catch (Exception ex)
        {
            event.remoteAccessRejected(catalogId, httpRequest, 0);
        }
        return null;
    }

    private URI toURI(
        String baseUrl,
        String path)
    {
        return URI.create(baseUrl).resolve(path);
    }

    private int generateCRC32C(
        String subject,
        String version)
    {
        byte[] bytes = (subject + version).getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }
}
