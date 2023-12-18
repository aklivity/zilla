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
package io.aklivity.zilla.runtime.validator.avro;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.function.LongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroValidatorFactoryTest
{
    @Test
    public void shouldCreateReadValidator()
    {
        // GIVEN
        ValidatorConfig validator = AvroValidatorConfig.builder()
                .subject("test-value")
                    .catalog()
                    .name("test0")
                        .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                    .build()
                .build();
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema("schema0")
                .build());
        AvroValidatorFactory factory = new AvroValidatorFactory();

        // WHEN
        ValueValidator reader = factory.createValueReader(validator, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(AvroReadValidator.class));
    }

    @Test
    public void shouldCreateWriteValidator()
    {
        // GIVEN
        ValidatorConfig validator = AvroValidatorConfig.builder()
                .subject("test-value")
                    .catalog()
                    .name("test0")
                        .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                    .build()
                .build();
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema("schema0")
                .build());
        AvroValidatorFactory factory = new AvroValidatorFactory();

        // WHEN
        ValueValidator writer = factory.createValueWriter(validator, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(AvroWriteValidator.class));
    }
}
