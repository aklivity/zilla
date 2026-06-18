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

/**
 * A pull parser over a full YAML 1.2 stream. {@link #next()} returns the next {@link YamlEvent} kind; the
 * current event's state (scalar value and type, node anchor / tag, alias name, source location) is read
 * through the accessor methods, the way {@code jakarta.json.stream.JsonParser} exposes its current event.
 *
 * <p>It is a thin streaming cursor over the {@link YamlStreamScanner}: the scanner classifies the whole
 * stream into a flat event buffer in one pass, and this parser frames that buffer into the YAML
 * representation graph — a stream wrapping one or more documents, each document a single balanced root node.
 * Documents are split depth-first: a scalar root is one event, a mapping or sequence root spans its balanced
 * {@code START} / {@code END} pair. The scanner rejects invalid input by failing {@link YamlStreamScanner#scan},
 * which this parser surfaces as a {@link YamlParseException} carrying the scanner's located bail message.
 *
 * <p>The JSON-restricting {@code YamlJsonParser} is layered on top of this stream — this parser itself does
 * not reject non-scalar keys, tags or other constructs that are valid YAML but not representable in JSON, and
 * it does not resolve aliases (it emits {@link YamlEvent#ALIAS} events verbatim).
 */
public final class YamlParser
{
    private enum Phase
    {
        STREAM_START,
        DOCUMENT_START,
        NODE,
        DOCUMENT_END,
        STREAM_END,
        DONE
    }

    private final String text;
    private final YamlStreamScanner scanner;
    private final int count;
    private final int documentCount;

    private Phase phase;
    private int index;
    private int document;
    private int depth;

    private YamlEvent event;
    private CharSequence value;
    private String view;
    private YamlScalarStyle style;
    private YamlScalarType scalarType;
    private String anchor;
    private String tag;
    private String alias;
    private boolean explicit;
    private YamlLocation location;

    public YamlParser(
        String text)
    {
        this.text = text;
        this.scanner = new YamlStreamScanner();
        if (!scanner.scan(text))
        {
            throw new YamlParseException(scanner.bailMessage(), scanner.bailLocation());
        }
        // a successful scan always classifies at least one event (an empty stream projects as a single null),
        // so there is always at least one document to frame
        this.count = scanner.count();
        this.documentCount = scanner.documentCount();
        this.phase = Phase.STREAM_START;
    }

    public boolean hasNext()
    {
        return phase != Phase.DONE;
    }

    public YamlEvent next()
    {
        clear();
        switch (phase)
        {
        case STREAM_START ->
        {
            event = YamlEvent.STREAM_START;
            location = locationAt(0);
            phase = Phase.DOCUMENT_START;
        }
        case DOCUMENT_START ->
        {
            event = YamlEvent.DOCUMENT_START;
            location = locationAt(scanner.offset(index));
            explicit = scanner.documentExplicitStart(document);
            depth = 0;
            phase = Phase.NODE;
        }
        case NODE ->
        {
            setScanEvent(index);
            byte kind = scanner.kind(index);
            if (kind == YamlStreamScanner.START_OBJECT || kind == YamlStreamScanner.START_ARRAY)
            {
                depth++;
            }
            else if (kind == YamlStreamScanner.END_OBJECT || kind == YamlStreamScanner.END_ARRAY)
            {
                depth--;
            }
            index++;
            if (depth == 0)
            {
                phase = Phase.DOCUMENT_END;
            }
        }
        case DOCUMENT_END ->
        {
            event = YamlEvent.DOCUMENT_END;
            int boundary = document < documentCount ? scanner.documentBoundary(document) : text.length();
            location = locationAt(boundary);
            explicit = scanner.documentExplicitEnd(document);
            document++;
            phase = index < count ? Phase.DOCUMENT_START : Phase.STREAM_END;
        }
        case STREAM_END ->
        {
            event = YamlEvent.STREAM_END;
            location = locationAt(text.length());
            phase = Phase.DONE;
        }
        default -> throw new IllegalStateException("No more events");
        }
        return event;
    }

    public CharSequence value()
    {
        return value;
    }

    /**
     * The raw, presentation-preserving source text of the current scalar event (see
     * {@link YamlStreamScanner#view}), or {@code null} when the current event is not a scalar.
     */
    public String view()
    {
        return view;
    }

    /**
     * The presentation style of the current scalar event, or {@code null} when the current event is not a
     * scalar.
     */
    public YamlScalarStyle style()
    {
        return style;
    }

    /**
     * Whether the current {@link YamlEvent#DOCUMENT_START} carried an explicit {@code ---} marker, or the
     * current {@link YamlEvent#DOCUMENT_END} an explicit {@code ...} marker. {@code false} for other events.
     */
    public boolean explicit()
    {
        return explicit;
    }

    public YamlScalarType scalarType()
    {
        return scalarType;
    }

    public String anchor()
    {
        return anchor;
    }

    public String tag()
    {
        return tag;
    }

    public String alias()
    {
        return alias;
    }

    public YamlLocation location()
    {
        return location;
    }

    private void clear()
    {
        event = null;
        value = null;
        view = null;
        style = null;
        scalarType = null;
        anchor = null;
        tag = null;
        alias = null;
        explicit = false;
        location = null;
    }

    private void setScanEvent(
        int at)
    {
        byte kind = scanner.kind(at);
        location = new YamlLocation(scanner.line(at), scanner.column(at), scanner.offset(at));
        switch (kind)
        {
        case YamlStreamScanner.START_OBJECT ->
        {
            event = YamlEvent.MAPPING_START;
            anchor = scanner.anchor(at);
            tag = scanner.tag(at);
        }
        case YamlStreamScanner.END_OBJECT -> event = YamlEvent.MAPPING_END;
        case YamlStreamScanner.START_ARRAY ->
        {
            event = YamlEvent.SEQUENCE_START;
            anchor = scanner.anchor(at);
            tag = scanner.tag(at);
        }
        case YamlStreamScanner.END_ARRAY -> event = YamlEvent.SEQUENCE_END;
        case YamlStreamScanner.KEY_NAME ->
        {
            event = YamlEvent.SCALAR;
            value = viewText(at);
            anchor = scanner.anchor(at);
            tag = scanner.tag(at);
        }
        case YamlStreamScanner.VALUE_STRING -> setScalar(at, viewText(at), YamlScalarType.STRING);
        case YamlStreamScanner.VALUE_NUMBER -> setScalar(at, viewText(at), YamlScalarType.NUMBER);
        case YamlStreamScanner.VALUE_TRUE -> setScalar(at, null, YamlScalarType.TRUE);
        case YamlStreamScanner.VALUE_FALSE -> setScalar(at, null, YamlScalarType.FALSE);
        case YamlStreamScanner.VALUE_NULL -> setScalar(at, null, YamlScalarType.NULL);
        case YamlStreamScanner.ALIAS ->
        {
            event = YamlEvent.ALIAS;
            alias = scanner.alias(at);
        }
        default -> throw new IllegalStateException("Unexpected scanner event kind: " + kind);
        }
        if (event == YamlEvent.SCALAR)
        {
            view = scanner.view(at);
            style = mapStyle(scanner.style(at));
        }
    }

    private static YamlScalarStyle mapStyle(
        byte style)
    {
        return switch (style)
        {
        case YamlStreamScanner.STYLE_SINGLE -> YamlScalarStyle.SINGLE;
        case YamlStreamScanner.STYLE_DOUBLE -> YamlScalarStyle.DOUBLE;
        case YamlStreamScanner.STYLE_LITERAL -> YamlScalarStyle.LITERAL;
        case YamlStreamScanner.STYLE_FOLDED -> YamlScalarStyle.FOLDED;
        default -> YamlScalarStyle.PLAIN;
        };
    }

    private void setScalar(
        int at,
        CharSequence value,
        YamlScalarType scalarType)
    {
        this.event = YamlEvent.SCALAR;
        this.value = value;
        this.scalarType = scalarType;
        this.anchor = scanner.anchor(at);
        this.tag = scanner.tag(at);
    }

    private String viewText(
        int at)
    {
        CharSequence view = scanner.stringView(at);
        return view != null ? view.toString() : "";
    }

    private YamlLocation locationAt(
        int offset)
    {
        int line = 1;
        int column = 1;
        int limit = Math.min(offset, text.length());
        for (int at = 0; at < limit; at++)
        {
            if (text.charAt(at) == '\n')
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
}
