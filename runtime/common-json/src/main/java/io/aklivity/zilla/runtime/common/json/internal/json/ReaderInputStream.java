/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import jakarta.json.JsonException;

final class ReaderInputStream
{
    private ReaderInputStream()
    {
    }

    static InputStream from(
        Reader reader)
    {
        StringBuilder builder = new StringBuilder();
        char[] chunk = new char[1024];
        try
        {
            for (int read = reader.read(chunk); read != -1; read = reader.read(chunk))
            {
                builder.append(chunk, 0, read);
            }
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
        return new ByteArrayInputStream(builder.toString().getBytes(UTF_8));
    }
}
