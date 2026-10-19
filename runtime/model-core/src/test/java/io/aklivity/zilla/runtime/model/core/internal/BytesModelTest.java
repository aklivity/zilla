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
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExt;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;

public class BytesModelTest
{
    @Test
    public void shouldReportName()
    {
        BytesModel model = new BytesModel(List.of());

        assertEquals(BytesModel.NAME, model.name());
    }

    @Test
    public void shouldSupplyContextForEachInstalledExtension()
    {
        BytesModelExtContext extContext = config -> null;
        BytesModelExt ext = new BytesModelExt()
        {
            @Override
            public BytesModelExtContext supply(
                EngineContext context)
            {
                return extContext;
            }
        };

        BytesModel model = new BytesModel(List.of(ext));
        ModelContext context = model.supply(mock(EngineContext.class));

        assertThat(context, instanceOf(BytesModelContext.class));
    }
}
