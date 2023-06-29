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
package io.aklivity.zilla.runtime.engine.vault;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class VaultFactory
{
    private final Map<String, VaultFactorySpi> factorySpis;

    public static VaultFactory instantiate()
    {
        return instantiate(load(VaultFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Vault create(
        String name,
        Configuration config)
    {
        requireNonNull(name, "name");

        VaultFactorySpi factorySpi = requireNonNull(factorySpis.get(name), () -> "Unrecognized vault name: " + name);

        return factorySpi.create(config);
    }

    private static VaultFactory instantiate(
        ServiceLoader<VaultFactorySpi> factories)
    {
        Map<String, VaultFactorySpi> factorySpisByName = new TreeMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.name(), factorySpi));

        return new VaultFactory(unmodifiableMap(factorySpisByName));
    }

    private VaultFactory(
        Map<String, VaultFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
