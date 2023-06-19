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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.TimerTask;

import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public final class OtlpExporterTask extends TimerTask
{
    private final URI url;
    private final OtlpMetricsSerializer serializer;
    private final HttpClient httpClient;

    public OtlpExporterTask(
        String url,
        OtlpMetricsSerializer serializer)
    {
        this.url = URI.create(url);
        this.serializer = serializer;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void run()
    {
        post(serializer.serializeAll());
    }

    private void post(
        String json)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception ex)
        {
            // do nothing, try again next time
        }
    }
}
