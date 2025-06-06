/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

final class VaultRegistry
{
    private final VaultConfig config;
    private final VaultContext context;

    private VaultHandler attached;

    VaultRegistry(
        VaultConfig vault,
        VaultContext context)
    {
        this.config = requireNonNull(vault);
        this.context = requireNonNull(context);
    }

    public void attach()
    {
        attached = context.attach(config);
    }

    public void detach()
    {
        context.detach(config);
        attached = null;
    }

    public VaultHandler handler()
    {
        return attached;
    }
}
