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
package io.aklivity.zilla.runtime.model.classify.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.test.internal.classifier.TestClassifierHandler;

public class ClassifyModelContextTest
{
    @Test
    public void shouldSupplyHandler()
    {
        EngineContext engine = mock(EngineContext.class);
        when(engine.supplyClassifier(anyLong())).thenReturn(new TestClassifierHandler(Runnable::run));

        ModelContext context = new ClassifyModelContext(engine, new ClassifyModelConfiguration(new Configuration()));
        ModelConfig config = ClassifyModelConfig.builder()
            .reject("moderator0", List.of("prompt.injection"))
            .reject("patterns0", List.of("secret.aws_key"))
            .build();

        ModelHandler handler = context.supplyHandler(config);

        assertThat(handler, instanceOf(ClassifyModelHandlerImpl.class));
    }
}
