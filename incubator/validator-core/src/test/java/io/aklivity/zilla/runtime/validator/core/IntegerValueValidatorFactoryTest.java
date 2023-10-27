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
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.core.config.IntegerValidatorConfig;

public class IntegerValueValidatorFactoryTest
{
    // GIVEN
    ValidatorConfig validator = new IntegerValidatorConfig();
    ToLongFunction<String> resolveId = mock(ToLongFunction.class);
    LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
    IntegerValidatorFactory factory = new IntegerValidatorFactory();

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateReadValidator()
    {
        // WHEN
        ValueValidator integerValueValidator = factory.createValueReader(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(integerValueValidator, instanceOf(IntegerValueValidator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateWriteValidator()
    {
        // WHEN
        ValueValidator integerValueValidator = factory.createValueWriter(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(integerValueValidator, instanceOf(IntegerValueValidator.class));
    }
}
