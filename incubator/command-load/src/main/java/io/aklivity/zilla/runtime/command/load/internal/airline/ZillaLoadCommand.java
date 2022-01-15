/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.load.internal.airline;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.collections.Long2ObjectHashMap;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.load.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.load.internal.layout.LoadLayout;
import io.aklivity.zilla.runtime.command.load.internal.layout.LoadReader;
import io.aklivity.zilla.runtime.command.load.internal.types.LoadSnapshot;

@Command(name = "load", description = "Show engine load")
public final class ZillaLoadCommand extends ZillaCommand
{
    private static final Pattern LOAD_PATTERN = Pattern.compile("load(\\d+)");

    @Option(name = { "--namespace" })
    public String namespace;

    @Arguments(title = {"name"})
    public List<String> args;

    private final Path directory;
    private final Map<Path, LoadReader> readersByPath;
    private final LabelManager labels;

    public ZillaLoadCommand()
    {
        this.directory = Paths.get(".zilla", "engine");
        this.readersByPath = new LinkedHashMap<>();
        this.labels = new LabelManager(directory);
    }

    @Override
    public void run()
    {
        String name = args != null && args.size() >= 1 ? args.get(0) : null;

        LongPredicate filter = filterBy(namespace, name);

        try (Stream<Path> files = Files.walk(directory, 1))
        {
            List<LoadReader> readers = files.filter(this::isLoadFile)
                 .map(this::supplyLoadReaders)
                 .collect(toList());

            Long2ObjectHashMap<String> namesById = new Long2ObjectHashMap<>();
            for (LoadReader reader : readers)
            {
                for (long id : reader.ids())
                {
                    if (filter.test(id) && !namesById.containsKey(id))
                    {
                        int namespaceId = (int)(id >> Integer.SIZE) & 0xffff_ffff;
                        int localId = (int)(id >> 0) & 0xffff_ffff;

                        String namespace = labels.lookupLabel(namespaceId);
                        String local = labels.lookupLabel(localId);
                        String namespaced = String.format("%s.%s", namespace, local);

                        namesById.put(id, namespaced);
                    }
                }
            }

            int widthMax = namesById.values().stream().mapToInt(String::length).max().orElse(10) + 2;
            String headerFormat = String.format("%%-%ds %%12s %%12s %%12s %%12s %%12s %%12s %%12s %%12s\n", widthMax);
            String entryFormat = String.format("%%-%ds %%12d %%12d %%12d %%12d %%12d %%12d %%12d %%12d\n", widthMax);

            System.out.format(headerFormat,
                    "binding",
                    "rx.opens", "rx.closes", "rx.errors", "rx.bytes",
                    "tx.opens", "tx.closes", "tx.errors", "tx.bytes");

            for (Map.Entry<Long, String> entry : namesById.entrySet())
            {
                long id = entry.getKey();
                String namespaced = entry.getValue();

                long initialOpens = 0L;
                long initialCloses = 0L;
                long initialBytes = 0L;
                int initialErrors = 0;
                long replyOpens = 0L;
                long replyCloses = 0L;
                long replyBytes = 0L;
                int replyErrors = 0;

                for (LoadReader reader : readers)
                {
                    LoadSnapshot snapshot = reader.entry(id);
                    if (snapshot != null)
                    {
                        initialOpens += snapshot.initialOpens();
                        initialCloses += snapshot.initialCloses();
                        initialBytes += snapshot.initialBytes();
                        initialErrors += snapshot.initialErrors();
                        replyOpens += snapshot.replyOpens();
                        replyCloses += snapshot.replyCloses();
                        replyBytes += snapshot.replyBytes();
                        replyErrors += snapshot.replyErrors();
                    }
                }

                System.out.format(entryFormat,
                        namespaced,
                        initialOpens,
                        initialCloses,
                        initialErrors,
                        initialBytes,
                        replyOpens,
                        replyCloses,
                        replyErrors,
                        replyBytes);
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    private boolean isLoadFile(
        Path path)
    {
        return path.getNameCount() - directory.getNameCount() == 1 &&
                LOAD_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private LoadReader supplyLoadReaders(
        Path loadPath)
    {
        return readersByPath.computeIfAbsent(loadPath, this::newLoadReader);
    }

    private LoadReader newLoadReader(
        Path loadPath)
    {
        LoadLayout layout = new LoadLayout.Builder()
                .path(loadPath)
                .build();

        return new LoadReader(layout.buffer());
    }

    private LongPredicate filterBy(
        String namespace,
        String name)
    {
        int namespaceId = namespace != null ? Math.max(labels.lookupLabelId(namespace), 0) : 0;
        int nameId = name != null ? Math.max(labels.lookupLabelId(name), 0) : 0;

        long namespacedId =
            (long) namespaceId << Integer.SIZE |
            (long) nameId << 0;

        long mask =
            (namespace != null ? 0xffff_ffff_0000_0000L : 0x0000_0000_0000_0000L) |
            (name != null ? 0x0000_0000_ffff_ffffL : 0x0000_0000_0000_0000L);
        return id -> (id & mask) == namespacedId;
    }
}
