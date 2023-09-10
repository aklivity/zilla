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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class ValidatorFactory
{
    private final Map<String, ValidatorFactorySpi> validatorSpis;

    public static ValidatorFactory instantiate()
    {
        return instantiate(load(ValidatorFactorySpi.class));
    }

    public Validator create(
        ValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        String type = config.type;
        requireNonNull(type, "name");

        ValidatorFactorySpi validatorSpi = requireNonNull(validatorSpis.get(type), () -> "Unrecognized validator name: " + type);

        return validatorSpi.create(config, resolveId, supplyCatalog);
    }

    private static ValidatorFactory instantiate(
        ServiceLoader<ValidatorFactorySpi> validators)
    {
        Map<String, ValidatorFactorySpi> validatorSpisByName = new TreeMap<>();
        validators.forEach(validatorSpi -> validatorSpisByName.put(validatorSpi.type(), validatorSpi));

        return new ValidatorFactory(unmodifiableMap(validatorSpisByName));
    }

    private ValidatorFactory(
        Map<String, ValidatorFactorySpi> validatorSpis)
    {
        this.validatorSpis = validatorSpis;
    }
}
