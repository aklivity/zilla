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
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelFactory;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonModelFactorySpiTest
{
    @Test
    public void shouldCreateReader()
    {
        Configuration config = new Configuration();
        ModelFactory factory = ModelFactory.instantiate();
        Model model = factory.create("json", config);

        ModelContext context = new JsonModelContext(mock(EngineContext.class));

        ModelConfig modelConfig = JsonModelConfig.builder()
            .subject("test-value")
                .catalog()
                .name("test0")
                    .schema()
                    .subject("subject1")
                    .version("latest")
                    .build()
                .build()
            .build();

        assertThat(model, instanceOf(JsonModel.class));
        assertThat(context.supplyReadConverterHandler(modelConfig), instanceOf(JsonConverterHandler.class));
        assertThat(context.supplyWriteConverterHandler(modelConfig), instanceOf(JsonConverterHandler.class));
    }
}
