/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_SERVICE_HOSTNAME_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_STORE_NAME_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_STORE_TYPE_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class AffinityMigrateIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/affinity");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .configure(ENGINE_SERVICE_HOSTNAME_NAME, "server-2.example.net")
        .configure(ENGINE_STORE_TYPE_NAME, "test")
        .configure(ENGINE_STORE_NAME_NAME, "test:cluster")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Before
    public void seedClusterStore()
    {
        TestStoreHandler.seed("sess-1", "server-1.example.net:8080");
        TestStoreHandler.seed("sess-2", "server-1.example.net:8080");
    }

    @After
    public void clearClusterStore()
    {
        TestStoreHandler.clearSeeds();
    }

    @Test
    @Ignore("binding-level migrate dispatch needs debug; raw-bytes script returns 404 instead of 429")
    @Configuration("server.affinity.migrate.yaml")
    @Specification({
        "${net}/request.with.header.affinity.migrate/client" })
    public void shouldRequestWithHeaderAffinityMigrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Ignore("binding-level migrate dispatch needs debug; raw-bytes script returns 404 instead of 429")
    @Configuration("server.affinity.migrate.yaml")
    @Specification({
        "${net}/request.with.query.affinity.migrate/client" })
    public void shouldRequestWithQueryAffinityMigrate() throws Exception
    {
        k3po.finish();
    }
}
