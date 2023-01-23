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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class HttpWatcherTask extends WatcherTask
{
    private final Map<URI, String> etags;
    private final Map<URI, byte[]> configHashes;
    private final Map<URI, Instant> startTimes;
    private final Map<URI, CompletableFuture<Void>> futures;
    private final Queue<URI> configWatcherQueue;

    //If server does not support long-polling use this interval
    private final int pollIntervalSeconds;
    private volatile boolean closed = false;
    private static final int LONG_POLLING_WAIT_SECONDS = 86400;

    public HttpWatcherTask(
        BiConsumer<URL, String> configChangeListener,
        int pollIntervalSeconds)
    {
        super(configChangeListener);
        this.etags = new ConcurrentHashMap<>();
        this.configHashes = new ConcurrentHashMap<>();
        this.startTimes = new ConcurrentHashMap<>();
        this.futures = new ConcurrentHashMap<>();
        this.configWatcherQueue = new LinkedBlockingQueue<>();
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    @Override
    public Void call()
    {
        while (!closed)
        {
            if (!configWatcherQueue.isEmpty())
            {
                URI configURI = configWatcherQueue.poll();
                String etag = etags.getOrDefault(configURI, "");
                sendAsync(configURI, etag);
            }
        }
        return null;
    }

    @Override
    public void onURLDiscovered(
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
        sendAsync(configURI, "");
    }

    @Override
    public void close()
    {
        futures.values().forEach(future -> future.cancel(true));
        closed = true;
    }

    private Future<Void> sendAsync(
        URI configURI,
        String etag)
    {

        HttpClient client = HttpClient.newBuilder()
            .version(HTTP_2)
            .followRedirects(NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .headers("If-None-Match", etag, "Prefer", "wait=86400")
            .uri(configURI)
            .build();
        startTimes.put(configURI, Instant.now());
        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handleConfigChange)
            .exceptionally(ex -> handleException(ex, configURI));
        futures.put(configURI, future);
        return future;
    }

    private Void handleException(
        Throwable ex,
        URI configURI)
    {
        try
        {
            TimeUnit.SECONDS.sleep(pollIntervalSeconds);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        configWatcherQueue.add(configURI);
        return null;
    }

    private void handleConfigChange(
        HttpResponse<String> response)
    {
        try
        {
            URI configURI = response.request().uri();
            Optional<String> requestEtag = response.request().headers().firstValue("If-None-Match");
            Optional<String> etagOptional = response.headers().firstValue("Etag");
            int statusCode = response.statusCode();
            if (statusCode == 404 || !isLongPollingSupported(configURI, requestEtag, etagOptional, Instant.now()))
            {
                changeListener.accept(configURI.toURL(), "");
                initConfigLatch.countDown();
            }
            else if (statusCode == 200)
            {
                String configText = response.body();
                if (etagOptional.isPresent() && !etags.getOrDefault(configURI, "").equals(etagOptional.get()))
                {
                    etags.put(configURI, etagOptional.get());
                    changeListener.accept(configURI.toURL(), configText);
                }
                else if (etagOptional.isEmpty())
                {
                    byte[] configHash = configHashes.get(configURI);
                    byte[] newConfigHash = computeHash(configText);
                    if (!Arrays.equals(configHash, newConfigHash))
                    {
                        configHashes.put(configURI, newConfigHash);
                        changeListener.accept(configURI.toURL(), configText);
                    }
                }
                initConfigLatch.countDown();
            }
            futures.remove(configURI);
            configWatcherQueue.add(configURI);
        }
        catch (MalformedURLException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private boolean isLongPollingSupported(
        URI configURI,
        Optional<String> requestEtag,
        Optional<String> responseEtag,
        Instant end)
    {
        if (responseEtag.isEmpty())
        {
            return false;
        }
        return requestEtag.get().isEmpty() || !etags.getOrDefault(configURI, "").equals(responseEtag.get()) ||
            Duration.between(startTimes.get(configURI), end).getSeconds() >= LONG_POLLING_WAIT_SECONDS;
    }
}
