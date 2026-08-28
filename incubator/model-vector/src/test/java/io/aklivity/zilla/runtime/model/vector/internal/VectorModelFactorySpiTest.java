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
package io.aklivity.zilla.runtime.model.vector.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelFactory;
import io.aklivity.zilla.runtime.engine.model.ModelFactorySpi;

public class VectorModelFactorySpiTest
{
    @Test
    public void shouldCreateModel()
    {
        Configuration config = new Configuration();
        ModelFactory factory = ModelFactory.instantiate();
        Model model = factory.create("vector", config);

        assertThat(model, instanceOf(VectorModel.class));
    }

    @Test
    public void shouldReportTypeAndSchema()
    {
        ModelFactorySpi spi = new VectorModelFactorySpi();

        assertEquals(VectorModel.NAME, spi.type());
        assertNotNull(spi.schema());
        assertThat(spi.create(new Configuration()), instanceOf(VectorModel.class));
    }
}
