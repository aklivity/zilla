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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThrows;

import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.binding.TestBindingExtInfo;
import io.aklivity.zilla.config.engine.test.internal.metrics.TestMetricGroupExtInfo;
import io.aklivity.zilla.config.engine.test.internal.metrics.TestMetricGroupInfo;
import io.aklivity.zilla.config.engine.test.internal.model.TestModelExtInfo;

public class EngineInfoTest
{
    @Test
    public void shouldIncludeModelExtensionSchemaPatches()
    {
        EngineInfo info = new EngineInfo();

        Collection<URL> patches = info.patches();

        assertThat(patches, hasItem(new TestModelExtInfo().schema()));
    }

    @Test
    public void shouldIncludeBindingExtensionSchemaPatches()
    {
        EngineInfo info = new EngineInfo();

        Collection<URL> patches = info.patches();

        assertThat(patches, hasItem(new TestBindingExtInfo().schema()));
    }

    @Test
    public void shouldIncludeMetricGroupExtensionSchemaPatches()
    {
        EngineInfo info = new EngineInfo();

        Collection<URL> patches = info.patches();

        assertThat(patches, hasItem(new TestMetricGroupExtInfo().schema()));
    }

    @Test
    public void shouldRejectMetricGroupExtensionRedefiningGroupMetric()
    {
        URL group = new TestMetricGroupInfo().schema();
        URL redefine = getClass().getResource("test.ext.redefine.schema.patch.json");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> EngineInfo.validateMetricNames("test", group, List.of(redefine)));

        assertThat(ex.getMessage(), containsString("test.counter"));
    }

    @Test
    public void shouldRejectMetricGroupExtensionsRedefiningEachOther()
    {
        URL group = new TestMetricGroupInfo().schema();
        URL extension = new TestMetricGroupExtInfo().schema();
        URL other = getClass().getResource("test.ext.other.schema.patch.json");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> EngineInfo.validateMetricNames("test", group, List.of(extension, other)));

        assertThat(ex.getMessage(), containsString("test.ext.counter"));
    }
}
