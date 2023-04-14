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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public class HttpWatcherTask extends WatcherTask
{
    private static final URI CLOSE_REQUESTED = URI.create("http://localhost:12345");

    private final Map<URI, String> etags;
    private final Map<URI, byte[]> configHashes;
    private final Map<URI, CompletableFuture<Void>> futures;
    private final BlockingQueue<URI> configQueue;
    private final int pollSeconds;

    public HttpWatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener,
        int pollSeconds)
    {
        super(changeListener);
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
    public CompletableFuture<NamespaceConfig> watch(
        URL configURL)
    {
        URI configURI = toURI(configURL);
        NamespaceConfig config = sendSync(configURI);
        if (config == null)
        {
            return CompletableFuture.failedFuture(new Exception("Parsing of the initial configuration failed."));
        }
        return CompletableFuture.completedFuture(config);
    }

    @Override
    public String readURL(
        URL configURL)
    {
        String output = "";
        HttpClient client = HttpClient.newBuilder()
            .version(HTTP_2)
            .followRedirects(NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(toURI(configURL))
            .build();

        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            output = response.body();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return output;
    }

    @Override
    public void close()
    {
        futures.values().forEach(future -> future.cancel(true));
        configQueue.add(CLOSE_REQUESTED);
    }

    private NamespaceConfig sendSync(
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

    private NamespaceConfig handleConfigChange(
        HttpResponse<String> response)
    {
        NamespaceConfig config = null;
        try
        {
            URI configURI = response.request().uri();
            int statusCode = response.statusCode();
            int pollIntervalSeconds = 0;
            if (statusCode == 404)
            {
                config = changeListener.apply(configURI.toURL(), "");
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
                        config = changeListener.apply(configURI.toURL(), configText);
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
                        config = changeListener.apply(configURI.toURL(), configText);
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
