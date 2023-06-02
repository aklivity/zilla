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
import java.util.Timer;
import java.util.TimerTask;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessorFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class OltpExporterHandler implements ExporterHandler
{
    private final EngineConfiguration config;
    //private final OtlpEndpointConfig[] endpoints;
    //private final OtlpMetricsDescriptor descriptor;
    private final Duration interval;
    private final Timer timer;

    private MetricsProcessor metrics;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter)
    {
        this.config = config;
        //this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
        //this.descriptor = new OtlpMetricsDescriptor(context::resolveMetric);
        this.interval = Duration.ofSeconds(5L);
        this.timer = new Timer();
    }

    @Override
    public void start()
    {
        System.out.println("Hello, World! I am the otlp exporter!");
        MetricsProcessorFactory factory = new MetricsProcessorFactory(config.directory(), null, null);
        metrics = factory.create();
        //MetricsPrinter printer = new MetricsPrinter(metrics, descriptor::kind, descriptor::name, descriptor::description);

        MetricRecord record = metrics.findRecord("example", "echo_server0", "stream.closes.received");
        System.out.println(((CounterGaugeRecord)record).value());

        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "my-zilla-service")));

        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
            //.setEndpoint("localhost:4317")
            .setEndpoint("http://localhost:4317")
            //.setEndpoint("http://localhost:9999")
            .build();

        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter)
            .setInterval(interval)
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setResource(resource)
            .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .buildAndRegisterGlobal();

        Meter meter = openTelemetry.meterBuilder("instrumentation-library-name")
            .setInstrumentationVersion("1.0.0")
            .build();

        ObservableLongGauge observableLongGauge = meter
            .gaugeBuilder("stream.active.received")
            .setDescription("desc todo")
            .setUnit("count")
            .ofLongs()
            .buildWithCallback(measurement -> measurement.record(fortyTwo(), Attributes.empty()));

        ObservableLongCounter observableLongCounter = meter
            .counterBuilder("stream.closes.received")
            .setDescription("desc todo")
            .setUnit("count")
            .buildWithCallback(measurement -> measurement.record(seventySeven(), Attributes.empty()));

        LongHistogram histogram1 = meter
            .histogramBuilder("http.request.size")
            .setDescription("desc todo")
            .setUnit("bytes")
            .ofLongs()
            .build();

        TimerTask histogramTask = new TimerTask()
        {
            public void run()
            {
                histogram1.record(eightyEight(), Attributes.empty());
            }
        };
        timer.schedule(histogramTask, 0, interval.toMillis());
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

    public long fortyTwo()
    {
        System.out.println("the answer is 42");
        return 42L;
    }

    public long seventySeven()
    {
        System.out.println("the result is 77");
        return 77L;
    }

    public long eightyEight()
    {
        System.out.println("sending 88");
        return 88L;
    }
}
