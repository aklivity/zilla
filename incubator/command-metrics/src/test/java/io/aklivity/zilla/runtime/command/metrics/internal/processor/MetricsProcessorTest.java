/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.metrics.internal.processor;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.COUNTER;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.HISTOGRAM;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;


public class MetricsProcessorTest
{
    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        // GIVEN
        LongSupplier[][] counterRecordReaders = new LongSupplier[][]
        {
            {
                // ns1, binding1, counter1, 42
                () -> pack(1, 11), () -> pack(1, 21), () -> 42L,
            },
            {
                // ns1, binding1, counter2, 77
                () -> pack(1, 11), () -> pack(1, 22), () -> 77L,
            },
            {
                // ns1, binding2, counter1, 43
                () -> pack(1, 12), () -> pack(1, 21), () -> 43L,
            },
            {
                // ns2, binding1, counter1, 44
                () -> pack(2, 11), () -> pack(2, 21), () -> 44L,
            },
        };
        LongSupplier[][] histogramRecordReaders = new LongSupplier[][]
        {
            {
                // ns1, binding1, histogram1, 1, all zeroes...
                () -> pack(1, 11), () -> pack(1, 31),
                () -> 1L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
                () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L,
            },
        };
        String expectedOutput =
            "namespace    binding     metric                                      value\n" +
            "ns1          binding1    counter1                                       42\n" +
            "ns1          binding1    counter2                                       77\n" +
            "ns1          binding1    histogram1    [min: 1 | max: 1 | cnt: 1 | avg: 1]\n" +
            "ns1          binding2    counter1                                       43\n" +
            "ns2          binding1    counter1                                       44\n\n";
        LabelManager mockLabelManager = mock(LabelManager.class);
        when(mockLabelManager.lookupLabel(1)).thenReturn("ns1");
        when(mockLabelManager.lookupLabel(2)).thenReturn("ns2");
        when(mockLabelManager.lookupLabel(11)).thenReturn("binding1");
        when(mockLabelManager.lookupLabel(12)).thenReturn("binding2");
        when(mockLabelManager.lookupLabel(21)).thenReturn("counter1");
        when(mockLabelManager.lookupLabel(22)).thenReturn("counter2");
        when(mockLabelManager.lookupLabel(31)).thenReturn("histogram1");
        when(mockLabelManager.lookupLabelId("ns1")).thenReturn(1);
        when(mockLabelManager.lookupLabelId("ns2")).thenReturn(2);
        when(mockLabelManager.lookupLabelId("binding1")).thenReturn(11);
        when(mockLabelManager.lookupLabelId("binding2")).thenReturn(12);
        CountersReader mockCountersReader = mock(CountersReader.class);
        when(mockCountersReader.kind()).thenReturn(COUNTER);
        when(mockCountersReader.recordReaders()).thenReturn(counterRecordReaders);
        HistogramsReader mockHistogramsReader = mock(HistogramsReader.class);
        when(mockHistogramsReader.kind()).thenReturn(HISTOGRAM);
        when(mockHistogramsReader.recordReaders()).thenReturn(histogramRecordReaders);
        MetricsProcessor metrics = new MetricsProcessor(List.of(mockCountersReader), List.of(mockHistogramsReader),
                mockLabelManager, null, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        metrics.doProcess(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    // packs the two provided id's (int) in one combined id (long)
    private static long pack(
        final int namespaceId,
        final int localId)
    {
        return (long) namespaceId << Integer.SIZE |
                (long) localId << 0;
    }
}
