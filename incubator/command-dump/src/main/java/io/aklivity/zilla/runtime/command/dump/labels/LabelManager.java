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
package io.aklivity.zilla.runtime.command.dump.labels;

import static java.nio.channels.Channels.newReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.LangUtil;

public final class LabelManager
{
    private static final Integer NO_LABEL_ID = Integer.valueOf(-1);

    private final List<String> labels;
    private final Map<String, Integer> labelIds;
    private final Path labelsPath;

    private long sizeInBytes;

    public LabelManager(
        Path directory)
    {
        this.labels =  new CopyOnWriteArrayList<>();
        this.labelIds = new ConcurrentHashMap<>();

        this.labelsPath = directory.resolve("labels");
        this.sizeInBytes = -1L;
    }

    public String lookupLabel(
        int labelId)
    {
        if (labelId < 1 || labelId > labels.size())
        {
            checkSnapshot();
        }

        return labelId >= 1 && labelId <= labels.size() ? labels.get(labelId - 1) : "??";
    }

    public int lookupLabelId(
        String label)
    {
        int labelId = labelIds.getOrDefault(label, NO_LABEL_ID);
        if (labelId == NO_LABEL_ID)
        {
            checkSnapshot();
            labelId = labelIds.getOrDefault(label, NO_LABEL_ID);
        }
        return labelId;
    }

    private void checkSnapshot()
    {
        try
        {
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
