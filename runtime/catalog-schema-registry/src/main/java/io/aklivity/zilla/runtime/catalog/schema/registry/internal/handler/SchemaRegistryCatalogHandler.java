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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler;

import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.CachedSchemaId.IN_PROGRESS;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteOrder;
import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryCatalogConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.events.SchemaRegistryEventContext;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer.RegisterSchemaRequest;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.SchemaRegistryPrefixFW;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class SchemaRegistryCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}";
    private static final String REGISTER_SUBJECT_PATH = "/subjects/{0}/versions";
    private static final String UNREGISTER_SUBJECT_PATH = "/subjects/{0}";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}";

    private static final int MAX_PADDING_LENGTH = 5;
    private static final byte MAGIC_BYTE = 0x0;
    private static final long RESET_RETRY_DELAY_MS_DEFAULT = 0L;
    private static final long RETRY_INITIAL_DELAY_MS_DEFAULT = 1000L;

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
    private final ConcurrentMap<Integer, CompletableFuture<CachedSchema>> cachedSchemas;
    private final ConcurrentMap<Integer, CompletableFuture<CachedSchemaId>> cachedSchemaIds;

    public SchemaRegistryCatalogHandler(
        SchemaRegistryCatalogConfig catalog)
    {
        this.baseUrl = catalog.options.url;
        this.client = HttpClient.newHttpClient();
        this.request = new RegisterSchemaRequest();
        this.crc32c = new CRC32C();
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.schemaIds = new Int2ObjectCache<>(1, 1024, i -> {});
        this.maxAgeMillis = catalog.options.maxAge.toMillis();
        this.event = catalog.events;
        this.catalogId = catalog.id;
        this.cachedSchemas = catalog.cache.schemas;
        this.cachedSchemaIds = catalog.cache.schemaIds;
    }

    @Override
    public int register(
        String subject,
        String schema)
    {
        int versionId = NO_VERSION_ID;

        String response = sendPostHttpRequest(MessageFormat.format(REGISTER_SUBJECT_PATH, subject), schema);
        if (response != null)
        {
            versionId = request.resolveResponse(response);
        }

        return versionId;
    }

    @Override
    public void unregister(
        String subject)
    {
        String response = sendDeleteHttpRequest(MessageFormat.format(UNREGISTER_SUBJECT_PATH, subject));
        if (response != null)
        {
            request.resolveResponse(response);
        }
    }

    @Override
    public String resolve(
        int schemaId)
    {
        String schema = null;
        if (schemaId != NO_SCHEMA_ID)
        {
            if (schemas.containsKey(schemaId))
            {
                schema = schemas.get(schemaId);
            }
            else
            {
                AtomicInteger retryAttempts = new AtomicInteger();
                CompletableFuture<CachedSchema> newFuture = new CompletableFuture<>();
                CompletableFuture<CachedSchema> existing = cachedSchemas.get(schemaId);
                if (existing != null && existing.isDone())
                {
                    try
                    {
                        CachedSchema cachedSchema = existing.get();
                        if (cachedSchema != null)
                        {
                            retryAttempts = cachedSchema.retryAttempts;
                        }
                    }
                    catch (Throwable ex)
                    {
                        existing.completeExceptionally(ex);
                    }
                }
                CompletableFuture<CachedSchema> future = cachedSchemas.merge(schemaId, newFuture, (v1, v2) ->
                    v1.getNow(CachedSchema.IN_PROGRESS).schema == null ? v2 : v1);
                if (future == newFuture)
                {
                    try
                    {
                        String response = sendHttpRequest(MessageFormat.format(SCHEMA_PATH, schemaId));
                        if (response == null)
                        {
                            if (retryAttempts.getAndIncrement() == 0)
                            {
                                event.onUnretrievableSchemaId(catalogId, schemaId);
                            }
                            newFuture.complete(new CachedSchema(null, retryAttempts));
                        }
                        else
                        {
                            if (retryAttempts.getAndSet(0) > 0)
                            {
                                event.onRetrievableSchemaId(catalogId, schemaId);
                            }
                            newFuture.complete(new CachedSchema(request.resolveSchemaResponse(response), retryAttempts));
                        }
                    }
                    catch (Throwable ex)
                    {
                        newFuture.completeExceptionally(ex);
                    }
                }
                assert future != null;
                try
                {
                    schema = future.get().schema;
                    if (schema != null)
                    {
                        schemas.put(schemaId, schema);
                    }
                }
                catch (Throwable ex)
                {
                    future.completeExceptionally(ex);
                }
            }
        }
        return schema;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        int schemaId = NO_SCHEMA_ID;

        int schemaKey = generateCRC32C(subject, version);
        if (schemaIds.containsKey(schemaKey) && !schemaIds.get(schemaKey).expired(maxAgeMillis))
        {
            schemaId = schemaIds.get(schemaKey).id;
        }
        else
        {
            CachedSchemaId cachedSchemaId = null;
            AtomicInteger retryAttempts = new AtomicInteger();
            long retryAfter = RESET_RETRY_DELAY_MS_DEFAULT;
            CompletableFuture<CachedSchemaId> newFuture = new CompletableFuture<>();
            CompletableFuture<CachedSchemaId> existing = cachedSchemaIds.get(schemaKey);
            if (existing != null && existing.isDone())
            {
                try
                {
                    cachedSchemaId = existing.get();
                    if (cachedSchemaId != null)
                    {
                        retryAttempts = cachedSchemaId.retryAttempts;
                    }
                }
                catch (Throwable ex)
                {
                    existing.completeExceptionally(ex);
                }
            }
            CompletableFuture<CachedSchemaId> future = cachedSchemaIds.merge(schemaKey, newFuture, (v1, v2) ->
                v1.getNow(IN_PROGRESS).retry() &&
                    (v1.getNow(IN_PROGRESS).id == NO_SCHEMA_ID || v1.getNow(IN_PROGRESS).expired(maxAgeMillis)) ? v2 : v1);
            if (future == newFuture)
            {
                try
                {
                    String response = sendHttpRequest(MessageFormat.format(SUBJECT_VERSION_PATH, subject, version));
                    if (response == null)
                    {
                        if (retryAttempts.getAndIncrement() == 0)
                        {
                            retryAfter = RETRY_INITIAL_DELAY_MS_DEFAULT;
                            event.onUnretrievableSchemaSubjectVersion(catalogId, subject, version);
                            if (cachedSchemaId != null && cachedSchemaId.id != NO_SCHEMA_ID)
                            {
                                event.onUnretrievableSchemaSubjectVersionStaleSchema(catalogId, subject, version,
                                    cachedSchemaId.id);
                            }
                        }

                        if (cachedSchemaId != null)
                        {
                            if (cachedSchemaId.retryAfter != RESET_RETRY_DELAY_MS_DEFAULT)
                            {
                                retryAfter = Math.min(cachedSchemaId.retryAfter << 1, maxAgeMillis);
                            }
                            newFuture.complete(new CachedSchemaId(cachedSchemaId.timestamp, cachedSchemaId.id,
                                retryAttempts, retryAfter));
                        }
                        else
                        {
                            newFuture.complete(new CachedSchemaId(System.currentTimeMillis(), NO_SCHEMA_ID,
                                retryAttempts, retryAfter));
                        }
                    }
                    else if (response != null)
                    {
                        if (retryAttempts.getAndSet(0) > 0)
                        {
                            event.onRetrievableSchemaSubjectVersion(catalogId, subject, version);
                        }
                        newFuture.complete(new CachedSchemaId(System.currentTimeMillis(), request.resolveResponse(response),
                            retryAttempts, retryAfter));
                    }
                }
                catch (Throwable ex)
                {
                    newFuture.completeExceptionally(ex);
                }
            }
            assert future != null;
            try
            {
                cachedSchemaId = future.get();
                schemaId = cachedSchemaId.id;
                if (schemaId != NO_SCHEMA_ID)
                {
                    schemaIds.put(schemaKey, cachedSchemaId);
                }
            }
            catch (Throwable ex)
            {
                future.completeExceptionally(ex);
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
    public int encodePadding(
        int length)
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

        String responseBody;
        try
        {
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            responseBody = httpResponse.statusCode() == 200 ? httpResponse.body() : null;
        }
        catch (Exception ex)
        {
            responseBody = null;
        }
        return responseBody;
    }

    private String sendPostHttpRequest(
        String path,
        String body)
    {
        HttpRequest httpRequest = HttpRequest
            .newBuilder(toURI(baseUrl, path))
            .version(HttpClient.Version.HTTP_1_1)
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        // TODO: introduce interrupt/timeout for request to schema registry

        String responseBody;
        try
        {
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            responseBody = httpResponse.statusCode() == 200 ? httpResponse.body() : null;
        }
        catch (Exception ex)
        {
            responseBody = null;
        }
        return responseBody;
    }

    private String sendDeleteHttpRequest(
        String path)
    {
        HttpRequest httpRequest = HttpRequest
            .newBuilder(toURI(baseUrl, path))
            .version(HttpClient.Version.HTTP_1_1)
            .DELETE()
            .build();

        String responseBody;
        try
        {
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            responseBody = httpResponse.statusCode() == 200 ? httpResponse.body() : null;
        }
        catch (Exception ex)
        {
            responseBody = null;
        }
        return responseBody;
    }

    @Override
    public String location()
    {
        return baseUrl;
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
