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
package io.aklivity.zilla.runtime.binding.openapi.internal.streams;

import static io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiConfigurationTest.OPENAPI_TARGET_ROUTE_ID_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class OpenapiClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/openapi/streams/http")
        .addScriptRoot("openapi", "io/aklivity/zilla/specs/binding/openapi/streams/openapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/openapi/config")
        .external("http0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${openapi}/create.pet/client",
        "${http}/create.pet/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/http0\"")
    @Configure(name = OPENAPI_TARGET_ROUTE_ID_NAME, value = "4294967298")
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.multiple.specs.yaml")
    @Specification({
        "${openapi}/create.pet.and.item/client",
        "${http}/create.pet.and.item/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/http0\"")
    @Configure(name = OPENAPI_TARGET_ROUTE_ID_NAME, value = "4294967298")
    public void shouldCreatePetAndItem() throws Exception
    {
        k3po.finish();
    }
}
