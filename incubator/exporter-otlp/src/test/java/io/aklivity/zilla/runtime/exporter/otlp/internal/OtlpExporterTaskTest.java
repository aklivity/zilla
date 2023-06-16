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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;

import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OtlpExporterTaskTest
{
    @Test
    public void shouldPost() throws IOException
    {
        // GIVEN
        OutputStream os = mock(OutputStream.class);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getOutputStream()).thenReturn(os);
        URL url = mock(URL.class);
        when(url.openConnection()).thenReturn(connection);
        OtlpMetricsSerializer serializer = mock(OtlpMetricsSerializer.class);
        when(serializer.serializeAll()).thenReturn("json");
        OtlpExporterTask task = new OtlpExporterTask(url, serializer);

        // WHEN
        task.run();

        // THEN
        verify(serializer).serializeAll();
        verify(url).openConnection();
        verify(connection).setRequestMethod("POST");
        verify(connection).setRequestProperty("Content-Type", "application/json");
        verify(os).write("json".getBytes());
        verify(connection).getResponseCode();
        verify(connection).disconnect();
    }
}
