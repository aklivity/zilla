package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    private final MessageDigest md5;
    private final URL rootConfigURL;

    protected final ScheduledExecutorService executor;
    protected final BiFunction<URL, String, NamespaceConfig> changeListener;

    protected WatcherTask(
        URL rootConfigURL,
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        this.rootConfigURL = rootConfigURL;
        this.changeListener = changeListener;
        this.md5 = initMessageDigest("MD5");
        this.executor  = Executors.newScheduledThreadPool(2);
    }

    public abstract Future<Void> submit();

    public String readURL(
        String location)
    {
        String output = null;
        try
        {
            final URL fileURL = new URL(rootConfigURL, location);
            if ("http".equals(fileURL.getProtocol()) || "https".equals(fileURL.getProtocol()))
            {
                HttpClient client = HttpClient.newBuilder()
                    .version(HTTP_2)
                    .followRedirects(NORMAL)
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(fileURL.toURI())
                    .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

                output = response.body();
            }
            else
            {

                URLConnection connection = fileURL.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    output = new String(input.readAllBytes(), UTF_8);
                }
            }
        }
        catch (IOException | URISyntaxException | InterruptedException ex)
        {
            output = "";
        }
        return output;
    }


    public abstract CompletableFuture<NamespaceConfig> watch(
        URL configURL);

    protected byte[] computeHash(
        String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }

    private MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest md5 = null;
        try
        {
            md5 = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return md5;
    }
}
