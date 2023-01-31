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
import java.nio.file.ClosedWatchServiceException;
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
        BiConsumer<URL, String> changeListener)
    {
        super(changeListener);
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
    public Void call()
    {
        while (true)
        {
            try
            {
                final WatchKey key = watchService.take();
                final Path parent = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents())
                {
                    final Path changed = parent.resolve((Path) event.context());
                    System.out.println("Got event for: " + changed + " event type is: " + event.kind());
                    System.out.println("Confighashes: " + configHashes);
                    if (configHashes.containsKey(changed))
                    {
                        String newConfigText = readConfigText(configURLs.get(changed));
                        System.out.println("New config text: " + newConfigText);
                        byte[] oldConfigHash = configHashes.remove(changed);
                        byte[] newConfigHash = computeHash(newConfigText);
                        if (!Arrays.equals(oldConfigHash, newConfigHash))
                        {
                            URL changedURL = configURLs.remove(changed);
                            // Real path could change with symlinks -> recalculate real path
                            Path configPath = getRealPathAndRegisterWatcher(changedURL);
                            System.out.println("Key cancelled");
                            key.cancel();
                            configHashes.put(configPath, newConfigHash);
                            configURLs.put(configPath, changedURL);
                            changeListener.accept(changedURL, newConfigText);
                        }
                    }
                    else
                    {
                        System.out.println("Key reset");
                        key.reset();
                    }
                }
            }
            catch (InterruptedException | ClosedWatchServiceException ex)
            {
                break;
            }
        }

        return null;
    }

    @Override
    public void onURLDiscovered(
        URL configURL)
    {
        Path configPath = getRealPathAndRegisterWatcher(configURL);

        configURLs.put(configPath, configURL);

        String configText = readConfigText(configURL);
        configHashes.put(configPath, computeHash(configText));
        changeListener.accept(configURL, configText);
    }

    private Path getRealPathAndRegisterWatcher(URL configURL)
    {
        Path configPath = Paths.get(new File(configURL.getPath()).getAbsolutePath());
        try
        {
            configPath = configPath.toRealPath();
        }
        catch (IOException ignored)
        {
            // If the file not exists, we can ignore and create a watcher regardless.
        }
        try
        {
            System.out.println("Real configPath is: " + configPath);
            configPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
            System.out.println("Registered watcher to: " + configPath.getParent());
        }
        catch (IOException ignored)
        {
        }
        return configPath;
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

    @Override
    public void close() throws IOException
    {
        watchService.close();
    }
}
