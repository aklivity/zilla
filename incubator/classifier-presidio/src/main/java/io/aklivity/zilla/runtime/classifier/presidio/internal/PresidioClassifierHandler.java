/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

final class PresidioClassifierHandler implements ClassifierHandler
{
    private static final String ANALYZE_PATH = "analyze";

    private final EngineContext context;
    private final HttpClient client;
    private final URI analyze;
    private final String authorization;
    private final String language;
    private final double threshold;
    private final Duration timeout;
    private final Map<String, String> entitiesByLabel;

    PresidioClassifierHandler(
        EngineContext context,
        ClassifierConfig config)
    {
        PresidioClassifierOptionsConfig options = (PresidioClassifierOptionsConfig) config.options;

        this.context = context;
        HttpClient.Builder client = HttpClient.newBuilder();

        if (!options.timeout.isZero())
        {
            client.connectTimeout(options.timeout);
        }

        this.client = client.build();
        this.analyze = analyze(options.endpoint);
        this.authorization = options.authorization;
        this.language = options.language;
        this.threshold = options.threshold;
        this.timeout = options.timeout;
        this.entitiesByLabel = options.labels;
    }

    @Override
    public Detector initDetector(
        List<String> labels)
    {
        List<String> entities = labels.stream()
            .map(this::entity)
            .distinct()
            .toList();

        return new PresidioDetector(context, client, analyze, authorization, language, threshold, timeout, entities);
    }

    void close()
    {
        client.shutdownNow();
    }

    private static URI analyze(
        String endpoint)
    {
        return URI.create(endpoint.endsWith("/") ? endpoint + ANALYZE_PATH : endpoint + "/" + ANALYZE_PATH);
    }

    private String entity(
        String label)
    {
        String entity = entitiesByLabel.isEmpty() ? label : entitiesByLabel.get(label);

        if (entity == null)
        {
            throw new IllegalArgumentException("Unrecognized label: " + label);
        }

        return entity;
    }
}
