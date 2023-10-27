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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.test.internal.validator.TestValueValidator;
import io.aklivity.zilla.runtime.engine.test.internal.validator.config.TestValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactory;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;

public class ValueValidatorFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreate()
    {
        // GIVEN
        ValidatorConfig testValidator = new TestValidatorConfig();
        ToLongFunction<String> resolveId = mock(ToLongFunction.class);
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        ValidatorFactory factory = ValidatorFactory.instantiate();

        // WHEN
        ValueValidator valueValidator = factory.createReadValidator(testValidator, resolveId, supplyCatalog);

        // THEN
        assertThat(valueValidator, instanceOf(TestValueValidator.class));
    }
}
