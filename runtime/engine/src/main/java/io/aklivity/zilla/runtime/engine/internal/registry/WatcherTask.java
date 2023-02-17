package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    private final MessageDigest md5;

    protected final BiFunction<URL, String, NamespaceConfig> changeListener;

    protected WatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        this.changeListener = changeListener;
        this.md5 = initMessageDigest("MD5");
    }

    public abstract String readURL(
        URL configURL);

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
