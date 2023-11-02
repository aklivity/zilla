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

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringValidatorFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateValueReader()
    {
        // GIVEN
        ValidatorConfig validator = new StringValidatorConfig("utf_8");
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        StringValidatorFactory factory = new StringValidatorFactory();

        // WHEN
        ValueValidator reader = factory.createValueReader(validator, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(StringValueValidator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateValueWriter()
    {
        // GIVEN
        ValidatorConfig validator = new StringValidatorConfig("utf_8");
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        StringValidatorFactory factory = new StringValidatorFactory();

        // WHEN
        ValueValidator writer = factory.createValueWriter(validator, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(StringValueValidator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateFragmentReader()
    {
        // GIVEN
        ValidatorConfig validator = new StringValidatorConfig("utf_8");
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        StringValidatorFactory factory = new StringValidatorFactory();

        // WHEN
        FragmentValidator reader = factory.createFragmentReader(validator, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(StringFragmentValidator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateFragmentWriter()
    {
        // GIVEN
        ValidatorConfig validator = new StringValidatorConfig("utf_8");
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        StringValidatorFactory factory = new StringValidatorFactory();

        // WHEN
        FragmentValidator writer = factory.createFragmentWriter(validator, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(StringFragmentValidator.class));
    }
}
