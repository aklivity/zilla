/*
 * Copyright 2021-2026 Aklivity Inc.
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

        ModelFactorySpi modelSpi = requireNonNull(modelSpis.get(name), () -> "Unrecognized Model name: " + name);

        return modelSpi.create(config);
    }

    public Collection<ModelFactorySpi> modelSpis()
    {
        return modelSpis.values();
    }

    private static ModelFactory instantiate(
        ServiceLoader<ModelFactorySpi> models)
    {
        Map<String, ModelFactorySpi> modelSpisByName = new TreeMap<>();
        models.forEach(modelSpi -> modelSpisByName.put(modelSpi.type(), modelSpi));

        return new ModelFactory(unmodifiableMap(modelSpisByName));
    }

    private ModelFactory(
        Map<String, ModelFactorySpi> modelSpis)
    {
        this.modelSpis = modelSpis;
    }
}
