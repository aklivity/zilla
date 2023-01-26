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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;


public class FileWatcherTask extends WatcherTask
{
    private final Map<Path, byte[]> configHashes;
    private final Map<Path, URL> configURLs;
    private MessageDigest md5;
    private WatchService watchService;

    public FileWatcherTask(
        BiConsumer<URL, String> configChangeListener)
    {
        super(configChangeListener);
        this.configHashes = new HashMap<>();
        this.configURLs = new HashMap<>();
        try
        {
            this.md5 = MessageDigest.getInstance("MD5");
            this.watchService = FileSystems.getDefault().newWatchService();
        }
        catch (NoSuchAlgorithmException | IOException ex)
        {
            rethrowUnchecked(ex);
        }

    }

    @Override
    public boolean run()
    {
        try
        {
            while (true)
            {
                try
                {
                    final WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents())
                    {
                        final Path changed = ((Path) key.watchable()).resolve((Path) event.context());
                        if (configHashes.containsKey(changed))
                        {
                            String newConfigText = readConfigText(configURLs.get(changed));
                            byte[] oldConfigHash = configHashes.get(changed);
                            byte[] newConfigHash = computeHash(newConfigText);
                            if (!Arrays.equals(oldConfigHash, newConfigHash))
                            {
                                configHashes.put(changed, newConfigHash);
                                configChangeListener.accept(configURLs.get(changed), newConfigText);
                            }
                        }
                    }
                    key.reset();
                }
                catch (InterruptedException ex)
                {
                    watchService.close();
                    break;
                }
            }
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        return true;
    }

    @Override
    protected void doInitialConfiguration(
        URL configURL)
    {
        Path configPath = Paths.get(new File(configURL.getPath()).getAbsolutePath());
        configURLs.put(configPath, configURL);

        String configText = "";
        configText = readConfigText(configURL);
        configHashes.put(configPath, computeHash(configText));
        try
        {
            configPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
        }
        catch (IOException ignored)
        {
        }

        configChangeListener.accept(configURL, configText);
    }

    private String readConfigText(
        URL configURL)
    {
        String configText;
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
            return "";
        }
        return configText;
    }

    private byte[] computeHash(
        String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }
}
