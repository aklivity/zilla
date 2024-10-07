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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class SchemaRegistryIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application")
        .addScriptRoot("remote", "io/aklivity/zilla/specs/catalog/schema/registry/streams");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/catalog/schema/registry/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("resolve/schema/id/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaIdViaSubjectVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/schema/id/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.schema.id" })
    public void shouldResolveSchemaViaSchemaIdFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/cache/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.subject.version" })
    public void shouldResolveSchemaIdViaSubjectVersionFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("unretrievable/schema/id/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.schema.id.failed"})
    public void shouldLogFailedRegistryResponseForSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("unretrievable/schema/subject/version/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.subject.version.failed"})
    public void shouldLogFailedRegistryResponseForSchemaId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/schema/id/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.schema.id.on.retry" })
    public void shouldResolveSchemaViaSchemaIdOnRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resolve/subject/version/retry/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/resolve.schema.via.subject.version.retry"})
    public void shouldResolveSchemaIdFromCacheAndRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("register/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/register.schema" })
    public void shouldRegisterSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("unregister/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server",
        "${remote}/unregister.schema" })
    public void shouldUnregisterSchema() throws Exception
    {
        k3po.finish();
    }
}
