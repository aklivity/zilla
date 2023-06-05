package io.aklivity.zilla.runtime.exporter.otlp.internal.publisher;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.runtime.exporter.otlp.internal.descriptor.OtlpMetricsDescriptor;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;

public class MetricsPublisherTest
{
    @Test
    public void test()
    {
        // GIVEN
        CounterGaugeRecord counterRecord = mock(CounterGaugeRecord.class);
        when(counterRecord.namespaceName()).thenReturn("ns1");
        when(counterRecord.bindingName()).thenReturn("binding1");
        when(counterRecord.metricName()).thenReturn("counter1");
        when(counterRecord.value()).thenReturn(42L);

        CounterGaugeRecord gaugeRecord = mock(CounterGaugeRecord.class);
        when(gaugeRecord.namespaceName()).thenReturn("ns1");
        when(gaugeRecord.bindingName()).thenReturn("binding1");
        when(gaugeRecord.metricName()).thenReturn("gauge1");
        when(gaugeRecord.value()).thenReturn(77L);

        List<MetricRecord> metricRecords = List.of(counterRecord, gaugeRecord);
        MetricsProcessor metricsProcessor = mock(MetricsProcessor.class);
        when(metricsProcessor.getRecords()).thenReturn(metricRecords);

        OtlpMetricsDescriptor descriptor = mock(OtlpMetricsDescriptor.class);
        when(descriptor.name("counter1")).thenReturn("counter1_name");
        when(descriptor.kind("counter1")).thenReturn("counter");
        when(descriptor.description("counter1")).thenReturn("description for counter1");
        when(descriptor.unit("counter1")).thenReturn("count");
        when(descriptor.name("gauge1")).thenReturn("gauge1_name");
        when(descriptor.kind("gauge1")).thenReturn("gauge");
        when(descriptor.description("gauge1")).thenReturn("description for gauge1");
        when(descriptor.unit("gauge1")).thenReturn("bytes");

        Meter meter = mock(Meter.class);
        LongCounterBuilder longCounterBuilder = mock(LongCounterBuilder.class);
        when(meter.counterBuilder(anyString())).thenReturn(longCounterBuilder);
        when(longCounterBuilder.setDescription(anyString())).thenReturn(longCounterBuilder);
        when(longCounterBuilder.setUnit(anyString())).thenReturn(longCounterBuilder);

        DoubleGaugeBuilder doubleGaugeBuilder = mock(DoubleGaugeBuilder.class);
        when(meter.gaugeBuilder(anyString())).thenReturn(doubleGaugeBuilder);
        LongGaugeBuilder longGaugeBuilder = mock(LongGaugeBuilder.class);
        when(doubleGaugeBuilder.ofLongs()).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.setDescription(anyString())).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.setUnit(anyString())).thenReturn(longGaugeBuilder);

        MetricsPublisher publisher = new MetricsPublisher(metricsProcessor, meter, descriptor::kind,
            descriptor::name, descriptor::description, descriptor::unit);

        ArgumentCaptor<Consumer<ObservableLongMeasurement>> captor = ArgumentCaptor.forClass(Consumer.class);
        ObservableLongMeasurement measurement = mock(ObservableLongMeasurement.class);

        // WHEN
        publisher.setup();

        // THEN
        verify(meter).counterBuilder("counter1_name");
        verify(longCounterBuilder).setDescription("description for counter1");
        verify(longCounterBuilder).setUnit("count");
        verify(longCounterBuilder).buildWithCallback(captor.capture());
        captor.getValue().accept(measurement);
        verify(measurement).record(42L, Attributes.empty());

        verify(meter).gaugeBuilder("gauge1_name");
        verify(doubleGaugeBuilder).ofLongs();
        verify(longGaugeBuilder).setDescription("description for gauge1");
        verify(longGaugeBuilder).setUnit("bytes");
        verify(longGaugeBuilder).buildWithCallback(captor.capture());
        captor.getValue().accept(measurement);
        verify(measurement).record(77L, Attributes.empty());
    }
}
