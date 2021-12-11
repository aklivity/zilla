/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.cog;

import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.cog.vault.BindingVault;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Vault;

final class TestAxle implements Axle
{
    private final TestStreamFactory factory;

    TestAxle(
        AxleContext context)
    {
        factory = new TestStreamFactory(context);
    }

    @Override
    public StreamFactory attach(
        Binding binding)
    {
        factory.attach(binding);
        return factory;
    }

    @Override
    public void detach(
        Binding binding)
    {
        factory.detach(binding);
    }

    @Override
    public BindingVault attach(
        Vault vault)
    {
        return new TestVault(vault);
    }

    @Override
    public void detach(
        Vault vault)
    {
    }
}
