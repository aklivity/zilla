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
package io.aklivity.zilla.runtime.validator.json;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public class JsonValueValidatorFactoryTest
{
    // GIVEN
    ValidatorConfig validator = JsonValidatorConfig.builder()
            .catalog()
                .name("test0")
                .build()
            .build();
    ToLongFunction<String> resolveId = i -> 0L;
    LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(new TestCatalogOptionsConfig("schema0"));
    JsonValidatorFactory factory = new JsonValidatorFactory();

    @Test
    public void shouldCreateReadValidator()
    {
        // WHEN
        ValueValidator jsonValueValidator = factory.createValueReader(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(jsonValueValidator, instanceOf(JsonValueValidator.class));
    }

    @Test
    public void shouldCreateWriteValidator()
    {
        // WHEN
        ValueValidator jsonValueValidator = factory.createValueWriter(validator, resolveId, supplyCatalog);

        // THEN
        assertThat(jsonValueValidator, instanceOf(JsonValueValidator.class));
    }
}
