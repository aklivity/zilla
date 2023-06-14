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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimerTask;

import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public final class OtlpExporterTask extends TimerTask
{
    private final URL url;
    private final OtlpMetricsSerializer serializer;

    public OtlpExporterTask(
        URL url,
        OtlpMetricsSerializer serializer)
    {
        this.url = url;
        this.serializer = serializer;
    }

    @Override
    public void run()
    {
        System.out.println(serializer.serializeAll()); // TODO: Ati
        post(serializer.serializeAll());
    }

    private void post(
        String json)
    {
        try
        {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(json.getBytes());
            os.flush();
            os.close();
            connection.getResponseCode();
            connection.disconnect();
        }
        catch (IOException ex)
        {
            // do nothing, try again next time
        }
    }
}
