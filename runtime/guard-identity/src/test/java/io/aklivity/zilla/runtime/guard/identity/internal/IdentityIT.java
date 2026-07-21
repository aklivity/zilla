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
package io.aklivity.zilla.runtime.guard.identity.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.rules.RuleChain.outerRule;

import org.agrona.collections.MutableLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class IdentityIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/guard/identity/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(k3po).around(engine).around(timeout);

    @Test
    @Configuration("zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${app}/handshake/server" })
    public void shouldReauthorize() throws Exception
    {
        k3po.finish();
    }

    @Test
    public void shouldVerifyIdentityAndRolesWhenAllowAccess() throws Exception
    {
        IdentityGuardHandler guard = new IdentityGuardHandler(new MutableLong(1L)::getAndIncrement, null);

        String token = "authorization-token";
        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo(token));
        assertThat(guard.expiresAt(sessionId), equalTo(Long.MAX_VALUE));
        assertThat(guard.expiringAt(sessionId), equalTo(Long.MAX_VALUE));
        assertThat(guard.credentials(sessionId), equalTo(token));

        guard.deauthorize(sessionId);
    }
}
