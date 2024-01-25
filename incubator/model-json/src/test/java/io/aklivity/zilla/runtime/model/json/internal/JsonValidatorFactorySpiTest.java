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
package io.aklivity.zilla.runtime.model.json.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorContext;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactory;
import io.aklivity.zilla.runtime.model.json.config.JsonValidatorConfig;

public class JsonValidatorFactorySpiTest
{
    @Test
    public void shouldCreateReader()
    {
        Configuration config = new Configuration();
        ValidatorFactory factory = ValidatorFactory.instantiate();
        Validator validator = factory.create("json", config);

        ValidatorContext context = new JsonValidatorContext(mock(EngineContext.class));

        ValidatorConfig validatorConfig = JsonValidatorConfig.builder()
            .subject("test-value")
                .catalog()
                .name("test0")
                    .schema()
                    .subject("subject1")
                    .version("latest")
                    .build()
                .build()
            .build();

        assertThat(validator, instanceOf(JsonValidator.class));
        assertThat(context.supplyHandler(validatorConfig), instanceOf(JsonValidatorHandler.class));
    }
}
