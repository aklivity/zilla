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
package io.aklivity.zilla.runtime.common.yaml.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlGeneratorImpl implements YamlGenerator
{
    private final Writer writer;
    private YamlValue root;
    private boolean written;
    private boolean closed;

    public YamlGeneratorImpl(
        Writer writer)
    {
        this.writer = writer;
    }

    public YamlGeneratorImpl(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public YamlGenerator write(
        YamlValue value)
    {
        ensureOpen();
        if (written)
        {
            throw new IllegalStateException("YAML document has already been written");
        }
        if (root != null)
        {
            throw new IllegalStateException("Only one root YAML value is supported");
        }
        root = value;
        return this;
    }

    @Override
    public void flush()
    {
        writeDocument();
        try
        {
            writer.flush();
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    @Override
    public void close()
    {
        if (!closed)
        {
            writeDocument();
            try
            {
                writer.close();
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
            closed = true;
        }
    }

    private void writeDocument()
    {
        ensureOpen();
        if (!written && root != null)
        {
            try
            {
                YamlEmitter.write(YamlValues.node(root), writer);
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
            written = true;
        }
    }

    private void ensureOpen()
    {
        if (closed)
        {
            throw new IllegalStateException("YAML generator is closed");
        }
    }
}
