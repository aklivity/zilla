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
import java.util.ArrayDeque;
import java.util.Deque;

import io.aklivity.zilla.runtime.common.yaml.YamlDocument;
import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlStream;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlGeneratorImpl implements YamlGenerator
{
    private final Writer writer;
    private final Deque<Context> stack;
    private YamlNode rootNode;
    private boolean written;
    private boolean closed;

    public YamlGeneratorImpl(
        Writer writer)
    {
        this.writer = writer;
        this.stack = new ArrayDeque<>();
    }

    public YamlGeneratorImpl(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public YamlGenerator writeStartObject()
    {
        add(new YamlObjectNode(1, 1, 0));
        stack.push(new Context(rootNode(), true));
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
        add(new YamlArrayNode(1, 1, 0));
        stack.push(new Context(rootNode(), false));
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
        Context context = objectContext();
        if (context.name != null)
        {
            throw new IllegalStateException("YAML object key has already been written");
        }
        context.name = name;
        return this;
    }

    @Override
    public YamlGenerator write(
        String value)
    {
        add(YamlScalarNode.string(value, 1, 1, 0));
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
        add(YamlScalarNode.number(Integer.toString(value), 1, 1, 0));
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
        add(YamlScalarNode.number(Long.toString(value), 1, 1, 0));
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
        add(YamlScalarNode.number(Double.toString(value), 1, 1, 0));
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
        add(YamlScalarNode.literal(value ? YamlScalarType.TRUE : YamlScalarType.FALSE, 1, 1, 0));
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
        add(YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0));
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
        add(YamlValues.node(value));
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
        Context context = stack.peek();
        if (context.object && context.name != null)
        {
            throw new IllegalStateException("YAML object key has no value");
        }
        stack.pop();
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

    void writeStream(
        YamlStream stream)
    {
        ensureOpen();
        if (!stack.isEmpty())
        {
            throw new IllegalStateException("YAML document has an open collection");
        }
        if (written || rootNode != null)
        {
            throw new IllegalStateException("YAML document has already been written");
        }
        try
        {
            for (YamlDocument document : stream)
            {
                YamlEmitter.write(YamlValues.node(document.getValue()), writer);
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        written = true;
    }

    private void writeDocument()
    {
        ensureOpen();
        if (!stack.isEmpty())
        {
            throw new IllegalStateException("YAML document has an open collection");
        }
        if (!written && rootNode != null)
        {
            try
            {
                YamlEmitter.write(rootNode, writer);
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
            written = true;
        }
    }

    private void add(
        YamlNode node)
    {
        ensureOpen();
        if (written)
        {
            throw new IllegalStateException("YAML document has already been written");
        }

        if (stack.isEmpty())
        {
            if (rootNode != null)
            {
                throw new IllegalStateException("Only one root YAML value is supported");
            }
            rootNode = node;
        }
        else
        {
            Context context = stack.peek();
            if (context.object)
            {
                if (context.name == null)
                {
                    throw new IllegalStateException("Expected YAML object key");
                }
                ((YamlObjectNode) context.node).add(new YamlEntry(context.name, node, 1, 1, 0));
                context.name = null;
            }
            else
            {
                ((YamlArrayNode) context.node).add(node);
            }
        }
    }

    private YamlNode rootNode()
    {
        return stack.isEmpty() ? rootNode : childNode();
    }

    private YamlNode childNode()
    {
        Context parent = stack.peek();
        if (parent.object)
        {
            YamlObjectNode object = (YamlObjectNode) parent.node;
            return object.entries.get(object.entries.size() - 1).value;
        }

        YamlArrayNode array = (YamlArrayNode) parent.node;
        return array.values.get(array.values.size() - 1);
    }

    private Context objectContext()
    {
        if (stack.isEmpty() || !stack.peek().object)
        {
            throw new IllegalStateException("Expected YAML object context");
        }
        return stack.peek();
    }

    private void ensureOpen()
    {
        if (closed)
        {
            throw new IllegalStateException("YAML generator is closed");
        }
    }

    private static final class Context
    {
        final YamlNode node;
        final boolean object;
        String name;

        private Context(
            YamlNode node,
            boolean object)
        {
            this.node = node;
            this.object = object;
        }
    }
}
