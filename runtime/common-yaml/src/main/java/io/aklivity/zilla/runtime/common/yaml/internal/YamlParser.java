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

import java.util.ArrayList;
import java.util.List;

/**
 * A pull parser over a full YAML 1.2 stream. {@link #next()} returns the next {@link YamlEvent} kind; the
 * current event's state (scalar value and type, node anchor / tag, alias name, source location) is read
 * through the accessor methods, the way {@code jakarta.json.stream.JsonParser} exposes its current event.
 *
 * <p>It is a facade over the conservative {@link YamlStreamScanner} fast path: when the scanner accepts the
 * input its token stream is projected to events directly; otherwise the parser falls back to the eager
 * {@link YamlDocumentParser} and walks the resulting document tree. The eager parser remains the independent
 * reference against which this parser's scanner path is differentially tested.
 *
 * <p>The JSON-restricting {@code YamlJsonParser} is layered on top of this stream — this parser itself does
 * not reject non-scalar keys, tags or other constructs that are valid YAML but not representable in JSON, and
 * it does not resolve aliases (it emits {@link YamlEvent#ALIAS} events verbatim).
 */
public final class YamlParser
{
    private final List<Step> steps;
    private int cursor;
    private Step current;

    public YamlParser(
        String text)
    {
        this(text, YamlConfiguration.DEFAULT);
    }

    public YamlParser(
        String text,
        YamlConfiguration config)
    {
        this.steps = new ArrayList<>();
        build(text, config);
    }

    public boolean hasNext()
    {
        return cursor < steps.size();
    }

    public YamlEvent next()
    {
        current = steps.get(cursor++);
        return current.event;
    }

    public CharSequence value()
    {
        return current.value;
    }

    public YamlScalarType scalarType()
    {
        return current.scalarType;
    }

    public String anchor()
    {
        return current.anchor;
    }

    public String tag()
    {
        return current.tag;
    }

    public String alias()
    {
        return current.alias;
    }

    public YamlLocation location()
    {
        return current.location;
    }

    private void build(
        String text,
        YamlConfiguration config)
    {
        add(YamlEvent.STREAM_START, null, null, null, null, null, locationAt(text, 0));
        YamlStreamScanner scanner = new YamlStreamScanner();
        // the scanner fast path frames a single document; multi-document framing from the flat token stream
        // is deferred to the eager parser, which delineates each document's root node cleanly
        if (scanner.scan(text) && scanner.documentCount() <= 1)
        {
            buildFromScanner(scanner, text);
        }
        else
        {
            buildFromEager(text, config);
        }
        add(YamlEvent.STREAM_END, null, null, null, null, null, locationAt(text, text.length()));
    }

    private void buildFromScanner(
        YamlStreamScanner scanner,
        String text)
    {
        int count = scanner.count();
        add(YamlEvent.DOCUMENT_START, null, null, null, null, null, locationAt(text, 0));
        for (int index = 0; index < count; index++)
        {
            addScanEvent(scanner, index);
        }
        // the document's boundary is the end of the stream; the JSON layer reports the root-terminal event here
        add(YamlEvent.DOCUMENT_END, null, null, null, null, null, locationAt(text, text.length()));
    }

    private void addScanEvent(
        YamlStreamScanner scanner,
        int index)
    {
        byte kind = scanner.kind(index);
        String anchor = scanner.anchor(index);
        String tag = scanner.tag(index);
        YamlLocation location = new YamlLocation(scanner.line(index), scanner.column(index), scanner.offset(index));
        switch (kind)
        {
        case YamlStreamScanner.START_OBJECT -> add(YamlEvent.MAPPING_START, null, null, anchor, tag, null, location);
        case YamlStreamScanner.END_OBJECT -> add(YamlEvent.MAPPING_END, null, null, null, null, null, location);
        case YamlStreamScanner.START_ARRAY -> add(YamlEvent.SEQUENCE_START, null, null, anchor, tag, null, location);
        case YamlStreamScanner.END_ARRAY -> add(YamlEvent.SEQUENCE_END, null, null, null, null, null, location);
        case YamlStreamScanner.KEY_NAME -> add(YamlEvent.SCALAR, viewText(scanner, index), null, anchor, tag, null, location);
        case YamlStreamScanner.VALUE_STRING ->
            add(YamlEvent.SCALAR, viewText(scanner, index), YamlScalarType.STRING, anchor, tag, null, location);
        case YamlStreamScanner.VALUE_NUMBER ->
            add(YamlEvent.SCALAR, viewText(scanner, index), YamlScalarType.NUMBER, anchor, tag, null, location);
        case YamlStreamScanner.VALUE_TRUE -> add(YamlEvent.SCALAR, null, YamlScalarType.TRUE, anchor, tag, null, location);
        case YamlStreamScanner.VALUE_FALSE -> add(YamlEvent.SCALAR, null, YamlScalarType.FALSE, anchor, tag, null, location);
        case YamlStreamScanner.VALUE_NULL -> add(YamlEvent.SCALAR, null, YamlScalarType.NULL, anchor, tag, null, location);
        case YamlStreamScanner.ALIAS ->
            add(YamlEvent.ALIAS, null, null, null, null, scanner.alias(index), location);
        default -> throw new IllegalStateException("Unexpected scanner event kind: " + kind);
        }
    }

    private void buildFromEager(
        String text,
        YamlConfiguration config)
    {
        int offset = 0;
        do
        {
            String remaining = text.substring(offset);
            YamlDocumentParser.Result result = YamlDocumentParser.parse(remaining, config);
            int read = (int) result.end.offset();
            int boundary = read <= 0 ? text.length() : offset + read;
            add(YamlEvent.DOCUMENT_START, null, null, null, null, null, locationAt(text, offset));
            walk(result.node, offset);
            add(YamlEvent.DOCUMENT_END, null, null, null, null, null, locationAt(text, boundary));
            if (read <= 0)
            {
                break;
            }
            offset = boundary;
        }
        while (offset < text.length() && !text.substring(offset).isBlank());
    }

    private void walk(
        YamlNode node,
        long base)
    {
        YamlLocation location = new YamlLocation(node.line, node.column, base + node.offset);
        if (node.alias != null)
        {
            add(YamlEvent.ALIAS, null, null, null, null, node.alias, location);
        }
        else if (node instanceof YamlObjectNode object)
        {
            add(YamlEvent.MAPPING_START, null, null, node.anchor, node.tag, null, location);
            for (YamlEntry entry : object.entries)
            {
                if (entry.name == null && entry.key != null &&
                    (!(entry.key instanceof YamlScalarNode) || entry.key.alias != null))
                {
                    walk(entry.key, base);
                }
                else if (entry.name != null)
                {
                    add(YamlEvent.SCALAR, entry.name, null, null, null, null,
                        new YamlLocation(entry.line, entry.column, base + entry.offset));
                }
                else
                {
                    YamlScalarNode key = (YamlScalarNode) entry.key;
                    add(YamlEvent.SCALAR, key.value, null, key.anchor, key.tag, null,
                        new YamlLocation(key.line, key.column, base + key.offset));
                }
                walk(entry.value, base);
            }
            add(YamlEvent.MAPPING_END, null, null, null, null, null, location);
        }
        else if (node instanceof YamlArrayNode array)
        {
            add(YamlEvent.SEQUENCE_START, null, null, node.anchor, node.tag, null, location);
            for (YamlNode value : array.values)
            {
                walk(value, base);
            }
            add(YamlEvent.SEQUENCE_END, null, null, null, null, null, location);
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            add(YamlEvent.SCALAR, scalar.value, scalar.type, scalar.anchor, scalar.tag, null, location);
        }
    }

    private static YamlLocation locationAt(
        String text,
        int offset)
    {
        int line = 1;
        int column = 1;
        int limit = Math.min(offset, text.length());
        for (int index = 0; index < limit; index++)
        {
            if (text.charAt(index) == '\n')
            {
                line++;
                column = 1;
            }
            else
            {
                column++;
            }
        }
        return new YamlLocation(line, column, offset);
    }

    private static String viewText(
        YamlStreamScanner scanner,
        int index)
    {
        CharSequence view = scanner.stringView(index);
        return view != null ? view.toString() : "";
    }

    private void add(
        YamlEvent event,
        CharSequence value,
        YamlScalarType scalarType,
        String anchor,
        String tag,
        String alias,
        YamlLocation location)
    {
        steps.add(new Step(event, value, scalarType, anchor, tag, alias, location));
    }

    private static final class Step
    {
        private final YamlEvent event;
        private final CharSequence value;
        private final YamlScalarType scalarType;
        private final String anchor;
        private final String tag;
        private final String alias;
        private final YamlLocation location;

        private Step(
            YamlEvent event,
            CharSequence value,
            YamlScalarType scalarType,
            String anchor,
            String tag,
            String alias,
            YamlLocation location)
        {
            this.event = event;
            this.value = value;
            this.scalarType = scalarType;
            this.anchor = anchor;
            this.tag = tag;
            this.alias = alias;
            this.location = location;
        }
    }
}
