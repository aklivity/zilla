package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.File;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.agrona.ErrorHandler;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Tuning;

public class FileWatcherTask implements WatcherTask
{
    private final URL configURL;
    private final Collection<URL> schemaTypes;
    private final Function<String, Guard> guardsByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final Collection<DispatchAgent> dispatchers;
    private final ErrorHandler errorHandler;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private NamespaceConfig rootNamespace;

    public FileWatcherTask(
        URL configURL,
        Collection<URL> schemaTypes,
        Function<String, Guard> guardsByType,
        ToIntFunction<String> supplyId,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        Collection<DispatchAgent> dispatchers,
        ErrorHandler errorHandler,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        List<EngineExtSpi> extensions)
    {
        this.configURL = configURL;
        this.schemaTypes = schemaTypes;
        this.guardsByType = guardsByType;
        this.supplyId = supplyId;
        this.maxWorkers = maxWorkers;
        this.tuning = tuning;
        this.dispatchers = dispatchers;
        this.errorHandler = errorHandler;
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.extensions = extensions;
    }

    @Override
    public Void call() throws Exception
    {
        try
        {
            WatchService watchService;
            Path configPath = Paths.get(new File(configURL.getPath()).getAbsolutePath());

            watchService = FileSystems.getDefault().newWatchService();
            configPath.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);

            Path configFileName = configPath.getFileName();
            while (true)
            {
                if (rootNamespace != null)
                {
                    try
                    {
                        final WatchKey key = watchService.take();
                        // Sleep is needed to prevent receiving two separate ENTRY_MODIFY events:
                        // file modified and timestamp updated.
                        // Instead, receive one ENTRY_MODIFY event with two counts.
                        Thread.sleep(50);

                        for (WatchEvent<?> event : key.pollEvents())
                        {
                            final Path changed = (Path) event.context();
                            if (changed.equals(configFileName))
                            {
                                commonPool().submit(new UnregisterTask(dispatchers, rootNamespace, context, extensions));
                                rootNamespace = commonPool().submit(
                                    new RegisterTask(configURL, schemaTypes, guardsByType, supplyId, maxWorkers, tuning,
                                        dispatchers, errorHandler, logger, context, config, extensions)
                                ).get();
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
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return null;
    }

    @Override
    public void setRootNamespace(NamespaceConfig rootNamespace)
    {
        this.rootNamespace = rootNamespace;
    }
}
