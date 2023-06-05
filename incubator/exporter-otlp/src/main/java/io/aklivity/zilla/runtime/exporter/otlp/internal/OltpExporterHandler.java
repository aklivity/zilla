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

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.descriptor.OtlpMetricsDescriptor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessorFactory;
import io.aklivity.zilla.runtime.exporter.otlp.internal.publisher.MetricsPublisher;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class OltpExporterHandler implements ExporterHandler
{
    private static final String CLASS_NAME =  OltpExporterHandler.class.getName();
    private static final String SCOPE_VERSION = "1.0.0";

    private final EngineConfiguration config;
    //private final OtlpEndpointConfig[] endpoints;
    private final OtlpMetricsDescriptor descriptor;
    private final Duration interval;
    private final Timer timer;

    private SdkMeterProvider meterProvider;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter)
    {
        this.config = config;
        //this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
        this.descriptor = new OtlpMetricsDescriptor(context::resolveMetric);
        this.interval = Duration.ofSeconds(5L); // TODO: Ati - get this from config
        this.timer = new Timer();
    }

    @Override
    public void start()
    {
        System.out.println("Hello, World! I am the otlp exporter!");
        MetricsProcessorFactory factory = new MetricsProcessorFactory(config.directory(), null, null);
        MetricsProcessor metrics = factory.create();
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "my-zilla-service")));
        // TODO: Ati - set attributes from config
        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint("http://localhost:4317") // TODO: Ati - get this from endpoint
            .build();
        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter)
            .setInterval(interval)
            .build();
        meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setResource(resource)
            .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .buildAndRegisterGlobal();
        Meter meter = openTelemetry.meterBuilder(CLASS_NAME)
            .setInstrumentationVersion(SCOPE_VERSION)
            .build();
        MetricsPublisher publisher = new MetricsPublisher(metrics, meter, descriptor::kind, descriptor::name,
            descriptor::description, descriptor::unit);
        publisher.setup();

        /*String streamActiveReceived = "stream.active.received";
        ObservableLongGauge observableLongGauge = meter
            .gaugeBuilder(descriptor.name(streamActiveReceived))
            .setDescription(descriptor.description(streamActiveReceived))
            .setUnit(descriptor.unit(streamActiveReceived))
            .ofLongs()
            .buildWithCallback(m -> m.record(fortyTwo(), Attributes.empty()));

        String streamClosesReceived = "stream.closes.received";
        ObservableLongCounter observableLongCounter = meter
            .counterBuilder(descriptor.name(streamClosesReceived))
            .setDescription(descriptor.description(streamClosesReceived))
            .setUnit(descriptor.unit(streamClosesReceived))
            .buildWithCallback(m -> m.record(seventySeven(), Attributes.empty()));*/

        /*String httpRequestSize = "http.request.size";
        LongHistogram histogram1 = meter
            .histogramBuilder(descriptor.name(httpRequestSize))
            .setDescription(descriptor.description(httpRequestSize))
            .setUnit(descriptor.unit(httpRequestSize))
            .ofLongs()
            .build();

        TimerTask histogramTask = new TimerTask()
        {
            public void run()
            {
                histogram1.record(eightyEight(), Attributes.empty());
            }
        };
        timer.schedule(histogramTask, 0, interval.toMillis());*/
    }

    @Override
    public int export()
    {
        return 0;
    }

    @Override
    public void stop()
    {
        meterProvider.close();
        System.out.println("----------------");
        System.out.println("handler stopped.");
        //timer.cancel();
        //System.out.println("Stopped.");
    }
}
