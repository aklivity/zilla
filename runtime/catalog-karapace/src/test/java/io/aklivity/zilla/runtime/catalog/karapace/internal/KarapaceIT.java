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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.catalog.karapace.internal.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class KarapaceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("local", "io/aklivity/zilla/runtime/catalog/karapace/internal");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    private KarapaceOptionsConfig config;
    private EngineContext context = mock(EngineContext.class);

    @Before
    public void setup()
    {
        config = KarapaceOptionsConfig.builder()
            .url("http://localhost:8081")
            .context("default")
            .maxAge(Duration.ofSeconds(1))
            .build();
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaId() throws Exception
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        String schema = catalog.resolve(9);

        k3po.finish();

        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaViaSubjectVersion() throws Exception
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        int schemaId = catalog.resolve("items-snapshots-value", "latest");

        String schema = catalog.resolve(schemaId);

        k3po.finish();

        assertEquals(schemaId, 9);
        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaIdFromCache() throws Exception
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        catalog.resolve(9);

        k3po.finish();

        String schema = catalog.resolve(9);

        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaViaSubjectVersionFromCache() throws Exception
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        catalog.resolve(catalog.resolve("items-snapshots-value", "latest"));

        k3po.finish();

        int schemaId = catalog.resolve("items-snapshots-value", "latest");

        String schema = catalog.resolve(schemaId);

        assertEquals(schemaId, 9);
        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.schema.id.failed"})
    public void shouldLogFailedRegistryResponseForSchema() throws Exception
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        KarapaceCache cache = new KarapaceCache();
        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L, cache);

        String schema = catalog.resolve(1);

        k3po.finish();

        assertEquals(schema, null);
        assertEquals(cache.schemas.get(1).get().retryAttempts.get(), 1);
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.subject.version.failed"})
    public void shouldServeStaleSchemaIdFromCacheAfterFailedRegistryResponseForId() throws Exception
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        KarapaceCache cache = new KarapaceCache();
        CompletableFuture<CachedSchemaId> future = new CompletableFuture<>();
        future.complete(new CachedSchemaId(0L, 1, new AtomicInteger(0), 0L));
        cache.schemaIds.put(-754089167, future);
        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L, cache);

        int schemaId = catalog.resolve("items-snapshots-value", "latest");

        k3po.finish();

        assertEquals(schemaId, 1);

        for (int schemaKey: cache.schemaIds.keySet())
        {
            assertEquals(cache.schemaIds.get(schemaKey).get().retryAfter, 1000L);
            assertEquals(cache.schemaIds.get(schemaKey).get().retryAttempts.get(), 1);
        }
    }

    @Test
    @Specification({
        "${local}/resolve.schema.via.subject.version" })
    public void shouldRetryToResolveSchemaViaSubjectVersionFromCache() throws Exception
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        KarapaceCache cache = new KarapaceCache();
        CompletableFuture<CachedSchemaId> future = new CompletableFuture<>();
        future.complete(new CachedSchemaId(System.currentTimeMillis(), 1, new AtomicInteger(2), 2000L));
        cache.schemaIds.put(-754089167, future);

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L, cache);

        int schemaId = catalog.resolve("items-snapshots-value", "latest");

        Thread.sleep(2000);

        assertEquals(schemaId, 1);

        k3po.start();

        schemaId = catalog.resolve("items-snapshots-value", "latest");

        String schema = catalog.resolve(schemaId);

        k3po.finish();

        assertEquals(schemaId, 9);
        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    public void shouldVerifyMaxPadding()
    {
        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        assertEquals(5, catalog.encodePadding());
    }

    @Test
    public void shouldVerifyEncodedData()
    {
        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(18, catalog.encode(0L, 0L, 1, data, 0, data.capacity(),
            ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY));
    }

    @Test
    public void shouldResolveSchemaIdAndProcessData()
    {

        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int valLength = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity() - 5, valLength);
    }

    @Test
    public void shouldResolveSchemaIdFromData()
    {
        KarapaceCatalogHandler catalog = new KarapaceCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int schemaId = catalog.resolve(data, 0, data.capacity());

        assertEquals(9, schemaId);
    }
}
