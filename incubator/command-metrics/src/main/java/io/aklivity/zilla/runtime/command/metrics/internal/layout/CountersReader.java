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
package io.aklivity.zilla.runtime.command.metrics.internal.layout;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.COUNTER;

import java.util.function.LongSupplier;
import java.util.stream.StreamSupport;

public class CountersReader /*implements FileReader*/
{
    private final CountersLayout layout;

    private LongSupplier[][] recordReaders;

    public CountersReader(
        CountersLayout layout)
    {
        this.layout = layout;
    }

    //@Override
    public FileReader.Kind kind()
    {
        return COUNTER;
    }

    //@Override
    public LongSupplier[][] recordReaders()
    {
        if (recordReaders == null)
        {
            recordReaders = StreamSupport.stream(layout.spliterator(), false).toArray(LongSupplier[][]::new);
        }
        return recordReaders;
    }

    public CountersLayout layout()
    {
        return layout;
    }

    //@Override
    public void close()
    {
        layout.close();
    }
}
