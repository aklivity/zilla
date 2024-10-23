/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.model;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelFactory;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestConverterHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModel;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelContext;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class ModelFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        ModelFactory factory = ModelFactory.instantiate();
        Model model = factory.create("test", config);

        TestModelConfig converterConfig = TestModelConfig.builder().length(4).build();
        ModelContext context = new TestModelContext(mock(EngineContext.class));

        assertThat(model, instanceOf(TestModel.class));
        assertThat(context.supplyReadConverterHandler(converterConfig), instanceOf(TestConverterHandler.class));
        assertThat(context.supplyWriteConverterHandler(converterConfig), instanceOf(TestConverterHandler.class));
    }
}
