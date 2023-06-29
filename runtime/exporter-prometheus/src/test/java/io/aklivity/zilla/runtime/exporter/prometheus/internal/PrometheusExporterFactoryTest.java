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
package io.aklivity.zilla.runtime.exporter.prometheus.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URL;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterFactory;

public final class PrometheusExporterFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        // GIVEN
        Configuration config = new Configuration();
        ExporterFactory factory = ExporterFactory.instantiate();

        // WHEN
        Exporter exporter = factory.create("prometheus", config);
        ExporterContext context = exporter.supply(mock(EngineContext.class));

        // THEN
        assertThat(exporter, instanceOf(PrometheusExporter.class));
        assertThat(exporter.name(), equalTo("prometheus"));
        assertThat(exporter.type(), instanceOf(URL.class));
        assertThat(context, instanceOf(ExporterContext.class));
    }
}
