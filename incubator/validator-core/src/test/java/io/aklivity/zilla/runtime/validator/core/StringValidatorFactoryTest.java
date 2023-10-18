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
package io.aklivity.zilla.runtime.validator.core;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringValidatorFactoryTest
{
    // GIVEN
    ValidatorConfig validator = new StringValidatorConfig("utf_8");
    ToLongFunction<String> resolveId = mock(ToLongFunction.class);
    LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
    StringValidatorFactory factory = new StringValidatorFactory();

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateReadValidator()
    {
        // WHEN
        Validator stringValidator = factory.createReadValidator(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(stringValidator, instanceOf(StringValidator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateWriteValidator()
    {
        // WHEN
        Validator stringValidator = factory.createWriteValidator(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(stringValidator, instanceOf(StringValidator.class));
    }
}
