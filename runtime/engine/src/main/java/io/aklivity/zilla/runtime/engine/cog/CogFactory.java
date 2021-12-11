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
package io.aklivity.zilla.runtime.engine.cog;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class CogFactory
{
    private final Map<String, CogFactorySpi> factorySpis;

    public static CogFactory instantiate()
    {
        return instantiate(load(CogFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Cog create(
        String name,
        Configuration config)
    {
        requireNonNull(name, "name");

        CogFactorySpi factorySpi = requireNonNull(factorySpis.get(name), () -> "Unregonized cog name: " + name);

        return factorySpi.create(config);
    }

    private static CogFactory instantiate(
        ServiceLoader<CogFactorySpi> factories)
    {
        Map<String, CogFactorySpi> factorySpisByName = new HashMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.name(), factorySpi));

        return new CogFactory(unmodifiableMap(factorySpisByName));
    }

    private CogFactory(
        Map<String, CogFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
