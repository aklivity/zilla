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
 * A pull parser over a full YAML 1.2 stream, emitting {@link YamlEvent}s in the YAML representation model
 * (stream, documents, mappings, sequences, scalars, aliases). It is a facade over the conservative
 * {@link YamlStreamScanner} fast path: when the scanner accepts the input its token stream is projected to
 * events directly; otherwise the parser falls back to the eager {@link YamlDocumentParser} and walks the
 * resulting document tree. The eager parser remains the independent reference against which this parser's
 * scanner path is differentially tested.
 *
 * <p>The JSON-restricting {@code YamlJsonParser} is layered on top of this stream — this parser itself does
 * not reject non-scalar keys, tags or other constructs that are valid YAML but not representable in JSON.
 */
public final class YamlParser
{
    private final List<Step> steps;
    private final YamlEvent event;
    private int cursor;

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
        this.event = new YamlEvent();
        build(text, config);
    }

    public boolean hasNext()
    {
        return cursor < steps.size();
    }

    public YamlEvent next()
    {
        Step step = steps.get(cursor++);
        return event.reset(step.type, step.value, step.scalarType, step.anchor, step.tag, step.alias);
    }

    private void build(
        String text,
        YamlConfiguration config)
    {
        add(YamlEventType.STREAM_START, null, null, null, null, null);
        YamlStreamScanner scanner = new YamlStreamScanner();
        // the scanner fast path frames a single document; multi-document framing from the flat token stream
        // is deferred to the eager parser, which delineates each document's root node cleanly
        if (scanner.scan(text, true) && scanner.documentCount() <= 1)
        {
            buildFromScanner(scanner);
        }
        else
        {
            buildFromEager(text, config);
        }
        add(YamlEventType.STREAM_END, null, null, null, null, null);
    }

    private void buildFromScanner(
        YamlStreamScanner scanner)
    {
        int count = scanner.count();
        add(YamlEventType.DOCUMENT_START, null, null, null, null, null);
        for (int index = 0; index < count; index++)
        {
            addScanEvent(scanner, index);
        }
        add(YamlEventType.DOCUMENT_END, null, null, null, null, null);
    }

    private void addScanEvent(
        YamlStreamScanner scanner,
        int index)
    {
        byte kind = scanner.kind(index);
        String anchor = scanner.anchor(index);
        String tag = scanner.tag(index);
        switch (kind)
        {
        case YamlStreamScanner.START_OBJECT -> add(YamlEventType.MAPPING_START, null, null, anchor, tag, null);
        case YamlStreamScanner.END_OBJECT -> add(YamlEventType.MAPPING_END, null, null, null, null, null);
        case YamlStreamScanner.START_ARRAY -> add(YamlEventType.SEQUENCE_START, null, null, anchor, tag, null);
        case YamlStreamScanner.END_ARRAY -> add(YamlEventType.SEQUENCE_END, null, null, null, null, null);
        case YamlStreamScanner.KEY_NAME -> add(YamlEventType.SCALAR, viewText(scanner, index), null, anchor, tag, null);
        case YamlStreamScanner.VALUE_STRING ->
            add(YamlEventType.SCALAR, viewText(scanner, index), YamlScalarType.STRING, anchor, tag, null);
        case YamlStreamScanner.VALUE_NUMBER ->
            add(YamlEventType.SCALAR, viewText(scanner, index), YamlScalarType.NUMBER, anchor, tag, null);
        case YamlStreamScanner.VALUE_TRUE -> add(YamlEventType.SCALAR, null, YamlScalarType.TRUE, anchor, tag, null);
        case YamlStreamScanner.VALUE_FALSE -> add(YamlEventType.SCALAR, null, YamlScalarType.FALSE, anchor, tag, null);
        case YamlStreamScanner.VALUE_NULL -> add(YamlEventType.SCALAR, null, YamlScalarType.NULL, anchor, tag, null);
        case YamlStreamScanner.ALIAS -> add(YamlEventType.ALIAS, null, null, null, null, scanner.alias(index));
        default -> throw new IllegalStateException("Unexpected scanner event kind: " + kind);
        }
    }

    private void buildFromEager(
        String text,
        YamlConfiguration config)
    {
        for (YamlDocumentParser.Result result : YamlDocumentParser.parseAll(text, config))
        {
            add(YamlEventType.DOCUMENT_START, null, null, null, null, null);
            walk(result.node);
            add(YamlEventType.DOCUMENT_END, null, null, null, null, null);
        }
    }

    private void walk(
        YamlNode node)
    {
        if (node.alias != null)
        {
            add(YamlEventType.ALIAS, null, null, null, null, node.alias);
        }
        else if (node instanceof YamlObjectNode object)
        {
            add(YamlEventType.MAPPING_START, null, null, node.anchor, node.tag, null);
            for (YamlEntry entry : object.entries)
            {
                if (entry.name == null && entry.key != null &&
                    (!(entry.key instanceof YamlScalarNode) || entry.key.alias != null))
                {
                    walk(entry.key);
                }
                else if (entry.name != null)
                {
                    add(YamlEventType.SCALAR, entry.name, null, null, null, null);
                }
                else
                {
                    YamlScalarNode key = (YamlScalarNode) entry.key;
                    add(YamlEventType.SCALAR, key.value, null, key.anchor, key.tag, null);
                }
                walk(entry.value);
            }
            add(YamlEventType.MAPPING_END, null, null, null, null, null);
        }
        else if (node instanceof YamlArrayNode array)
        {
            add(YamlEventType.SEQUENCE_START, null, null, node.anchor, node.tag, null);
            for (YamlNode value : array.values)
            {
                walk(value);
            }
            add(YamlEventType.SEQUENCE_END, null, null, null, null, null);
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            add(YamlEventType.SCALAR, scalar.value, scalar.type, scalar.anchor, scalar.tag, null);
        }
    }

    private static String viewText(
        YamlStreamScanner scanner,
        int index)
    {
        CharSequence view = scanner.stringView(index);
        return view != null ? view.toString() : "";
    }

    private void add(
        YamlEventType type,
        CharSequence value,
        YamlScalarType scalarType,
        String anchor,
        String tag,
        String alias)
    {
        steps.add(new Step(type, value, scalarType, anchor, tag, alias));
    }

    private static final class Step
    {
        private final YamlEventType type;
        private final CharSequence value;
        private final YamlScalarType scalarType;
        private final String anchor;
        private final String tag;
        private final String alias;

        private Step(
            YamlEventType type,
            CharSequence value,
            YamlScalarType scalarType,
            String anchor,
            String tag,
            String alias)
        {
            this.type = type;
            this.value = value;
            this.scalarType = scalarType;
            this.anchor = anchor;
            this.tag = tag;
            this.alias = alias;
        }
    }
}
