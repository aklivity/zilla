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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class HttpWatcherTask extends WatcherTask
{
    private static final String INTIAL_ETAG = "INIT";
    private final Map<URI, String> etags;
    private final Map<URI, byte[]> configHashes;
    private final Map<URI, CompletableFuture<Void>> futures;
    private final Queue<URI> configWatcherQueue;

    //If server does not support long-polling use this interval
    private final int pollIntervalSeconds;
    private volatile boolean closed = false;

    public HttpWatcherTask(
        BiConsumer<URL, String> configChangeListener,
        int pollIntervalSeconds)
    {
        super(configChangeListener);
        this.etags = new ConcurrentHashMap<>();
        this.configHashes = new ConcurrentHashMap<>();
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
        sendAsync(configURI, INTIAL_ETAG);
    }

    @Override
    public void close()
    {
        futures.values().forEach(future -> future.cancel(true));
        closed = true;
    }

    private void sendAsync(
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
        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handleConfigChange)
            .exceptionally(ex -> handleException(ex, configURI));
        futures.put(configURI, future);
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
            int statusCode = response.statusCode();
            if (statusCode == 404)
            {
                changeListener.accept(configURI.toURL(), "");
                initConfigLatch.countDown();
                TimeUnit.SECONDS.sleep(pollIntervalSeconds);
            }
            else if (statusCode >= 500 && statusCode <= 599)
            {
                TimeUnit.SECONDS.sleep(pollIntervalSeconds);
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
                        changeListener.accept(configURI.toURL(), configText);
                        initConfigLatch.countDown();
                    }
                    else if (response.statusCode() != 304)
                    {
                        TimeUnit.SECONDS.sleep(pollIntervalSeconds);
                    }
                }
                else
                {
                    byte[] configHash = configHashes.get(configURI);
                    byte[] newConfigHash = computeHash(configText);
                    if (!Arrays.equals(configHash, newConfigHash))
                    {
                        configHashes.put(configURI, newConfigHash);
                        changeListener.accept(configURI.toURL(), configText);
                        initConfigLatch.countDown();
                    }
                    TimeUnit.SECONDS.sleep(pollIntervalSeconds);
                }
            }
            futures.remove(configURI);
            configWatcherQueue.add(configURI);
        }
        catch (MalformedURLException ex)
        {
            rethrowUnchecked(ex);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
