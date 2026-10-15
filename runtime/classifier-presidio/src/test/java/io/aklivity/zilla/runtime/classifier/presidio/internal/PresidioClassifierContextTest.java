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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;

public class PresidioClassifierContextTest
{
    private PresidioClassifierContext context;
    private ClassifierConfig config;

    @Before
    public void init()
    {
        context = new PresidioClassifierContext(mock(EngineContext.class));

        config = GenericClassifierConfig.builder()
            .namespace("test")
            .name("presidio0")
            .type("presidio")
            .options(PresidioClassifierOptionsConfig.builder()
                .endpoint("http://localhost:3000")
                .build())
            .build();
    }

    @Test
    public void shouldAttachAndDetach()
    {
        ClassifierHandler handler = context.attach(config);

        assertThat(handler, instanceOf(PresidioClassifierHandler.class));

        context.detach(config);
    }

    @Test
    public void shouldDetachWhenNotAttached()
    {
        context.detach(config);
    }
}
