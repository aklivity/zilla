/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusEndpointConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusExporterConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusOptionsConfig;

public class PrometheusExporterHandlerTest
{
    @Test
    public void shouldStart() throws Exception
    {
        // GIVEN
        EngineConfiguration config = mock(EngineConfiguration.class);
        Path tmp = Files.createTempDirectory("engine");
        Files.createDirectory(tmp.resolve("metrics"));
        when(config.directory()).thenReturn(tmp);
        EngineContext context = mock(EngineContext.class);
        PrometheusEndpointConfig endpoint = new PrometheusEndpointConfig("http", 4242, "/metrics");
        PrometheusOptionsConfig options = new PrometheusOptionsConfig(new PrometheusEndpointConfig[]{endpoint});
        ExporterConfig exporter = new ExporterConfig("test0", "prometheus", options);
        PrometheusExporterConfig prometheusExporter = new PrometheusExporterConfig(exporter);
        PrometheusExporterHandler handler = new PrometheusExporterHandler(config, context, prometheusExporter);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest
            .newBuilder(new URI("http://localhost:4242/metrics"))
            .timeout(Duration.of(10, SECONDS))
            .GET()
            .build();

        // WHEN
        handler.start();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode(), equalTo(200));
        handler.stop();
    }
}
