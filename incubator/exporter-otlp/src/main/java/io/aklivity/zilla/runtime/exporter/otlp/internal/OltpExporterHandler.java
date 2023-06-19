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
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReaderFactory;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsDescriptor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final long DELAY = Duration.ofSeconds(1).toMillis();

    private final EngineConfiguration config;
    private final OtlpMetricsDescriptor descriptor;
    private final OtlpEndpointConfig endpoint;
    private final Duration interval;
    private final List<AttributeConfig> attributes;
    private final Timer timer;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter,
        Function<String, KindConfig> findBindingKind,
        List<AttributeConfig> attributes)
    {
        this.config = config;
        this.descriptor = new OtlpMetricsDescriptor(context::resolveMetric, findBindingKind);
        // options is required, at least one endpoint is required, url must be valid url
        this.endpoint = exporter.options().endpoints[0];
        this.interval = Duration.ofSeconds(exporter.options().interval);
        this.attributes = attributes;
        this.timer = new Timer();
    }

    @Override
    public void start()
    {
        MetricsReaderFactory factory = new MetricsReaderFactory(config.directory(), null, null);
        MetricsReader metrics = factory.create();
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(metrics, attributes, descriptor::kind,
            descriptor::nameByBinding, descriptor::description, descriptor::unit);
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
