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

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessorFactory;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsDescriptor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{

    private final EngineConfiguration config;
    //private final OtlpEndpointConfig[] endpoints;
    private final OtlpMetricsDescriptor descriptor;
    private final URL otlpCollectorUrl;
    private final Duration interval;
    private final Timer timer;

    //private SdkMeterProvider meterProvider;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter,
        Function<String, KindConfig> findBindingKind)
    {
        this.config = config;
        //this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
        this.descriptor = new OtlpMetricsDescriptor(context::resolveMetric, findBindingKind);
        URL otlpCollectorUrl = null;
        try
        {
            otlpCollectorUrl = new URL("http://localhost:4318/v1/metrics"); // TODO: Ati - get this from config
        }
        catch (MalformedURLException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        this.otlpCollectorUrl = otlpCollectorUrl;
        this.interval = Duration.ofSeconds(5L); // TODO: Ati - get this from config
        this.timer = new Timer();
    }

    @Override
    public void start()
    {
        MetricsProcessorFactory factory = new MetricsProcessorFactory(config.directory(), null, null);
        MetricsProcessor metrics = factory.create();
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(metrics, descriptor::kind, descriptor::name,
            descriptor::description);
        TimerTask task = new OtlpExporterTask(otlpCollectorUrl, serializer);
        timer.schedule(task, 0, interval.toMillis());
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
