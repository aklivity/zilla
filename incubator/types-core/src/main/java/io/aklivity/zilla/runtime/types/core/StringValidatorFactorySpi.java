/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.types.core;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
import io.aklivity.zilla.runtime.types.core.config.StringValidatorConfig;

import java.util.function.LongFunction;

public class StringValidatorFactorySpi implements ValidatorFactorySpi
{
    @Override
    public String type()
    {
        return StringValidator.NAME;
    }

    @Override
    public Validator create(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new StringValidator(StringValidatorConfig.class.cast(config), supplyCatalog);
    }
}
