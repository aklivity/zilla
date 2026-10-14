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
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;

public class ClassifyModelTest
{
    @Test
    public void shouldReportName()
    {
        ClassifyModel model = new ClassifyModel(new ClassifyModelConfiguration(new Configuration()));

        assertEquals(ClassifyModel.NAME, model.name());
    }

    @Test
    public void shouldSupplyContext()
    {
        ClassifyModel model = new ClassifyModel(new ClassifyModelConfiguration(new Configuration()));
        ModelContext context = model.supply(mock(EngineContext.class));

        assertThat(context, instanceOf(ClassifyModelContext.class));
    }
}
