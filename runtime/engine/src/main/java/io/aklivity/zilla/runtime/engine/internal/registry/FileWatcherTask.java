package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Consumer;


public class FileWatcherTask extends WatcherTask
{
    private byte[] configHash;

    public FileWatcherTask(URL configURL, Consumer<String> configChangeListener)
    {
        super(configURL, configChangeListener);
    }

    @Override
    public boolean run()
    {
        doInitialConfiguration();

        try (WatchService watchService = FileSystems.getDefault().newWatchService())
        {
            Path configPath = Paths.get(new File(configURL.getPath()).getAbsolutePath());

            configPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);

            Path configFileName = configPath.getFileName();
            while (true)
            {
                try
                {
                    final WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents())
                    {
                        final Path changed = (Path) event.context();
                        if (changed.equals(configFileName))
                        {
                            String configText = readConfigText();
                            byte[] newConfigHash = computeHash(configText);
                            if (!Arrays.equals(configHash, newConfigHash))
                            {
                                configHash = newConfigHash;
                                configChangeListener.accept(configText);
                            }
                        }
                    }
                    key.reset();
                }
                catch (InterruptedException ex)
                {
                    watchService.close();
                    Thread.currentThread().interrupt();
                }
            }
        }
        catch (Exception ex)
        {
            initConfigLatch.countDown();
            rethrowUnchecked(ex);
        }
        return true;
    }

    private void doInitialConfiguration()
    {
        String configText = readConfigText();
        configChangeListener.accept(configText);
        initConfigLatch.countDown();
        configHash = computeHash(configText);
    }

    private String readConfigText()
    {
        String configText;
        if (configURL == null)
        {
            configText = CONFIG_TEXT_DEFAULT;
        }
        else
        {
            try
            {
                URLConnection connection = configURL.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    configText = new String(input.readAllBytes(), UTF_8);
                }
            }
            catch (IOException ex)
            {
                configText = CONFIG_TEXT_DEFAULT;
            }
        }
        return configText;
    }

    private byte[] computeHash(String configText)
    {
        byte[] hash = new byte[0];
        try
        {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            hash = md5Digest.digest(configText.getBytes(UTF_8));
        }
        catch (NoSuchAlgorithmException ex)
        {
            return hash;
        }
        return hash;
    }
}
