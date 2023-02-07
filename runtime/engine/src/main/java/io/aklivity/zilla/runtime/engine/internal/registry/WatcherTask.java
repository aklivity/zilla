package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
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
        MessageDigest md5 = null;
        try
        {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        this.md5 = md5;
    }

    public abstract void watch(
        URL configURL);

    public abstract void doInitialConfiguration(
        URL configURL) throws Exception;

    protected byte[] computeHash(String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }
}
