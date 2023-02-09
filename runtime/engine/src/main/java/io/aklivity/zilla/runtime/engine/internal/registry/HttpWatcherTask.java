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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public class HttpWatcherTask extends WatcherTask
{
    private static final String INTIAL_ETAG = "INIT";

    private final Map<URI, String> etags;
    private final Map<URI, byte[]> configHashes;
    private final Map<URI, CompletableFuture<Void>> futures;
    private final Queue<URI> configWatcherQueue;
    private final ScheduledExecutorService executor;
    //If server does not support long-polling use this interval
    private final int pollIntervalSeconds;
    private volatile boolean closed;

    public HttpWatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener,
        int pollIntervalSeconds)
    {
        super(changeListener);
        this.etags = new ConcurrentHashMap<>();
        this.configHashes = new ConcurrentHashMap<>();
        this.futures = new ConcurrentHashMap<>();
        this.configWatcherQueue = new LinkedBlockingQueue<>();
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.executor  = Executors.newSingleThreadScheduledExecutor();
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
    public CompletableFuture<NamespaceConfig> watch(
        URL configURL)
    {
        URI configURI = toURI(configURL);
        NamespaceConfig config = sendSync(configURI, INTIAL_ETAG);
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
        closed = true;
    }

    private NamespaceConfig sendSync(
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
        Throwable throwable,
        URI configURI)
    {
        scheduleRequest(configURI, pollIntervalSeconds);
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
                pollIntervalSeconds = this.pollIntervalSeconds;
            }
            else if (statusCode >= 500 && statusCode <= 599)
            {
                pollIntervalSeconds = this.pollIntervalSeconds;
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
                        pollIntervalSeconds = this.pollIntervalSeconds;
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
                    pollIntervalSeconds = this.pollIntervalSeconds;
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
            configWatcherQueue.add(configURI);
        }
        else
        {
            executor.schedule(() -> configWatcherQueue.add(configURI), pollIntervalSeconds, TimeUnit.SECONDS);
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
