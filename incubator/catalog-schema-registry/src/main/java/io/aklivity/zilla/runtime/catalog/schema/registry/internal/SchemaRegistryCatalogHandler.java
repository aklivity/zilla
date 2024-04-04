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
import java.nio.ByteOrder;
import java.text.MessageFormat;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer.RegisterSchemaRequest;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.SchemaRegistryPrefixFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class SchemaRegistryCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}";
    private static final String REGISTER_SCHEMA_PATH = "/subjects/{0}/versions";
    private static final int MAX_PADDING_LENGTH = 5;
    private static final byte MAGIC_BYTE = 0x0;

    private final SchemaRegistryPrefixFW.Builder prefixRW = new SchemaRegistryPrefixFW.Builder()
        .wrap(new UnsafeBuffer(new byte[5]), 0, 5);

    private final HttpClient client;
    private final String baseUrl;
    private final RegisterSchemaRequest request;
    private final CRC32C crc32c;
    private final Int2ObjectCache<String> schemas;
    private final Int2ObjectCache<CachedSchemaId> schemaIds;
    private final long maxAgeMillis;
    private final SchemaRegistryEventContext event;
    private final long catalogId;

    public SchemaRegistryCatalogHandler(
        SchemaRegistryOptionsConfig config,
        EngineContext context,
        long catalogId)
    {
        this.baseUrl = config.url;
        this.client = HttpClient.newHttpClient();
        this.request = new RegisterSchemaRequest();
        this.crc32c = new CRC32C();
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.schemaIds = new Int2ObjectCache<>(1, 1024, i -> {});
        this.maxAgeMillis = config.maxAge.toMillis();
        this.event = new SchemaRegistryEventContext(context);
        this.catalogId = catalogId;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        String schema;
        if (schemas.containsKey(schemaId))
        {
            schema = schemas.get(schemaId);
        }
        else
        {
            String response = sendHttpRequest(MessageFormat.format(SCHEMA_PATH, schemaId));
            schema = response != null ? request.resolveSchemaResponse(response) : null;
            if (schema != null)
            {
                schemas.put(schemaId, schema);
            }
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
        if (schemaIds.containsKey(checkSum) &&
            (System.currentTimeMillis() - schemaIds.get(checkSum).timestamp) < maxAgeMillis)
        {
            schemaId = schemaIds.get(checkSum).id;
        }
        else
        {
            String response = sendHttpRequest(MessageFormat.format(SUBJECT_VERSION_PATH, subject, version));
            schemaId = response != null ? request.resolveResponse(response) : NO_SCHEMA_ID;
            if (schemaId != NO_SCHEMA_ID)
            {
                schemaIds.put(checkSum, new CachedSchemaId(System.currentTimeMillis(), schemaId));
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

    @Override
    public int decode(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Decoder decoder)
    {
        int schemaId = NO_SCHEMA_ID;
        int progress = 0;
        int valLength = -1;
        if (data.getByte(index) == MAGIC_BYTE)
        {
            progress += BitUtil.SIZE_OF_BYTE;
            schemaId = data.getInt(index + progress, ByteOrder.BIG_ENDIAN);
            progress += BitUtil.SIZE_OF_INT;
        }

        if (schemaId > NO_SCHEMA_ID)
        {
            valLength = decoder.accept(traceId, bindingId, schemaId, data, index + progress, length - progress, next);
        }
        return valLength;
    }

    @Override
    public int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Encoder encoder)
    {
        SchemaRegistryPrefixFW prefix = prefixRW.rewrap().schemaId(schemaId).build();
        next.accept(prefix.buffer(), prefix.offset(), prefix.sizeof());
        int valLength = encoder.accept(traceId, bindingId, schemaId, data, index, length, next);
        return valLength > 0 ? prefix.sizeof() + valLength : -1;
    }

    @Override
    public int encodePadding()
    {
        return MAX_PADDING_LENGTH;
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
