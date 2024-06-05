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

import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResourceWatchManager
{
    private final Map<String, Set<String>> resources;
    private final Map<String, ResourceWatcherTask> resourceTasks;

    private FileSystem fileSystem;
    private Consumer<Set<String>> resourceChangeListener;
    private Function<String, String> readURL;

    public ResourceWatchManager()
    {
        this.resources = new ConcurrentHashMap<>();
        this.resourceTasks = new ConcurrentHashMap<>();
    }

    public void initialize(
        FileSystem fileSystem,
        Consumer<Set<String>> resourceChangeListener,
        Function<String, String> readURL)
    {
        this.fileSystem = fileSystem;
        this.resourceChangeListener = resourceChangeListener;
        this.readURL = readURL;
    }

    public void addResources(
        List<String> additionalResources,
        String namespace)
    {
        additionalResources.forEach(resource ->
            {
                resources.computeIfAbsent(resource, i ->
                    {
                        startWatchingResource(resource, namespace);
                        return ConcurrentHashMap.newKeySet();
                    }
                ).add(namespace);
                resourceTasks.get(resource).addNamespace(namespace);
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
            URL resourceURL = Path.of(resource).toUri().toURL();
            watcherTask.watchResource(resourceURL);
            resourceTasks.put(resource, watcherTask);
            System.out.println("started watching resource: " + resource); // TODO: Ati
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
    }
}
