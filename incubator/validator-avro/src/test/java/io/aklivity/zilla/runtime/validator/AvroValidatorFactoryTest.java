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
package io.aklivity.zilla.runtime.validator;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.validator.config.AvroValidatorConfig;

public class AvroValidatorFactoryTest
{
    @Test
    public void shouldCreate()
    {
        // GIVEN
        ValidatorConfig validator = new AvroValidatorConfig(List.of(new CatalogedConfig("test0", List.of())), "test-value");
        ToLongFunction<String> resolveId = i -> 0L;
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(new TestCatalogOptionsConfig("schema0"));
        AvroValidatorFactory factory = new AvroValidatorFactory();

        // WHEN
        Validator avroValidator = factory.create(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(avroValidator, instanceOf(AvroValidator.class));
    }
}
