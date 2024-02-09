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
package io.aklivity.zilla.runtime.engine.model;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModel;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelContext;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestValidatorHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class ModelFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        ModelFactory factory = ModelFactory.instantiate();
        Model model = factory.create("test", config);

        TestModelConfig modelConfig = TestModelConfig.builder().length(4).build();
        ModelContext context = new TestModelContext(mock(EngineContext.class));

        assertThat(model, instanceOf(TestModel.class));
        assertThat(context.supplyValidatorHandler(modelConfig), instanceOf(TestValidatorHandler.class));
    }

    @Test
    public void shouldCreateNullValidator()
    {
        TestModelConfig config = TestModelConfig.builder().length(4).build();
        ModelContext context = new ModelContext()
        {
            @Override
            public ConverterHandler supplyReadConverterHandler(
                ModelConfig config)
            {
                return null;
            }

            @Override
            public ConverterHandler supplyWriteConverterHandler(
                ModelConfig config)
            {
                return null;
            }
        };
        assertNull(context.supplyValidatorHandler(config));
    }
}
