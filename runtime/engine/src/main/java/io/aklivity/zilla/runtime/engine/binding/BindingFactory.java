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
package io.aklivity.zilla.runtime.engine.binding;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class BindingFactory
{
    private final Map<String, BindingFactorySpi> factorySpis;

    public static BindingFactory instantiate()
    {
        return instantiate(load(BindingFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Binding create(
        String name,
        Configuration config)
    {
        requireNonNull(name, "name");

        BindingFactorySpi factorySpi = requireNonNull(factorySpis.get(name), () -> "Unrecognized binding name: " + name);

        return factorySpi.create(config);
    }

    private static BindingFactory instantiate(
        ServiceLoader<BindingFactorySpi> factories)
    {
        Map<String, BindingFactorySpi> factorySpisByName = new HashMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.name(), factorySpi));

        return new BindingFactory(unmodifiableMap(factorySpisByName));
    }

    private BindingFactory(
        Map<String, BindingFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
