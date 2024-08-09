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

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.catalog.apicurio.config.ApicurioOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ApicurioCatalogIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application")
        .addScriptRoot("remote", "io/aklivity/zilla/specs/catalog/apicurio/streams");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/catalog/apicurio/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    private ApicurioOptionsConfig config;
    private EngineContext context = mock(EngineContext.class);

    @Before
    public void setup()
    {
        config = ApicurioOptionsConfig.builder()
            .url("http://localhost:8080")
            .groupId("groupId")
            .maxAge(Duration.ofSeconds(1))
            .build();
    }

    @Test
    @Configuration("resolve/artifact/global/id/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.global.id" })
    public void shouldResolveArtifactViaGlobalId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/id/subject/version/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.artifactid.version" })
    public void shouldResolveArtifactIdViaSubjectAndVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/id/subject/version/latest/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.latest.version" })
    public void shouldResolveArtifactIdViaSubjectAndLatestVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/global/id/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.global.id" })
    public void shouldResolveArtifactViaGlobalIdFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/id/subject/version/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.artifactid.version" })
    public void shouldResolveArtifactIdViaSubjectAndVersionFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/global/id/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.global.id.retry" })
    public void shouldResolveArtifactViaGlobalIdRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/id/subject/version/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.artifactid.version.retry" })
    public void shouldResolveArtifactIdViaSubjectAndVersionFromCacheAndRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/artifact/id/subject/version/failed/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.artifact.via.artifactid.version.failed" })
    public void shouldResolveArtifactIdViaSubjectAndVersionFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    public void shouldVerifyMaxPadding()
    {
        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        assertEquals(9, catalog.encodePadding(0));
    }

    @Test
    public void shouldVerifyEncodedData()
    {
        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

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

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int valLength = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity() - 9, valLength);
    }

    @Test
    public void shouldResolveSchemaIdFromData()
    {
        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int schemaId = catalog.resolve(data, 0, data.capacity());

        assertEquals(9, schemaId);
    }
}
