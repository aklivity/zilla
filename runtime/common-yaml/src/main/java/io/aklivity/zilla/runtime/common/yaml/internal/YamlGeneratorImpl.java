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

import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.ARRAY_ELEMENT;
import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.OBJECT_VALUE;
import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.ROOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import io.aklivity.zilla.runtime.common.yaml.YamlDocument;
import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlStream;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlGeneratorImpl implements YamlGenerator
{
    private final Writer writer;
    private final YamlConfiguration config;
    private final YamlGeneratorStack stack;
    private final char[] numberBuffer;
    private boolean started;
    private boolean closed;

    public YamlGeneratorImpl(
        Writer writer)
    {
        this(writer, YamlConfiguration.DEFAULT);
    }

    public YamlGeneratorImpl(
        Writer writer,
        YamlConfiguration config)
    {
        this.writer = writer;
        this.config = config;
        this.stack = new YamlGeneratorStack();
        this.numberBuffer = new char[20];
    }

    public YamlGeneratorImpl(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public YamlGenerator writeStartObject()
    {
        beginContainer(false);
        return this;
    }

    @Override
    public YamlGenerator writeStartObject(
        String name)
    {
        writeKey(name);
        return writeStartObject();
    }

    @Override
    public YamlGenerator writeStartArray()
    {
        beginContainer(true);
        return this;
    }

    @Override
    public YamlGenerator writeStartArray(
        String name)
    {
        writeKey(name);
        return writeStartArray();
    }

    @Override
    public YamlGenerator writeKey(
        String name)
    {
        ensureOpen();
        if (stack.isEmpty() || stack.array())
        {
            throw new IllegalStateException("Expected YAML object context");
        }
        if (stack.pendingKey() != null)
        {
            throw new IllegalStateException("YAML object key has already been written");
        }
        stack.pendingKey(name);
        return this;
    }

    @Override
    public YamlGenerator write(
        String value)
    {
        beginScalarValue(value);
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        String value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator write(
        int value)
    {
        beginInteger(value);
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        int value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator write(
        long value)
    {
        beginInteger(value);
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        long value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator write(
        double value)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException("Non-finite YAML numbers are not valid JSON values");
        }
        beginScalar(Double.toString(value));
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        double value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator write(
        boolean value)
    {
        beginScalar(value ? "true" : "false");
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        boolean value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator writeNull()
    {
        beginScalar("null");
        return this;
    }

    @Override
    public YamlGenerator writeNull(
        String name)
    {
        writeKey(name);
        return writeNull();
    }

    @Override
    public YamlGenerator write(
        YamlValue value)
    {
        placeNode(YamlValues.node(value));
        return this;
    }

    @Override
    public YamlGenerator write(
        String name,
        YamlValue value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public YamlGenerator writeEnd()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            throw new IllegalStateException("No YAML collection is open");
        }
        if (!stack.array() && stack.pendingKey() != null)
        {
            throw new IllegalStateException("YAML object key has no value");
        }
        boolean opened = stack.opened();
        int kind = stack.kind();
        boolean array = stack.array();
        int introIndent = stack.introIndent();
        String introKey = stack.introKey();
        stack.pop();
        if (!opened)
        {
            closeEmpty(kind, array, introIndent, introKey);
        }
        return this;
    }

    @Override
    public void flush()
    {
        ensureOpen();
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
            if (!stack.isEmpty())
            {
                throw new IllegalStateException("YAML document has an open collection");
            }
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

    void writeStream(
        YamlStream stream)
    {
        ensureOpen();
        if (!stack.isEmpty())
        {
            throw new IllegalStateException("YAML document has an open collection");
        }
        if (started)
        {
            throw new IllegalStateException("YAML document has already been written");
        }
        try
        {
            if (!config.multiDocumentStreams() && stream.size() > 1)
            {
                throw new IllegalStateException("YAML document streams are disabled");
            }
            if (!config.documentMarkers() && !config.preserveSource() && stream.size() > 1)
            {
                throw new IllegalStateException("YAML document markers are disabled");
            }
            int index = 0;
            for (YamlDocument document : stream)
            {
                if (index != 0 && !config.preserveSource())
                {
                    writer.write("---\n");
                }
                YamlEmitter.write(YamlValues.node(document.getValue()), writer, config);
                index++;
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        started = true;
    }

    private void beginScalar(
        String text)
    {
        beginScalarPrefix();
        emit(text);
        emit("\n");
    }

    private void beginScalarValue(
        String value)
    {
        beginScalarPrefix();
        emitScalar(value);
        emit("\n");
    }

    private void beginInteger(
        long value)
    {
        beginScalarPrefix();
        try
        {
            YamlEmitter.writeInteger(writer, value, numberBuffer);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        emit("\n");
    }

    private void beginScalarPrefix()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            started = true;
        }
        else
        {
            ensureOpened();
            if (stack.array())
            {
                stack.clearFirst();
                indent(stack.childIndent());
                emit("- ");
            }
            else
            {
                String key = requireKey();
                if (stack.firstInline() && stack.first())
                {
                    stack.clearFirstInline();
                }
                else
                {
                    indent(stack.childIndent());
                }
                emitScalar(key);
                emit(": ");
                stack.clearFirst();
            }
        }
    }

    private void beginContainer(
        boolean array)
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            started = true;
            stack.push(ROOT, array, 0, null);
        }
        else
        {
            ensureOpened();
            if (stack.array())
            {
                int childIndent = stack.childIndent();
                stack.clearFirst();
                stack.push(ARRAY_ELEMENT, array, childIndent, null);
            }
            else
            {
                String key = requireKey();
                int childIndent = stack.childIndent();
                stack.clearFirst();
                stack.push(OBJECT_VALUE, array, childIndent, key);
            }
        }
    }

    private void placeNode(
        YamlNode node)
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            started = true;
            try
            {
                YamlEmitter.write(node, writer, config);
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
        else
        {
            ensureOpened();
            try
            {
                if (stack.array())
                {
                    stack.clearFirst();
                    YamlEmitter.writeArrayElement(node, writer, stack.childIndent(), config);
                }
                else
                {
                    String key = requireKey();
                    int childIndent = stack.childIndent();
                    if (stack.firstInline() && stack.first())
                    {
                        stack.clearFirstInline();
                    }
                    else
                    {
                        indent(childIndent);
                    }
                    emitScalar(key);
                    emit(":");
                    YamlEmitter.writeObjectValue(node, writer, childIndent, config);
                    stack.clearFirst();
                }
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
    }

    private void ensureOpened()
    {
        if (!stack.opened())
        {
            stack.markOpened();
            switch (stack.kind())
            {
            case OBJECT_VALUE ->
            {
                indent(stack.introIndent());
                emitScalar(stack.introKey());
                emit(":\n");
            }
            case ARRAY_ELEMENT ->
            {
                indent(stack.introIndent());
                if (stack.array())
                {
                    emit("-\n");
                }
                else
                {
                    emit("- ");
                    stack.markFirstInline();
                }
            }
            default ->
            {
            }
            }
        }
    }

    private void closeEmpty(
        int kind,
        boolean array,
        int introIndent,
        String introKey)
    {
        String body = array ? "[]" : "{}";
        switch (kind)
        {
        case OBJECT_VALUE ->
        {
            indent(introIndent);
            emitScalar(introKey);
            emit(": ");
            emit(body);
        }
        case ARRAY_ELEMENT ->
        {
            indent(introIndent);
            emit("- ");
            emit(body);
        }
        default -> emit(body);
        }
        emit("\n");
    }

    private String requireKey()
    {
        String key = stack.pendingKey();
        if (key == null)
        {
            throw new IllegalStateException("Expected YAML object key");
        }
        stack.pendingKey(null);
        return key;
    }

    private void requireRootAvailable()
    {
        if (started)
        {
            throw new IllegalStateException("Only one root YAML value is supported");
        }
    }

    private void indent(
        int depth)
    {
        for (int i = 0; i < depth; i++)
        {
            emit("  ");
        }
    }

    private void emit(
        String text)
    {
        try
        {
            writer.write(text);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void emitScalar(
        String value)
    {
        try
        {
            YamlEmitter.writeScalar(writer, value);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
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
