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

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.descriptor.OtlpMetricsDescriptor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessorFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class OltpExporterHandler implements ExporterHandler
{
    private final EngineConfiguration config;
    private final OtlpEndpointConfig[] endpoints;
    private final OtlpMetricsDescriptor descriptor;

    private MetricsProcessor metrics;
    //private MetricsPrinter printer;
    //private LongCounter counter1;
    private ObservableDoubleMeasurement gauge1;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter)
    {
        this.config = config;
        this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
        this.descriptor = new OtlpMetricsDescriptor(context::resolveMetric);
    }

    @Override
    public void start()
    {
        MetricsProcessorFactory factory = new MetricsProcessorFactory(config.directory(), null, null);
        metrics = factory.create();
        //printer = new MetricsPrinter(metrics, descriptor::kind, descriptor::name, descriptor::description);

        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "my-zilla-service")));

        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint("localhost:4317")
            .build();

        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter).build();

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

        gauge1 = meter
            .gaugeBuilder("stream.closes.received")
            .setDescription("desc for scr todo")
            .setUnit("1")
            .buildObserver();
    }

    @Override
    public int export()
    {
        // TODO: Ati
        System.out.println("Hello, World! I am the otlp exporter!");
        gauge1.record(77, Attributes.empty());

        try
        {
            Thread.sleep(30 * 1000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        return 0;
    }

    @Override
    public void stop()
    {
    }
}
