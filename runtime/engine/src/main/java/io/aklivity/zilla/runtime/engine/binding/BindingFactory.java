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

import static io.aklivity.zilla.runtime.common.feature.FeatureLoader.filter;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Map;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class BindingFactory extends Factory
{
    private final Map<String, BindingFactorySpi> factorySpis;

    public static BindingFactory instantiate()
    {
        return instantiate(filter(load(BindingFactorySpi.class)), BindingFactory::new);
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

    private BindingFactory(
        Map<String, BindingFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
