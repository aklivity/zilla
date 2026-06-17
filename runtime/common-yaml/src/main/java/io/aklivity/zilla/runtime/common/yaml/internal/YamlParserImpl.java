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
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.YamlEvent;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlScalar;
import io.aklivity.zilla.runtime.common.yaml.YamlStream;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlParserImpl implements YamlParser
{
    private final String text;
    private final YamlConfiguration config;
    private final CharSequenceView stringView;

    private boolean streaming;
    private boolean parsed;

    // streaming via the tree-free scanner fast path (default config only)
    private YamlStreamScanner scanner;
    private int scanCursor;
    private int scanCurrent;
    private int buildCursor;

    // streaming via the eager DOM (fallback) — unresolved (raw) tree so the Parse layer stays unresolved
    private Deque<Frame> stack;
    private YamlNode rawRoot;
    private YamlNode root;
    private YamlEvent pending;
    private YamlNode pendingNode;
    private String pendingString;
    private boolean pendingValid;
    private boolean exhausted;

    // currently exposed event
    private YamlEvent current;
    private YamlNode currentNode;
    private String currentString;
    private YamlValue currentValue;

    public YamlParserImpl(
        Reader reader)
    {
        this(reader, YamlConfiguration.DEFAULT);
    }

    public YamlParserImpl(
        Reader reader,
        YamlConfiguration config)
    {
        this.text = readAll(reader);
        this.config = config;
        this.stringView = new CharSequenceView();
        this.scanCurrent = -1;
    }

    public YamlParserImpl(
        InputStream in)
    {
        this(in, UTF_8, YamlConfiguration.DEFAULT);
    }

    public YamlParserImpl(
        InputStream in,
        YamlConfiguration config)
    {
        this(in, UTF_8, config);
    }

    public YamlParserImpl(
        InputStream in,
        Charset charset)
    {
        this(in, charset, YamlConfiguration.DEFAULT);
    }

    public YamlParserImpl(
        InputStream in,
        Charset charset,
        YamlConfiguration config)
    {
        this.text = readAll(in, charset);
        this.config = config;
        this.stringView = new CharSequenceView();
        this.scanCurrent = -1;
    }

    @Override
    public boolean hasNext()
    {
        boolean hasNext;
        if (parsed)
        {
            hasNext = false;
        }
        else
        {
            if (!streaming)
            {
                streaming = true;
                scanner = scanner();
                if (scanner == null)
                {
                    ensureStack();
                }
            }

            if (scanner != null)
            {
                hasNext = scanCursor < scanner.count();
            }
            else
            {
                if (!pendingValid && !exhausted)
                {
                    pending = eagerAdvance();
                    pendingValid = pending != null;
                    exhausted = !pendingValid;
                }
                hasNext = pendingValid;
            }
        }
        return hasNext;
    }

    @Override
    public YamlEvent next()
    {
        if (!hasNext())
        {
            throw new IllegalStateException("No more YAML events");
        }

        currentValue = null;
        if (scanner != null)
        {
            scanCurrent = scanCursor++;
            current = event(scanner.kind(scanCurrent));
        }
        else
        {
            current = pending;
            currentNode = pendingNode;
            currentString = pendingString;
            pendingValid = false;
        }
        return current;
    }

    @Override
    public YamlValue parse()
    {
        if (parsed || streaming)
        {
            throw new IllegalStateException("YAML document has already been parsed");
        }
        parsed = true;
        return YamlValues.wrap(root());
    }

    @Override
    public YamlValue getValue()
    {
        if (currentValue == null && current != null)
        {
            currentValue = scanner != null ? scanValue() : currentNode != null ? YamlValues.wrap(currentNode) : null;
        }
        return currentValue;
    }

    @Override
    public YamlObject getObject()
    {
        return getValue().asYamlObject();
    }

    @Override
    public YamlArray getArray()
    {
        return getValue().asYamlArray();
    }

    @Override
    public YamlScalar getScalar()
    {
        return getValue().asYamlScalar();
    }

    @Override
    public String getString()
    {
        String value;
        if (scanner != null)
        {
            value = scanCurrent < 0 ? null : scanner.string(scanCurrent);
        }
        else
        {
            value = currentString;
        }
        return value;
    }

    @Override
    public CharSequence getStringView()
    {
        CharSequence view;
        if (scanner != null)
        {
            view = scanCurrent < 0 ? null : scanner.stringView(scanCurrent);
        }
        else
        {
            view = currentString != null ? stringView.wrap(currentString, 0, currentString.length()) : null;
        }
        return view;
    }

    @Override
    public String getAnchor()
    {
        return scanner != null ? scanCurrent < 0 ? null : scanner.anchor(scanCurrent) :
            currentNode != null ? currentNode.anchor : null;
    }

    @Override
    public String getAlias()
    {
        return scanner != null ? scanCurrent < 0 ? null : scanner.alias(scanCurrent) :
            currentNode != null ? currentNode.alias : null;
    }

    @Override
    public String getTag()
    {
        return scanner == null && currentNode != null ? currentNode.tag : null;
    }

    @Override
    public void close()
    {
    }

    YamlStream parseStream()
    {
        if (parsed || streaming)
        {
            throw new IllegalStateException("YAML document has already been parsed");
        }
        parsed = true;
        List<YamlNode> nodes = YamlDocumentParser.parseAll(text, config).stream()
            .map(r -> r.node)
            .toList();
        return YamlValues.stream(nodes);
    }

    private YamlStreamScanner scanner()
    {
        YamlStreamScanner candidate = null;
        if (config == null || config.isDefault())
        {
            YamlStreamScanner streamScanner = new YamlStreamScanner();
            if (streamScanner.scan(text, true))
            {
                candidate = streamScanner;
            }
        }
        return candidate;
    }

    private YamlEvent event(
        byte kind)
    {
        return switch (kind)
        {
        case YamlStreamScanner.START_OBJECT -> YamlEvent.START_OBJECT;
        case YamlStreamScanner.END_OBJECT -> YamlEvent.END_OBJECT;
        case YamlStreamScanner.START_ARRAY -> YamlEvent.START_ARRAY;
        case YamlStreamScanner.END_ARRAY -> YamlEvent.END_ARRAY;
        case YamlStreamScanner.KEY_NAME -> YamlEvent.KEY_NAME;
        case YamlStreamScanner.VALUE_STRING -> YamlEvent.VALUE_STRING;
        case YamlStreamScanner.VALUE_NUMBER -> YamlEvent.VALUE_NUMBER;
        case YamlStreamScanner.VALUE_TRUE -> YamlEvent.VALUE_TRUE;
        case YamlStreamScanner.VALUE_FALSE -> YamlEvent.VALUE_FALSE;
        case YamlStreamScanner.VALUE_NULL -> YamlEvent.VALUE_NULL;
        case YamlStreamScanner.ALIAS -> YamlEvent.ALIAS;
        default -> throw new IllegalStateException("Unexpected scanner event: " + kind);
        };
    }

    private YamlValue scanValue()
    {
        YamlValue value;
        byte kind = scanner.kind(scanCurrent);
        if (kind == YamlStreamScanner.END_OBJECT || kind == YamlStreamScanner.END_ARRAY)
        {
            value = null;
        }
        else if (kind == YamlStreamScanner.KEY_NAME)
        {
            value = YamlValues.wrap(YamlScalarNode.string(scanner.string(scanCurrent),
                scanner.line(scanCurrent), scanner.column(scanCurrent), scanner.offset(scanCurrent)));
        }
        else
        {
            buildCursor = scanCurrent;
            value = YamlValues.wrap(buildNode());
        }
        return value;
    }

    private YamlNode buildAlias()
    {
        YamlScalarNode marker = YamlScalarNode.literal(YamlScalarType.NULL,
            scanner.line(buildCursor), scanner.column(buildCursor), scanner.offset(buildCursor));
        marker.alias = scanner.alias(buildCursor);
        buildCursor++;
        return marker;
    }

    private YamlNode buildNode()
    {
        byte kind = scanner.kind(buildCursor);
        int line = scanner.line(buildCursor);
        int column = scanner.column(buildCursor);
        int offset = scanner.offset(buildCursor);
        YamlNode node;
        switch (kind)
        {
        case YamlStreamScanner.START_OBJECT ->
        {
            YamlObjectNode object = new YamlObjectNode(line, column, offset);
            buildCursor++;
            while (scanner.kind(buildCursor) != YamlStreamScanner.END_OBJECT)
            {
                int keyLine = scanner.line(buildCursor);
                int keyColumn = scanner.column(buildCursor);
                int keyOffset = scanner.offset(buildCursor);
                String name = scanner.string(buildCursor);
                buildCursor++;
                object.add(new YamlEntry(name, buildNode(), keyLine, keyColumn, keyOffset));
            }
            buildCursor++;
            node = object;
        }
        case YamlStreamScanner.START_ARRAY ->
        {
            YamlArrayNode array = new YamlArrayNode(line, column, offset);
            buildCursor++;
            while (scanner.kind(buildCursor) != YamlStreamScanner.END_ARRAY)
            {
                array.add(buildNode());
            }
            buildCursor++;
            node = array;
        }
        case YamlStreamScanner.VALUE_NUMBER ->
        {
            node = YamlScalarNode.number(scanner.string(buildCursor), line, column, offset);
            buildCursor++;
        }
        case YamlStreamScanner.VALUE_TRUE ->
        {
            node = YamlScalarNode.literal(YamlScalarType.TRUE, line, column, offset);
            buildCursor++;
        }
        case YamlStreamScanner.VALUE_FALSE ->
        {
            node = YamlScalarNode.literal(YamlScalarType.FALSE, line, column, offset);
            buildCursor++;
        }
        case YamlStreamScanner.VALUE_NULL ->
        {
            node = YamlScalarNode.literal(YamlScalarType.NULL, line, column, offset);
            buildCursor++;
        }
        case YamlStreamScanner.ALIAS -> node = buildAlias();
        default ->
        {
            node = YamlScalarNode.string(scanner.string(buildCursor), line, column, offset);
            buildCursor++;
        }
        }
        return node;
    }

    private YamlNode root()
    {
        if (root == null)
        {
            root = YamlDocumentParser.parse(text, config).node;
        }
        return root;
    }

    private YamlNode rawRoot()
    {
        if (rawRoot == null)
        {
            Map<String, Object> raw = new HashMap<>(config.config());
            raw.put(YamlConfig.RESOLVE_REFERENCES, false);
            rawRoot = YamlDocumentParser.parse(text, new YamlConfiguration(raw)).node;
        }
        return rawRoot;
    }

    private void ensureStack()
    {
        if (stack == null)
        {
            stack = new ArrayDeque<>();
            stack.push(new Frame(rawRoot()));
        }
    }

    private YamlEvent eagerAdvance()
    {
        YamlEvent event = null;
        while (event == null && !stack.isEmpty())
        {
            Frame frame = stack.peek();
            if (frame.node.alias != null)
            {
                stack.pop();
                pendingNode = frame.node;
                pendingString = null;
                event = YamlEvent.ALIAS;
            }
            else if (frame.node instanceof YamlObjectNode object)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    pendingNode = object;
                    pendingString = null;
                    event = YamlEvent.START_OBJECT;
                }
                else if (frame.value)
                {
                    frame.value = false;
                    stack.push(new Frame(object.entries.get(frame.index++).value));
                }
                else if (frame.index < object.entries.size())
                {
                    YamlEntry entry = object.entries.get(frame.index);
                    frame.value = true;
                    pendingNode = entry.key != null ? entry.key :
                        YamlScalarNode.string(entry.name, entry.line, entry.column, entry.offset);
                    pendingString = entry.name;
                    event = YamlEvent.KEY_NAME;
                }
                else
                {
                    stack.pop();
                    pendingNode = null;
                    pendingString = null;
                    event = YamlEvent.END_OBJECT;
                }
            }
            else if (frame.node instanceof YamlArrayNode array)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    pendingNode = array;
                    pendingString = null;
                    event = YamlEvent.START_ARRAY;
                }
                else if (frame.index < array.values.size())
                {
                    stack.push(new Frame(array.values.get(frame.index++)));
                }
                else
                {
                    stack.pop();
                    pendingNode = null;
                    pendingString = null;
                    event = YamlEvent.END_ARRAY;
                }
            }
            else
            {
                stack.pop();
                event = eagerScalar((YamlScalarNode) frame.node);
            }
        }
        return event;
    }

    private YamlEvent eagerScalar(
        YamlScalarNode scalar)
    {
        pendingNode = scalar;
        pendingString = scalar.value;
        return switch (scalar.type)
        {
        case STRING -> YamlEvent.VALUE_STRING;
        case NUMBER -> YamlEvent.VALUE_NUMBER;
        case TRUE -> YamlEvent.VALUE_TRUE;
        case FALSE -> YamlEvent.VALUE_FALSE;
        case NULL -> YamlEvent.VALUE_NULL;
        };
    }

    private static String readAll(
        Reader reader)
    {
        try
        {
            char[] buffer = new char[1024];
            int length = 0;
            int read;
            while ((read = reader.read(buffer, length, buffer.length - length)) != -1)
            {
                length += read;
                if (length == buffer.length)
                {
                    buffer = Arrays.copyOf(buffer, buffer.length << 1);
                }
            }
            return new String(buffer, 0, length);
        }
        catch (IOException ex)
        {
            throw new YamlParseException(ex.getMessage(), new YamlLocation(1, 1, 0));
        }
    }

    private static String readAll(
        InputStream in,
        Charset charset)
    {
        try
        {
            return new String(in.readAllBytes(), charset);
        }
        catch (IOException ex)
        {
            throw new YamlParseException(ex.getMessage(), new YamlLocation(1, 1, 0));
        }
    }

    private static final class Frame
    {
        final YamlNode node;
        int index;
        boolean started;
        boolean value;

        private Frame(
            YamlNode node)
        {
            this.node = node;
        }
    }
}
