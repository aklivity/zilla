package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    private MessageDigest md5;
    protected final BiConsumer<URL, String> changeListener;
    protected final CountDownLatch initConfigLatch;

    protected WatcherTask(
        BiConsumer<URL, String> changeListener)
    {
        this.changeListener = changeListener;
        this.initConfigLatch = new CountDownLatch(1);
        try
        {
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

    public abstract void onURLDiscovered(
        URL configURL);

    protected byte[] computeHash(String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }
}
