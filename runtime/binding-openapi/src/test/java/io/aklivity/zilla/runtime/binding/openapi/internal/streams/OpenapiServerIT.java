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
package io.aklivity.zilla.runtime.binding.openapi.internal.streams;

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

public class OpenapiServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("openapi", "io/aklivity/zilla/specs/binding/openapi/streams/openapi")
        .addScriptRoot("composite", "io/aklivity/zilla/specs/binding/openapi/streams/composite");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/openapi/config")
        .configure(EngineConfiguration.ENGINE_VERBOSE, false)
        .configure(EngineConfiguration.ENGINE_VERBOSE_COMPOSITES, false)
        .external("openapi0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${composite}/create.pet/client",
        "${openapi}/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.multiple.specs.yaml")
    @Specification({
        "${composite}/create.pet.and.item/client",
        "${openapi}/create.pet.and.item/server"
    })
    public void shouldCreatePetAndItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.secure.yaml")
    @Specification({
        "${composite}/create.pet/client",
        "${openapi}/create.pet/server"
    })
    public void shouldCreateSecurePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.env.prod.yaml")
    @Specification({
        "${composite}/create.pet.prod/client",
        "${openapi}/create.pet.prod/server"
    })
    public void shouldCreatePetWithProductionServer() throws Exception
    {
        k3po.finish();
    }
}
