/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.store.memory.internal;

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

public class MemoryStoreHandlerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/store/memory/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("store.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server"})
    public void shouldHandshakeWithMemoryStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("store.lock.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server"})
    public void shouldLockResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("store.unlock.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server"})
    public void shouldUnlockAbsentResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("store.renew.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server"})
    public void shouldRenewOwnedLock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("store.watch.yaml")
    @Specification({
        "${net}/store.watch/client",
        "${app}/store.watch/server"})
    public void shouldWatchKey() throws Exception
    {
        k3po.finish();
    }
}
