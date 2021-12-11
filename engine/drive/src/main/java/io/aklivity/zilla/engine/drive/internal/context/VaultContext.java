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
package io.aklivity.zilla.engine.drive.internal.context;

import static java.util.Objects.requireNonNull;

import io.aklivity.zilla.engine.drive.cog.Axle;
import io.aklivity.zilla.engine.drive.cog.vault.BindingVault;
import io.aklivity.zilla.engine.drive.config.Vault;

final class VaultContext
{
    private final Vault vault;
    private final Axle elektron;

    private BindingVault attached;

    VaultContext(
        Vault vault,
        Axle elektron)
    {
        this.vault = requireNonNull(vault);
        this.elektron = requireNonNull(elektron);
    }

    public void attach()
    {
        attached = elektron.attach(vault);
    }

    public void detach()
    {
        elektron.detach(vault);
        attached = null;
    }

    public BindingVault vaultFactory()
    {
        return attached;
    }
}
