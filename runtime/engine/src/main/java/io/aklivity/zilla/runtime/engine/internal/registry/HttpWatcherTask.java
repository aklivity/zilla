/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.config.EngineConfig;

public class HttpWatcherTask extends WatcherTask
{
    private static final URI CLOSE_REQUESTED = URI.create("http://localhost:12345");

    private final Map<URI, String> etags;
    private final Map<URI, byte[]> configHashes;
    private final Map<URI, CompletableFuture<Void>> futures;
    private final BlockingQueue<URI> configQueue;
    private final int pollSeconds;

    public HttpWatcherTask(
        BiFunction<URL, String, EngineConfig> configChangeListener,
        Consumer<Set<String>> resourceChangeListener,
        int pollSeconds)
    {
        super(configChangeListener, resourceChangeListener);
        this.etags = new ConcurrentHashMap<>();
        this.configHashes = new ConcurrentHashMap<>();
        this.futures = new ConcurrentHashMap<>();
        this.configQueue = new LinkedBlockingQueue<>();
        this.pollSeconds = pollSeconds;
    }

    @Override
    public Future<Void> submit()
    {
        return executor.submit(this);
    }

    @Override
    public Void call() throws InterruptedException
    {
        while (true)
        {
            URI configURI = configQueue.take();
            if (configURI == CLOSE_REQUESTED)
            {
                break;
            }
            String etag = etags.getOrDefault(configURI, "");
            sendAsync(configURI, etag);
        }
        return null;
    }

    @Override
    public CompletableFuture<EngineConfig> watchConfig(
        URL configURL)
    {
        URI configURI = toURI(configURL);

        CompletableFuture<EngineConfig> configFuture;
        try
        {
            EngineConfig config = sendSync(configURI);
            configFuture = CompletableFuture.completedFuture(config);
        }
        catch (Exception ex)
        {
            configFuture = CompletableFuture.failedFuture(ex);
        }

        return configFuture;
    }

    @Override
    public void watchResource(
        URL resourceURL)
    {
        // TODO: Ati
    }

    @Override
    public void close()
    {
        futures.values().forEach(future -> future.cancel(true));
        configQueue.add(CLOSE_REQUESTED);
    }

    private EngineConfig sendSync(
        URI configURI)
    {
        HttpClient client = HttpClient.newBuilder()
            .version(HTTP_2)
            .followRedirects(NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(configURI)
            .build();
        HttpResponse<String> response;
        try
        {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception ex)
        {
            handleException(ex, configURI);
            return null;
        }
        return handleConfigChange(response);
    }

    private void sendAsync(
        URI configURI,
        String etag)
    {
        HttpClient client = HttpClient.newBuilder()
            .version(HTTP_2)
            .followRedirects(NORMAL)
            .build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(configURI);
        if (etag != null && !etag.isEmpty())
        {
            requestBuilder = requestBuilder.headers("If-None-Match", etag, "Prefer", "wait=86400");
        }

        CompletableFuture<Void> future = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handleConfigChange)
            .exceptionally(ex -> handleException(ex, configURI));
        futures.put(configURI, future);
    }

    private Void handleException(
        Throwable throwable,
        URI configURI)
    {
        scheduleRequest(configURI, pollSeconds);
        return null;
    }

    private EngineConfig handleConfigChange(
        HttpResponse<String> response)
    {
        EngineConfig config = null;
        try
        {
            URI configURI = response.request().uri();
            int statusCode = response.statusCode();
            int pollIntervalSeconds = 0;
            if (statusCode == 404)
            {
                config = configChangeListener.apply(configURI.toURL(), "");
                pollIntervalSeconds = this.pollSeconds;
            }
            else if (statusCode >= 500 && statusCode <= 599)
            {
                pollIntervalSeconds = this.pollSeconds;
            }
            else
            {
                Optional<String> etagOptional = response.headers().firstValue("Etag");
                String configText = response.body();

                if (etagOptional.isPresent())
                {
                    String oldEtag = etags.getOrDefault(configURI, "");
                    if (!oldEtag.equals(etagOptional.get()))
                    {
                        etags.put(configURI, etagOptional.get());
                        config = configChangeListener.apply(configURI.toURL(), configText);
                    }
                    else if (response.statusCode() != 304)
                    {
                        pollIntervalSeconds = this.pollSeconds;
                    }
                }
                else
                {
                    byte[] configHash = configHashes.get(configURI);
                    byte[] newConfigHash = computeHash(configText);
                    if (!Arrays.equals(configHash, newConfigHash))
                    {
                        configHashes.put(configURI, newConfigHash);
                        config = configChangeListener.apply(configURI.toURL(), configText);
                    }
                    pollIntervalSeconds = this.pollSeconds;
                }
            }
            futures.remove(configURI);
            scheduleRequest(configURI, pollIntervalSeconds);
        }
        catch (MalformedURLException ex)
        {
            rethrowUnchecked(ex);
        }
        return config;
    }

    private void scheduleRequest(URI configURI, int pollIntervalSeconds)
    {
        if (pollIntervalSeconds == 0)
        {
            configQueue.add(configURI);
        }
        else
        {
            executor.schedule(() -> configQueue.add(configURI), pollIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    private URI toURI(
        URL configURL)
    {
        URI configURI = null;
        try
        {
            configURI = configURL.toURI();
        }
        catch (URISyntaxException ex)
        {
            rethrowUnchecked(ex);
        }
        return configURI;
    }
}
