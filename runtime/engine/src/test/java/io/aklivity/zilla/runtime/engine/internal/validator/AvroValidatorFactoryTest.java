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

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public class AvroValidatorFactoryTest
{
    @Test
    public void shouldCreate()
    {
        // GIVEN
        ValidatorConfig validator = new AvroValidatorConfig(List.of(new CatalogedConfig("test0", List.of())));
        ToLongFunction<String> resolveId = i -> 0L;
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(new TestCatalogOptionsConfig("schema0"));
        AvroValidatorFactory factory = new AvroValidatorFactory();

        // WHEN
        Validator avroValidator = factory.create(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(avroValidator, instanceOf(AvroValidator.class));
    }
}
