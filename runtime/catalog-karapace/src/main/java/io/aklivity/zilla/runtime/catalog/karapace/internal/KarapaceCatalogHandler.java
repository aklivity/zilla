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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import static io.aklivity.zilla.runtime.catalog.karapace.internal.CachedSchemaId.IN_PROGRESS;

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

import io.aklivity.zilla.runtime.catalog.karapace.internal.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.catalog.karapace.internal.serializer.RegisterSchemaRequest;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.KarapacePrefixFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class KarapaceCatalogHandler implements CatalogHandler
{
    private static final String SUBJECT_VERSION_PATH = "/subjects/{0}/versions/{1}";
    private static final String SCHEMA_PATH = "/schemas/ids/{0}";
    private static final String REGISTER_SCHEMA_PATH = "/subjects/{0}/versions";
    private static final int MAX_PADDING_LENGTH = 5;
    private static final byte MAGIC_BYTE = 0x0;
    public static final long RESET_RETRY_DELAY_MS_DEFAULT = 0L;
    public static final long RETRY_INITIAL_DELAY_MS_DEFAULT = 1000L;
    public static final int RETRY_MULTIPLER_DEFAULT = 2;

    private final KarapacePrefixFW.Builder prefixRW = new KarapacePrefixFW.Builder()
        .wrap(new UnsafeBuffer(new byte[5]), 0, 5);

    private final HttpClient client;
    private final String baseUrl;
    private final RegisterSchemaRequest request;
    private final CRC32C crc32c;
    private final Int2ObjectCache<String> schemas;
    private final Int2ObjectCache<CachedSchemaId> schemaIds;
    private final long maxAgeMillis;
    private final KarapaceEventContext event;
    private final long catalogId;
    private final ConcurrentMap<Integer, CompletableFuture<CachedSchema>> cachedSchemas;
    private final ConcurrentMap<Integer, CompletableFuture<CachedSchemaId>> cachedSchemaIds;

    public KarapaceCatalogHandler(
        KarapaceOptionsConfig config,
        EngineContext context,
        long catalogId)
    {
        this(config, context, catalogId, new KarapaceCache());
    }

    public KarapaceCatalogHandler(
        KarapaceOptionsConfig config,
        EngineContext context,
        long catalogId,
        KarapaceCache cache)
    {
        this.baseUrl = config.url;
        this.client = HttpClient.newHttpClient();
        this.request = new RegisterSchemaRequest();
        this.crc32c = new CRC32C();
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.schemaIds = new Int2ObjectCache<>(1, 1024, i -> {});
        this.maxAgeMillis = config.maxAge.toMillis();
        this.event = new KarapaceEventContext(context);
        this.catalogId = catalogId;
        this.cachedSchemas = cache.schemas;
        this.cachedSchemaIds = cache.schemaIds;
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
                AtomicInteger eventStatus = new AtomicInteger();
                CompletableFuture<CachedSchema> newFuture = new CompletableFuture<>();
                CompletableFuture<CachedSchema> existing = cachedSchemas.get(schemaId);
                if (existing != null && existing.isDone())
                {
                    try
                    {
                        CachedSchema cachedSchema = existing.get();
                        if (cachedSchema != null)
                        {
                            eventStatus = cachedSchema.event;
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
                        if (response == null && eventStatus.getAndIncrement() == 0)
                        {
                            event.unretrievableSchemaId(catalogId, schemaId);
                        }
                        else if (response != null && eventStatus.getAndSet(0) > 0)
                        {
                            event.retrievableSchemaId(catalogId, schemaId);
                        }
                        newFuture.complete(new CachedSchema(response != null ? request.resolveSchemaResponse(response) : null,
                            eventStatus));
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
            AtomicInteger eventStatus = new AtomicInteger();
            long retry = RESET_RETRY_DELAY_MS_DEFAULT;
            CompletableFuture<CachedSchemaId> newFuture = new CompletableFuture<>();
            CompletableFuture<CachedSchemaId> existing = cachedSchemaIds.get(schemaKey);
            if (existing != null && existing.isDone())
            {
                try
                {
                    cachedSchemaId = existing.get();
                    if (cachedSchemaId != null)
                    {
                        eventStatus = cachedSchemaId.event;
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
                        if (eventStatus.getAndIncrement() == 0)
                        {
                            retry = RETRY_INITIAL_DELAY_MS_DEFAULT;
                            event.unretrievableSchemaSubjectVersion(catalogId, subject, version);
                        }

                        if (cachedSchemaId != null && cachedSchemaId.retry != RESET_RETRY_DELAY_MS_DEFAULT)
                        {
                            long current = cachedSchemaId.retry;
                            long update = current * RETRY_MULTIPLER_DEFAULT;
                            retry = update < maxAgeMillis ? update : current;
                        }
                    }
                    else if (response != null && eventStatus.getAndSet(0) > 0)
                    {
                        event.retrievableSchemaSubjectVersion(catalogId, subject, version);
                    }
                    newFuture.complete(new CachedSchemaId(System.currentTimeMillis(),
                        response != null ? request.resolveResponse(response) :
                            cachedSchemaId != null ? cachedSchemaId.id : NO_SCHEMA_ID, eventStatus, retry));
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
                    if (cachedSchemaId.event.getAndIncrement() == 1)
                    {
                        event.unretrievableSchemaSubjectVersionStaleSchema(catalogId, subject, version, schemaId);
                    }
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
        KarapacePrefixFW prefix = prefixRW.rewrap().schemaId(schemaId).build();
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
            return responseBody;
        }
        catch (Exception ex)
        {
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
