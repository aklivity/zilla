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

import java.time.Duration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.IntFunction;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final long DELAY = Duration.ofSeconds(1).toMillis();

    private final EngineContext context;
    private final OtlpEndpointConfig endpoint;
    private final Duration interval;
    private final Collector collector;
    private final IntFunction<KindConfig> resolveKind;
    private final List<AttributeConfig> attributes;
    private final Timer timer;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter,
        Collector collector,
        IntFunction<KindConfig> resolveKind,
        List<AttributeConfig> attributes)
    {
        this.context = context;
        this.endpoint = exporter.options().endpoints[0];
        this.interval = Duration.ofSeconds(exporter.options().interval);
        this.attributes = attributes;
        this.collector = collector;
        this.resolveKind = resolveKind;
        this.timer = new Timer();
    }

    @Override
    public void start()
    {
        MetricsReader metrics = new MetricsReader(collector, context::supplyLocalName);
        List<MetricRecord> records = metrics.records();
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(records, attributes, context::resolveMetric, resolveKind);
        TimerTask task = new OtlpExporterTask(endpoint.url, serializer);
        timer.schedule(task, DELAY, interval.toMillis());
    }

    @Override
    public int export()
    {
        return 0;
    }

    @Override
    public void stop()
    {
        timer.cancel();
    }
}
