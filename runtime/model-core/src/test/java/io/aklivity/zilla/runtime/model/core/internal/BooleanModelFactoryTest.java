/*
 * Copyright 2021-2026 Aklivity Inc
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.model.core.BooleanModelConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelFactory;
import io.aklivity.zilla.runtime.engine.model.ModelFactorySpi;

public class BooleanModelFactoryTest
{
    @Test
    public void shouldCreateReader()
    {
        Configuration config = new Configuration();
        ModelFactory factory = ModelFactory.instantiate();
        Model model = factory.create("boolean", config);

        ModelContext context = new BooleanModelContext(mock(EngineContext.class));

        ModelConfig modelConfig = BooleanModelConfig.builder().build();

        assertThat(model, instanceOf(BooleanModel.class));
        assertThat(context.supplyHandler(modelConfig), instanceOf(CoreModelHandler.class));
    }

    @Test
    public void shouldReportTypeAndSchema()
    {
        ModelFactorySpi spi = new BooleanModelFactorySpi();

        assertEquals(BooleanModel.NAME, spi.type());
        assertNotNull(spi.schema());
        assertThat(spi.create(new Configuration()), instanceOf(BooleanModel.class));
    }
}
