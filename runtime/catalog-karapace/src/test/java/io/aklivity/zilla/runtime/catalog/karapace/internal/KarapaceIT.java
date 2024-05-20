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
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;
import static org.mockito.Mockito.mock;

import java.time.Duration;

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
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class KarapaceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application")
        .addScriptRoot("local", "io/aklivity/zilla/runtime/catalog/karapace/internal");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/catalog/karapace/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

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
    @Configuration("resolve/schema/id/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaIdViaSubjectVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/schema/id/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaIdFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaIdViaSubjectVersionFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("unretrievable/schema/id/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.schema.id.failed"})
    public void shouldLogFailedRegistryResponseForSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("unretrievable/schema/subject/version/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.subject.version.failed"})
    public void shouldLogFailedRegistryResponseForSchemaId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/schema/id/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.schema.id.on.retry" })
    public void shouldResolveSchemaViaSchemaIdOnRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${local}/resolve.schema.via.subject.version.retry"})
    public void shouldResolveSchemaIdFromCacheAndRetry() throws Exception
    {
        k3po.finish();
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
