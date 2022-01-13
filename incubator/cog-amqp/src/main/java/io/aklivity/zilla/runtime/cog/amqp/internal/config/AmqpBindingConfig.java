/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.amqp.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpCapabilities;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RoleConfig;

public final class AmqpBindingConfig
{
    public final long id;
    public final long vaultId;
    public final String entry;
    public final RoleConfig kind;
    public final List<AmqpRouteConfig> routes;
    public final AmqpRouteConfig exit;

    public AmqpBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.vaultId = binding.vault != null ? binding.vault.id : 0L;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(AmqpRouteConfig::new).collect(toList());
        this.exit = binding.exit != null ? new AmqpRouteConfig(binding.exit) : null;
    }

    public AmqpRouteConfig resolve(
        long authorization,
        String address,
        AmqpCapabilities capabilities)
    {
        return routes.stream()
            .filter(r -> r.when.isEmpty() || r.when.stream().anyMatch(m -> m.matches(address, capabilities)))
            .findFirst()
            .orElse(exit);
    }
}
