/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class TmpDump // TMPDUMP
{
    static synchronized void dump(
        String kind,
        String text)
    {
        try
        {
            Files.writeString(Path.of("/tmp/llmdump.log"), kind + "\t" + System.getProperty("llm.test") + "\t" +
                text.replace("\n", "\\n") + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (java.io.IOException ex)
        {
            ex.printStackTrace();
        }
    }

    private TmpDump()
    {
    }
}
