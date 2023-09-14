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
package io.aklivity.zilla.runtime.engine.internal.validator;

import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;

public final class AvroValidatorFactory implements ValidatorFactorySpi
{
    @Override
    public String type()
    {
        return "avro";
    }

    @Override
    public Validator create(
        ValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new AvroValidator(AvroValidatorConfig.class.cast(config), resolveId, supplyCatalog);
    }
}
