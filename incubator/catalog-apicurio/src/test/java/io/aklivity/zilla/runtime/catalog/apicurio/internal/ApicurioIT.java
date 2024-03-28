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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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

import io.aklivity.zilla.runtime.catalog.apicurio.internal.config.ApicurioOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class ApicurioIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("local", "io/aklivity/zilla/runtime/catalog/schema/registry/internal");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
    @Specification({
        "${local}/resolve.artifact.via.global.id" })
    public void shouldResolveArtifactViaGlobalId() throws Exception
    {
        String expected = "asyncapi: 3.0.0\n" +
            "info:\n" +
            "  title: Zilla MQTT Proxy\n" +
            "  version: 1.0.0\n" +
            "  license:\n" +
            "    name: Aklivity Community License\n" +
            "servers:\n" +
            "  plain:\n" +
            "    host: mqtt://localhost:7183\n" +
            "    protocol: mqtt\n" +
            "defaultContentType: application/json";

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        String artifact = catalog.resolve(1);

        k3po.finish();

        assertThat(artifact, not(nullValue()));
        assertEquals(expected, artifact);
    }

    @Test
    @Specification({
        "${local}/resolve.artifact.via.artifactid.version" })
    public void shouldResolveArtifactViaArtifactIdVersion() throws Exception
    {
        String expected = "asyncapi: 3.0.0\n" +
            "info:\n" +
            "  title: Zilla MQTT Proxy\n" +
            "  version: 1.0.0\n" +
            "  license:\n" +
            "    name: Aklivity Community License\n" +
            "servers:\n" +
            "  plain:\n" +
            "    host: mqtt://localhost:7183\n" +
            "    protocol: mqtt\n" +
            "defaultContentType: application/json";

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        int globalId = catalog.resolve("artifactId", "0");

        String artifact = catalog.resolve(globalId);

        k3po.finish();

        assertEquals(globalId, 1);
        assertThat(artifact, not(nullValue()));
        assertEquals(expected, artifact);
    }

    @Test
    @Specification({
        "${local}/resolve.artifact.via.global.id" })
    public void shouldResolveArtifactViaGlobalIdFromCache() throws Exception
    {
        String expected = "asyncapi: 3.0.0\n" +
            "info:\n" +
            "  title: Zilla MQTT Proxy\n" +
            "  version: 1.0.0\n" +
            "  license:\n" +
            "    name: Aklivity Community License\n" +
            "servers:\n" +
            "  plain:\n" +
            "    host: mqtt://localhost:7183\n" +
            "    protocol: mqtt\n" +
            "defaultContentType: application/json";

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        catalog.resolve(1);

        k3po.finish();

        String artifact = catalog.resolve(1);

        assertThat(artifact, not(nullValue()));
        assertEquals(expected, artifact);
    }

    @Test
    @Specification({
        "${local}/resolve.artifact.via.artifactid.version" })
    public void shouldResolveArtifactViaArtifactIdVersionFromCache() throws Exception
    {
        String expected = "asyncapi: 3.0.0\n" +
            "info:\n" +
            "  title: Zilla MQTT Proxy\n" +
            "  version: 1.0.0\n" +
            "  license:\n" +
            "    name: Aklivity Community License\n" +
            "servers:\n" +
            "  plain:\n" +
            "    host: mqtt://localhost:7183\n" +
            "    protocol: mqtt\n" +
            "defaultContentType: application/json";

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        catalog.resolve(catalog.resolve("artifactId", "0"));

        k3po.finish();

        int globalId = catalog.resolve("artifactId", "0");

        String artifact = catalog.resolve(globalId);

        assertEquals(1, globalId);
        assertThat(artifact, not(nullValue()));
        assertEquals(expected, artifact);
    }

    @Test
    public void shouldVerifyMaxPadding()
    {
        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        assertEquals(9, catalog.encodePadding());
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
