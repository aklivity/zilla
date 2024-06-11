/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.watcher;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public class EngineConfigWatcher
{
    private final URL configURL;
    private final FileSystem fileSystem;
    private final Function<String, String> readURL;
    private final Map<String, Set<String>> resources;
    private final Map<String, ResourceWatcherTask> resourceTasks;
    private final Consumer<Set<String>> resourceChangeListener;
    private final ConfigWatcherTask configWatcherTask;

    public EngineConfigWatcher(
        URL configURL,
        Function<String, String> readURL,
        Function<String, EngineConfig> configChangeListener,
        Consumer<Set<String>> resourceChangeListener)
    {
        this.configURL = configURL;
        this.fileSystem = resolveFileSystem(configURL);
        this.readURL = readURL;
        this.resources = new ConcurrentHashMap<>();
        this.resourceTasks = new ConcurrentHashMap<>();
        this.resourceChangeListener = resourceChangeListener;
        this.configWatcherTask = new ConfigWatcherTask(this.fileSystem, configChangeListener, readURL);
    }

    public void startWatchingConfig() throws Exception
    {
        configWatcherTask.submit();
        configWatcherTask.watchConfig(configURL).get();
    }

    public void addResources(
        NamespaceConfig namespace)
    {
        namespace.resources.forEach(resource ->
            {
                resources.computeIfAbsent(resource, i ->
                    {
                        startWatchingResource(resource, namespace.name);
                        return ConcurrentHashMap.newKeySet();
                    }
                ).add(namespace.name);
                resourceTasks.get(resource).addNamespace(namespace.name);
            }
        );
    }

    public void removeNamespace(
        String namespace)
    {
        resources.entrySet().removeIf(e ->
            {
                String resource = e.getKey();
                Set<String> namespaces = e.getValue();
                namespaces.remove(namespace);
                boolean empty = namespaces.isEmpty();
                if (empty)
                {
                    stopWatchingResource(resource);
                }
                else
                {
                    removeNamespaceFromWatchedResource(resource, namespace);
                }
                return empty;
            }
        );
    }

    private void startWatchingResource(
        String resource,
        String namespace)
    {
        try
        {
            ResourceWatcherTask watcherTask = new ResourceWatcherTask(fileSystem, resourceChangeListener, readURL);
            watcherTask.addNamespace(namespace);
            watcherTask.submit();
            URL resourceURL = new URL(configURL, resource);
            watcherTask.watchResource(resourceURL);
            resourceTasks.put(resource, watcherTask);
            System.out.printf("started watching resource: %s resourceURL: %s\n", resource, resourceURL); // TODO: Ati
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private void stopWatchingResource(
        String resource)
    {
        try
        {
            resourceTasks.remove(resource).close();
            System.out.println("stopped watching resource: " + resource); // TODO: Ati
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private void removeNamespaceFromWatchedResource(
        String resource,
        String namespace)
    {
        resourceTasks.get(resource).removeNamespace(namespace);
    }

    public void close()
    {
        resourceTasks.forEach((resource, watcherTask) ->
        {
            try
            {
                watcherTask.close();
            }
            catch (Exception ex)
            {
                rethrowUnchecked(ex);
            }
        });
        try
        {
            configWatcherTask.close();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
    }

    // TODO: Ati - chk if this can be simplified
    // TODO: Ati - check this after adding hfs; this should just work fine for http
    private static FileSystem resolveFileSystem(
        URL url)
    {
        FileSystem result = null;
        try
        {
            URI uri = url.toURI();
            String location;
            if ("file".equals(uri.getScheme()))
            {
                location = uri.getSchemeSpecificPart();
            }
            else
            {
                location = uri.toString();
            }
            result = Path.of(location).getFileSystem();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }
}
