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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class OpenapiAsyncapiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("openapi", "io/aklivity/zilla/specs/binding/openapi/asyncapi/streams/openapi")
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/openapi/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/openapi/asyncapi/config")
        .external("asyncapi_client0")
        .configure(EngineConfiguration.ENGINE_VERBOSE, true)
        .configure(EngineConfiguration.ENGINE_VERBOSE_COMPOSITES, true)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${openapi}/create.pet/client",
        "${asyncapi}/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy-async.yaml")
    @Specification({
        "${openapi}/async.verify.customer/client",
        "${asyncapi}/async.verify.customer/server"
    })
    public void shouldVerifyCustomerAsync() throws Exception
    {
        k3po.finish();
    }
}
