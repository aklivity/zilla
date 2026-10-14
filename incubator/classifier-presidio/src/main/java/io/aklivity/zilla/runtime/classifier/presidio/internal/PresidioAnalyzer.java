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
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class PresidioAnalyzer
{
    private final HttpClient client;
    private final URI analyze;
    private final String authorization;
    private final String language;
    private final double threshold;
    private final Duration timeout;
    private final List<String> entities;

    PresidioAnalyzer(
        HttpClient client,
        URI analyze,
        String authorization,
        String language,
        double threshold,
        Duration timeout,
        List<String> entities)
    {
        this.client = client;
        this.analyze = analyze;
        this.authorization = authorization;
        this.language = language;
        this.threshold = threshold;
        this.timeout = timeout;
        this.entities = entities;
    }

    CompletableFuture<String> analyze(
        String value)
    {
        CompletableFuture<HttpResponse<String>> response;

        try
        {
            response = client.sendAsync(request(value), BodyHandlers.ofString());
        }
        catch (RuntimeException ex)
        {
            response = CompletableFuture.failedFuture(ex);
        }

        return response.thenApply(PresidioAnalyzer::body);
    }

    static Throwable cause(
        Throwable failure)
    {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private HttpRequest request(
        String value)
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(analyze)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(PresidioRequest.body(value, language, entities, threshold)));

        if (!timeout.isZero())
        {
            request.timeout(timeout);
        }

        if (authorization != null)
        {
            request.header("Authorization", authorization);
        }

        return request.build();
    }

    private static String body(
        HttpResponse<String> response)
    {
        if (response.statusCode() / 100 != 2)
        {
            throw new IllegalStateException("analyze responded with status " + response.statusCode());
        }

        return response.body();
    }
}
