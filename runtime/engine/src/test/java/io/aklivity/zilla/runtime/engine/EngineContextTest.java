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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.rules.RuleChain.outerRule;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.CountersLayout;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.test.EngineRule;

public class EngineContextTest
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests-ati1";

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory(ENGINE_DIRECTORY)
        .configure(ENGINE_WORKERS, 3)
        .commandBufferCapacity(8192)
        .responseBufferCapacity(8192)
        .counterValuesBufferCapacity(8192)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(timeout);

    @Test
    public void shouldFetchCounterValue() throws Exception
    {
        // GIVEN
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
