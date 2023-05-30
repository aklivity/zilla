/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static io.aklivity.zilla.runtime.engine.internal.layouts.Layout.Mode.CREATE_READ_WRITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.CountersLayout;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class EngineContextTest
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests";

    @Test
    public void shouldFetchCounterValue() throws Exception
    {
        // GIVEN
        // start up the engine
        Properties properties = new Properties();
        properties.put(EngineConfiguration.ENGINE_DIRECTORY.name(), ENGINE_DIRECTORY);
        properties.put(ENGINE_WORKERS.name(), "3");
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        Engine engine = Engine.builder()
            .config(config)
            .errorHandler(errors::add)
            .build();
        engine.start();

        // create labels
        LabelManager labels = new LabelManager(Paths.get(ENGINE_DIRECTORY));
        int namespaceId = labels.supplyLabelId("ns1");
        int bindingId = labels.supplyLabelId("binding1");
        int metricId = labels.supplyLabelId("counter1");
        long namespacedBindingId = NamespacedId.id(namespaceId, bindingId);
        long namespacedMetricId = NamespacedId.id(namespaceId, metricId);

        // write some metric data
        Path path0 = Paths.get(ENGINE_DIRECTORY, "metrics", "counters0");
        Path path1 = Paths.get(ENGINE_DIRECTORY, "metrics", "counters1");
        Path path2 = Paths.get(ENGINE_DIRECTORY, "metrics", "counters2");
        CountersLayout countersLayout0 = new CountersLayout.Builder()
            .path(path0)
            .capacity(8192)
            .mode(CREATE_READ_WRITE)
            .build();
        CountersLayout countersLayout1 = new CountersLayout.Builder()
            .path(path1)
            .capacity(8192)
            .mode(CREATE_READ_WRITE)
            .build();
        CountersLayout countersLayout2 = new CountersLayout.Builder()
            .path(path2)
            .capacity(8192)
            .mode(CREATE_READ_WRITE)
            .build();
        LongConsumer writer0 = countersLayout0.supplyWriter(namespacedBindingId, namespacedMetricId);
        LongConsumer writer1 = countersLayout1.supplyWriter(namespacedBindingId, namespacedMetricId);
        LongConsumer writer2 = countersLayout2.supplyWriter(namespacedBindingId, namespacedMetricId);
        writer0.accept(42L);
        writer1.accept(21L);
        writer2.accept(14L);

        // WHEN
        LongSupplier counter = engine.context().counter("ns1", "binding1", "counter1");

        // THEN
        // the aggregated counter value across the 3 cores should be 42 + 21 + 14 = 77
        assertThat(counter.getAsLong(), equalTo(77L));
    }
}
