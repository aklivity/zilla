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
package io.aklivity.zilla.runtime.engine.model;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class ModelFactory
{
    private final Map<String, ModelFactorySpi> modelSpis;

    public static ModelFactory instantiate()
    {
        return instantiate(load(ModelFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return modelSpis.keySet();
    }

    public Model create(
        String name,
        Configuration config)
    {
        requireNonNull(name, "name");

        ModelFactorySpi converterSpi = requireNonNull(modelSpis.get(name), () -> "Unrecognized Model name: " + name);

        return converterSpi.create(config);
    }

    public Collection<ModelFactorySpi> converterSpis()
    {
        return modelSpis.values();
    }

    private static ModelFactory instantiate(
        ServiceLoader<ModelFactorySpi> converters)
    {
        Map<String, ModelFactorySpi> converterSpisByName = new TreeMap<>();
        converters.forEach(converterSpi -> converterSpisByName.put(converterSpi.type(), converterSpi));

        return new ModelFactory(unmodifiableMap(converterSpisByName));
    }

    private ModelFactory(
        Map<String, ModelFactorySpi> modelSpis)
    {
        this.modelSpis = modelSpis;
    }
}
