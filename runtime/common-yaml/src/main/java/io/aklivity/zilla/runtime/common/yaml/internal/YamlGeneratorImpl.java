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
    private static final int ROOT = 0;
    private static final int OBJECT_VALUE = 1;
    private static final int ARRAY_ELEMENT = 2;

    private final Writer writer;
    private final YamlConfiguration config;
    private final Deque<Scope> stack;
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
        if (stack.isEmpty() || stack.peek().array)
        {
            throw new IllegalStateException("Expected YAML object context");
        }
        Scope context = stack.peek();
        if (context.pendingKey != null)
        {
            throw new IllegalStateException("YAML object key has already been written");
        }
        context.pendingKey = name;
        return this;
    }

    @Override
    public YamlGenerator write(
        String value)
    {
        beginScalar(YamlEmitter.formatPlain(value));
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
        beginScalar(Integer.toString(value));
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
        beginScalar(Long.toString(value));
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
        Scope context = stack.peek();
        if (!context.array && context.pendingKey != null)
        {
            throw new IllegalStateException("YAML object key has no value");
        }
        stack.pop();
        if (!context.opened)
        {
            closeEmpty(context);
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
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            started = true;
            emit(text);
            emit("\n");
        }
        else
        {
            Scope parent = stack.peek();
            ensureOpened(parent);
            if (parent.array)
            {
                parent.first = false;
                indent(parent.childIndent);
                emit("- ");
                emit(text);
                emit("\n");
            }
            else
            {
                String key = requireKey(parent);
                if (parent.firstInline && parent.first)
                {
                    parent.firstInline = false;
                }
                else
                {
                    indent(parent.childIndent);
                }
                emit(YamlEmitter.formatPlain(key));
                emit(": ");
                emit(text);
                emit("\n");
                parent.first = false;
            }
        }
    }

    private void beginContainer(
        boolean array)
    {
        ensureOpen();
        Scope child;
        if (stack.isEmpty())
        {
            requireRootAvailable();
            started = true;
            child = new Scope(ROOT, array, 0, null);
        }
        else
        {
            Scope parent = stack.peek();
            ensureOpened(parent);
            if (parent.array)
            {
                parent.first = false;
                child = new Scope(ARRAY_ELEMENT, array, parent.childIndent, null);
            }
            else
            {
                String key = requireKey(parent);
                parent.first = false;
                child = new Scope(OBJECT_VALUE, array, parent.childIndent, key);
            }
        }
        stack.push(child);
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
            Scope parent = stack.peek();
            ensureOpened(parent);
            try
            {
                if (parent.array)
                {
                    parent.first = false;
                    YamlEmitter.writeArrayElement(node, writer, parent.childIndent, config);
                }
                else
                {
                    String key = requireKey(parent);
                    if (parent.firstInline && parent.first)
                    {
                        parent.firstInline = false;
                    }
                    else
                    {
                        indent(parent.childIndent);
                    }
                    emit(YamlEmitter.formatPlain(key));
                    emit(":");
                    YamlEmitter.writeObjectValue(node, writer, parent.childIndent, config);
                    parent.first = false;
                }
            }
            catch (IOException ex)
            {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
    }

    private void ensureOpened(
        Scope scope)
    {
        if (!scope.opened)
        {
            scope.opened = true;
            switch (scope.introKind)
            {
            case ROOT -> scope.childIndent = 0;
            case OBJECT_VALUE ->
            {
                indent(scope.introIndent);
                emit(YamlEmitter.formatPlain(scope.introKey));
                emit(":\n");
                scope.childIndent = scope.introIndent + 1;
            }
            case ARRAY_ELEMENT ->
            {
                indent(scope.introIndent);
                if (scope.array)
                {
                    emit("-\n");
                }
                else
                {
                    emit("- ");
                    scope.firstInline = true;
                }
                scope.childIndent = scope.introIndent + 1;
            }
            default ->
            {
            }
            }
        }
    }

    private void closeEmpty(
        Scope scope)
    {
        String body = scope.array ? "[]" : "{}";
        switch (scope.introKind)
        {
        case ROOT -> emit(body);
        case OBJECT_VALUE ->
        {
            indent(scope.introIndent);
            emit(YamlEmitter.formatPlain(scope.introKey));
            emit(": ");
            emit(body);
        }
        case ARRAY_ELEMENT ->
        {
            indent(scope.introIndent);
            emit("- ");
            emit(body);
        }
        default ->
        {
        }
        }
        emit("\n");
    }

    private String requireKey(
        Scope object)
    {
        if (object.pendingKey == null)
        {
            throw new IllegalStateException("Expected YAML object key");
        }
        String key = object.pendingKey;
        object.pendingKey = null;
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

    private void ensureOpen()
    {
        if (closed)
        {
            throw new IllegalStateException("YAML generator is closed");
        }
    }

    private static final class Scope
    {
        private final int introKind;
        private final boolean array;
        private final int introIndent;
        private final String introKey;
        private int childIndent;
        private boolean opened;
        private boolean first = true;
        private boolean firstInline;
        private String pendingKey;

        private Scope(
            int introKind,
            boolean array,
            int introIndent,
            String introKey)
        {
            this.introKind = introKind;
            this.array = array;
            this.introIndent = introIndent;
            this.introKey = introKey;
        }
    }
}
