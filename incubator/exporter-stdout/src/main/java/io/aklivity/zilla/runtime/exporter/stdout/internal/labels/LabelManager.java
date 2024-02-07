/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.exporter.stdout.internal.labels;

import static java.nio.channels.Channels.newReader;
import static java.nio.channels.Channels.newWriter;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.agrona.LangUtil;

public final class LabelManager
{
    private final List<String> labels;
    private final Map<String, Integer> labelIds;
    private final Path labelsPath;

    private long sizeInBytes;

    public LabelManager(
        Path directory)
    {
        this.labels =  new ArrayList<>();
        this.labelIds = new HashMap<>();

        this.labelsPath = directory.resolve("labels");
        this.sizeInBytes = -1L;
    }

    public synchronized int supplyLabelId(
        String label)
    {
        checkSnapshot();
        return labelIds.computeIfAbsent(label, this::nextLabelId);
    }

    public synchronized String lookupLabel(
        int labelId)
    {
        if (labelId < 1 || labelId > labels.size())
        {
            checkSnapshot();
        }

        return labels.get(labelId - 1);
    }

    private int nextLabelId(
        String nextLabel)
    {
        try (FileChannel channel = FileChannel.open(labelsPath, APPEND))
        {
            try (BufferedWriter out = new BufferedWriter(newWriter(channel, UTF_8.name())))
            {
                out.write(nextLabel);
                out.write('\n');
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        labels.add(nextLabel);

        return labels.size();
    }

    private void checkSnapshot()
    {
        try
        {
            if (!Files.exists(labelsPath))
            {
                this.sizeInBytes = -1L;
            }

            if (this.sizeInBytes == -1L || this.sizeInBytes < Files.size(labelsPath))
            {
                Files.createDirectories(labelsPath.getParent());
                try (FileChannel channel = FileChannel.open(labelsPath, CREATE, READ, WRITE))
                {
                    labels.clear();
                    labelIds.clear();

                    try (BufferedReader in = new BufferedReader(newReader(channel, UTF_8.name())))
                    {
                        for (String label = in.readLine(); label != null; label = in.readLine())
                        {
                            labels.add(label);
                            labelIds.put(label, labels.size());
                        }

                        this.sizeInBytes = channel.position();
                    }
                }
            }
        }
        catch (final IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
