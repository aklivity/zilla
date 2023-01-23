package io.aklivity.zilla.runtime.engine.internal.registry;

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


public class FileWatcherTask extends WatcherTask
{
    private byte[] configHash;

    public FileWatcherTask(URL configURL, Runnable configChangeListener)
    {
        super(configURL, configChangeListener);
    }

    @Override
    public boolean run()
    {
        try
        {
            Path configPath = Paths.get(new File(configURL.getPath()).getAbsolutePath());

            WatchService watchService = FileSystems.getDefault().newWatchService();
            configPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);

            Path configFileName = configPath.getFileName();
            configHash = computeHash();
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
                            byte[] newConfigHash = computeHash();
                            if (!Arrays.equals(configHash, newConfigHash))
                            {
                                configHash = newConfigHash;
                                configChangeListener.run();
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
            rethrowUnchecked(ex);
        }
        return true;
    }

    private byte[] computeHash()
    {
        byte[] hash = new byte[0];
        try
        {
            URLConnection connection = configURL.openConnection();
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            try (InputStream input = connection.getInputStream())
            {
                byte[] bytes = input.readAllBytes();
                if (bytes.length == 0)
                {
                    return hash;
                }
                hash = md5Digest.digest(bytes);
            }
        }
        catch (IOException | NoSuchAlgorithmException ex)
        {
            return hash;
        }
        return hash;
    }
}
