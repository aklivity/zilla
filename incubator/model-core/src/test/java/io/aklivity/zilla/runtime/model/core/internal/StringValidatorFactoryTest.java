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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorContext;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
import io.aklivity.zilla.runtime.engine.validator.ValidatorHandler;
import io.aklivity.zilla.runtime.model.core.config.StringValidatorConfig;

public class StringValidatorFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreate()
    {
        // GIVEN
        Configuration config = new Configuration();
        ValidatorConfig validator = new StringValidatorConfig("utf_8");
        ValidatorFactorySpi factory = new StringValidatorFactorySpi();

        // WHEN
        Validator reader = factory.create(config);
        ValidatorContext context = reader.supply(mock(EngineContext.class));
        ValidatorHandler handler = context.supplyHandler(validator);

        // THEN
        assertThat(reader, instanceOf(StringValidator.class));
        assertThat(handler, instanceOf(StringValidatorHandler.class));
    }
}
