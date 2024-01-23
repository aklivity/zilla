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
package io.aklivity.zilla.runtime.engine.validator;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.test.internal.validator.TestValidator;
import io.aklivity.zilla.runtime.engine.test.internal.validator.TestValidatorContext;
import io.aklivity.zilla.runtime.engine.test.internal.validator.TestValidatorHandler;
import io.aklivity.zilla.runtime.engine.test.internal.validator.config.TestValidatorConfig;

public class ValidatorFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        ValidatorFactory factory = ValidatorFactory.instantiate();
        Validator validator = factory.create("test", config);

        TestValidatorConfig validatorConfig = TestValidatorConfig.builder().length(4).build();
        ValidatorContext context = new TestValidatorContext(mock(EngineContext.class));

        assertThat(validator, instanceOf(TestValidator.class));
        assertThat(context.supplyHandler(validatorConfig), instanceOf(TestValidatorHandler.class));
    }
}
