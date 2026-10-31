/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfigurationTest.AGUI_A2A_HANDLE_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.router.TestRouter;

public class AguiA2aOwnerRedirectRunIT
{
    private static final long OWNER_AFFINITY = 0x00000000000000b1L;

    private final AtomicInteger resolutions = new AtomicInteger();

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("agui", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui")
        .addScriptRoot("agent", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_ROUTER, TestRouter.NAME)
        .configure(AGUI_A2A_HANDLE_ID_NAME, "%s::handleId".formatted(AguiA2aProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/runtime/binding/agui/a2a/internal/stream")
        .external("a2a0")
        .clean();

    private final ExternalResource router = new ExternalResource()
    {
        @Override
        protected void before()
        {
            TestRouter.affinityResolver = (keyHash, affinity) ->
                resolutions.getAndIncrement() == 0 ? OWNER_AFFINITY : affinity;
        }

        @Override
        protected void after()
        {
            TestRouter.affinityResolver = null;
        }
    };

    @Rule
    public final TestRule chain = outerRule(router).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.redirect/client",
        "${agent}/owner.redirect/server"})
    public void shouldRedirectRunToOwnerThenRun() throws Exception
    {
        k3po.finish();
    }
}
