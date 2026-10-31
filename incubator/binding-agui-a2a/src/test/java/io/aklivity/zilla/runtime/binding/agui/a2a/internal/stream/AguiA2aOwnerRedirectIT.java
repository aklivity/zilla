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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.rules.RuleChain.outerRule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aKeyHash;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.router.TestRouter;

public class AguiA2aOwnerRedirectIT
{
    private static final long OWNER_AFFINITY = 0x00000000000000b1L;

    private final List<Long> keyHashes = new CopyOnWriteArrayList<>();

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("agui", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_ROUTER, TestRouter.NAME)
        .configurationRoot("io/aklivity/zilla/runtime/binding/agui/a2a/internal/stream")
        .clean();

    private final ExternalResource router = new ExternalResource()
    {
        @Override
        protected void before()
        {
            TestRouter.affinityResolver = (keyHash, affinity) ->
            {
                keyHashes.add(keyHash);
                return OWNER_AFFINITY;
            };
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
    @Configuration("proxy.route.identity.yaml")
    @Specification({
        "${agui}/owner.redirect.begin/client"})
    public void shouldRedirectRunToOwnerKeyedOnGuardIdentity() throws Exception
    {
        k3po.finish();

        assertThat(keyHashes, contains(AguiA2aKeyHash.hash("test", "weather", "thread-1")));
    }
}
