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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.function.LongConsumer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class OtlpExporterIT
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests";

    private final K3poRule k3po = new K3poRule()
        .setScriptRoot("io/aklivity/zilla/runtime/exporter/otlp/internal/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory(ENGINE_DIRECTORY)
        .configure(ENGINE_WORKERS, 3)
        .configurationRoot("io/aklivity/zilla/runtime/exporter/otlp/internal/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("exporter.otlp.yaml")
    @Specification({
        "client"
    })
    public void shouldPostToOtlpCollector() throws Exception
    {
        // GIVEN
        int namespaceId = engine.supplyLabelId("test");
        int bindingId = engine.supplyLabelId("net0");
        int counterId = engine.supplyLabelId("test.counter");
        int gaugeId = engine.supplyLabelId("test.gauge");
        int histogramId = engine.supplyLabelId("test.histogram");

        long nsBindingId = namespacedId(namespaceId, bindingId);
        long nsCounterId = namespacedId(namespaceId, counterId);
        long nsGaugeId = namespacedId(namespaceId, gaugeId);
        long nsHistogramId = namespacedId(namespaceId, histogramId);

        LongConsumer counterWriter0 = engine.counterWriter(nsBindingId, nsCounterId, 0);
        LongConsumer counterWriter1 = engine.counterWriter(nsBindingId, nsCounterId, 1);
        LongConsumer counterWriter2 = engine.counterWriter(nsBindingId, nsCounterId, 2);
        counterWriter0.accept(42L);
        counterWriter1.accept(21L);
        counterWriter2.accept(14L);
        // the aggregated counter value across the 3 cores should be 42 + 21 + 14 = 77

        LongConsumer gaugeWriter0 = engine.gaugeWriter(nsBindingId, nsGaugeId, 0);
        LongConsumer gaugeWriter1 = engine.gaugeWriter(nsBindingId, nsGaugeId, 1);
        LongConsumer gaugeWriter2 = engine.gaugeWriter(nsBindingId, nsGaugeId, 2);
        gaugeWriter0.accept(11L);
        gaugeWriter1.accept(22L);
        gaugeWriter2.accept(33L);
        // the aggregated gauge value across the 3 cores should be 11 + 22 + 33 = 66

        LongConsumer histogramWriter0 = engine.histogramWriter(nsBindingId, nsHistogramId, 0);
        LongConsumer histogramWriter1 = engine.histogramWriter(nsBindingId, nsHistogramId, 1);
        LongConsumer histogramWriter2 = engine.histogramWriter(nsBindingId, nsHistogramId, 2);
        // values 0..1 (2^0..2^1-1) go to bucket #0
        histogramWriter0.accept(1L);
        // values 16..31 (2^4..2^5-1) go to bucket #4
        histogramWriter1.accept(17L);
        histogramWriter2.accept(18L);
        // 1 value goes to bucket #0 and 2 values go to bucket #4

        // WHEN
        // the exporter publishes the metric data to the collector in json format

        // THEN
        k3po.finish();
    }

    private static long namespacedId(
        final int namespaceId,
        final int localId)
    {
        return (long) namespaceId << Integer.SIZE | (long) localId << 0;
    }
}
