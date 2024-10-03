/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.openapi.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class CompositeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("composite", "io/aklivity/zilla/specs/binding/openapi/streams/composite");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${composite}/create.pet/client",
        "${composite}/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${composite}/create.pet.and.item/client",
        "${composite}/create.pet.and.item/server"
    })
    public void shouldCreatePetAndItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${composite}/create.pet.prod/client",
        "${composite}/create.pet.prod/server"
    })
    public void shouldCreatePetWithProductionEnvironment() throws Exception
    {
        k3po.finish();
    }
}
