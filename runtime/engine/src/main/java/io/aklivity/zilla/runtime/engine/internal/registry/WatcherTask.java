package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.Closeable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import java.util.concurrent.CountDownLatch;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    private MessageDigest md5;
    protected final BiFunction<URL, String, NamespaceConfig> changeListener;
    protected final CountDownLatch initConfigLatch;

    protected WatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        this.changeListener = changeListener;
        this.initConfigLatch = new CountDownLatch(1);
        try
        {
            //TODO: final
            this.md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    public void awaitInitConfig()
    {
        try
        {
            initConfigLatch.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    };

    public abstract NamespaceConfig watch(
        URL configURL);

    public abstract void doInitialConfiguration(
        URL configURL) throws Exception;

    protected byte[] computeHash(String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }
}
