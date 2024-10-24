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
package io.aklivity.zilla.runtime.guard.jwt.internal;

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

public class JwtGuardIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("keys", "io/aklivity/zilla/specs/guard/jwt/config/keys");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .countersBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/guard/jwt/config")
            .clean();

    @Rule
    public final TestRule chain = outerRule(k3po).around(engine).around(timeout);

    @Test
    @Configuration("guard.yaml")
    public void shouldInitialize() throws Exception
    {
    }

    @Test
    @Configuration("guard-keys-dynamic.yaml")
    @Specification({
        "${keys}/issuer"
    })
    public void shouldInitializeGuardWithDynamicKeys() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("guard-keys-implicit.yaml")
    @Specification({
        "${keys}/issuer"
    })
    public void shouldInitializeGuardWithImplicitKeys() throws Exception
    {
        k3po.finish();
    }
}
