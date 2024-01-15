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
package io.aklivity.zilla.runtime.engine.validator;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public final class ValidatorFactory
{
    private final Map<String, ValidatorFactorySpi> factorySpis;

    public static ValidatorFactory instantiate()
    {
        return instantiate(load(ValidatorFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Validator create(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        String type = config.type;
        requireNonNull(type, "name");

        ValidatorFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized validator name: " + type);

        return factorySpi.create(config, supplyCatalog);
    }

    public Collection<ValidatorFactorySpi> validatorSpis()
    {
        return factorySpis.values();
    }

    private static ValidatorFactory instantiate(
        ServiceLoader<ValidatorFactorySpi> factories)
    {
        Map<String, ValidatorFactorySpi> factorySpisByName = new TreeMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.type(), factorySpi));

        return new ValidatorFactory(unmodifiableMap(factorySpisByName));
    }

    private ValidatorFactory(
        Map<String, ValidatorFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
