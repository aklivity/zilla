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

import static io.aklivity.zilla.runtime.common.feature.FeatureLoader.filter;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Collection;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class ValidatorFactory extends Factory
{
    private final Map<String, ValidatorFactorySpi> validatorSpis;

    public static ValidatorFactory instantiate()
    {
        return instantiate(filter(load(ValidatorFactorySpi.class)), ValidatorFactory::new);
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

    public Collection<ValidatorFactorySpi> validatorSpis()
    {
        return validatorSpis.values();
    }

    private ValidatorFactory(
        Map<String, ValidatorFactorySpi> validatorSpis)
    {
        this.validatorSpis = validatorSpis;
    }
}
