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

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree-free, buffer-backed streaming scanner over a YAML document. A single forward pass classifies
 * lines into a compact event buffer (parallel arrays indexing into the source {@code text}) without
 * building the {@link YamlNode} tree, the intermediate {@code Line} objects, or the per-scalar
 * {@code String}s that the eager {@link YamlDocumentParser} allocates. Plain scalars stay as zero-copy
 * slices; only hex integers — which JSON renders in decimal — are materialized.
 * <p>
 * The scanner is deliberately conservative: it accepts only single-document block mappings, block
 * sequences and plain scalars (with comments, blank lines and JSON-style scalar typing). The moment it
 * encounters anything outside that subset — quoted or block scalars, flow collections, anchors, aliases,
 * merge keys, tags, explicit keys, document markers, directives or tabs — {@link #scan(String)} returns
 * {@code false} and the caller re-parses the whole document with the eager parser. Because of that
 * fallback the scanner only has to be correct for what it accepts; it never has to be complete.
 */
public final class YamlStreamScanner
{
    public static final byte START_OBJECT = 1;
    public static final byte END_OBJECT = 2;
    public static final byte START_ARRAY = 3;
    public static final byte END_ARRAY = 4;
    public static final byte KEY_NAME = 5;
    public static final byte VALUE_STRING = 6;
    public static final byte VALUE_NUMBER = 7;
    public static final byte VALUE_TRUE = 8;
    public static final byte VALUE_FALSE = 9;
    public static final byte VALUE_NULL = 10;
    public static final byte ALIAS = 11;

    private static final int INITIAL_EVENTS = 48;

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern HEX_INTEGER_PATTERN = Pattern.compile("-?0x[0-9a-fA-F]+");

    private static final Bail BAIL = new Bail();

    private Matcher numberMatcher;
    private Matcher hexMatcher;
    private CharSequenceView classifyView;
    private CharSequenceView resultView;

    private String text;

    // line model — one slot per source line, sized to the document on each scan
    private int[] lineStart;
    private int[] lineIndent;
    private int[] contentStart;
    private int[] contentEnd;
    private int lineCount;

    // event buffer — kind plus a slice [offset, offset + length) into text, or a materialized value
    private byte[] kinds;
    private int[] offsets;
    private int[] lengths;
    private String[] texts;
    // sparse per-event reference metadata, only populated in raw mode
    private String[] anchors;
    private String[] aliases;
    private String[] tags;
    private String pendingAnchor;
    private String pendingTag;
    private int eventCount;

    private boolean raw;
    private boolean references;
    private int cursor;
    private int flowAt;
    private int flowTokenStart;
    private int flowTokenEnd;

    public boolean scan(
        String text)
    {
        return scan(text, false);
    }

    public boolean scan(
        String text,
        boolean rawReferences)
    {
        this.text = text;
        this.eventCount = 0;
        this.cursor = 0;
        this.raw = rawReferences;
        this.references = false;
        this.pendingAnchor = null;
        this.pendingTag = null;

        boolean scanned;
        try
        {
            if (text.indexOf('\t') != -1)
            {
                throw BAIL;
            }
            int first = firstContent(text);
            if (first < text.length() && (text.charAt(first) == '{' || text.charAt(first) == '['))
            {
                splitLines(text);
                scanFlowDocument(first);
            }
            else
            {
                feasible(text);
                splitLines(text);
                scanRoot();
            }
            scanned = true;
        }
        catch (Bail ignore)
        {
            scanned = false;
        }
        return scanned;
    }

    public int count()
    {
        return eventCount;
    }

    public boolean hasReferences()
    {
        return references;
    }

    public byte kind(
        int index)
    {
        return kinds[index];
    }

    public int offset(
        int index)
    {
        return offsets[index];
    }

    public String anchor(
        int index)
    {
        return anchors == null ? null : anchors[index];
    }

    public String alias(
        int index)
    {
        return aliases == null ? null : aliases[index];
    }

    public String tag(
        int index)
    {
        return tags == null ? null : tags[index];
    }

    public int line(
        int index)
    {
        return lineOf(offsets[index]) + 1;
    }

    public int column(
        int index)
    {
        int at = offsets[index];
        return at - lineStart[lineOf(at)] + 1;
    }

    public CharSequence stringView(
        int index)
    {
        CharSequence view;
        if (resultView == null)
        {
            resultView = new CharSequenceView();
        }
        if (texts[index] != null)
        {
            view = resultView.wrap(texts[index], 0, texts[index].length());
        }
        else if (hasSlice(kinds[index]))
        {
            view = resultView.wrap(text, offsets[index], lengths[index]);
        }
        else
        {
            view = null;
        }
        return view;
    }

    public String string(
        int index)
    {
        String value = texts[index];
        if (value == null && hasSlice(kinds[index]))
        {
            value = text.substring(offsets[index], offsets[index] + lengths[index]);
        }
        return value;
    }

    private static boolean hasSlice(
        byte kind)
    {
        return kind == KEY_NAME || kind == VALUE_STRING || kind == VALUE_NUMBER;
    }

    private void scanRoot()
    {
        skipIgnorable();
        boolean directives = false;
        while (cursor < lineCount && text.charAt(contentStart[cursor]) == '%')
        {
            cursor++;
            skipIgnorable();
            directives = true;
        }
        boolean marker = cursor < lineCount && documentMarker(cursor, '-');
        if (marker)
        {
            cursor++;
            skipIgnorable();
        }
        else if (directives)
        {
            // a directives block must be terminated by a --- document-start marker
            throw BAIL;
        }
        if (cursor >= lineCount)
        {
            throw BAIL;
        }

        int line = cursor;
        char first = text.charAt(contentStart[line]);
        if (first == '{' || first == '[' || documentMarker(line, '-') || documentMarker(line, '.'))
        {
            throw BAIL;
        }

        int indent = lineIndent[line];
        if (isSequence(line, indent) || isExplicitKey(line) || mappingColon(contentStart[line], contentEnd[line]) != -1)
        {
            scanBlock(indent);
        }
        else if ((first == '|' || first == '>') && blockIndicator(contentStart[line], contentEnd[line]))
        {
            // a document that is a block scalar
            cursor++;
            scanBlockScalar(contentStart[line], contentEnd[line], indent, true);
        }
        else
        {
            // a document that is a single scalar; scanScalar bails on any non-scalar root
            cursor++;
            scanScalar(contentStart[line], contentEnd[line], indent, line, true, true);
        }

        skipIgnorable();
        if (cursor < lineCount && documentMarker(cursor, '.'))
        {
            cursor++;
            skipIgnorable();
        }
        if (cursor < lineCount)
        {
            throw BAIL;
        }
    }

    private boolean documentMarker(
        int line,
        char c)
    {
        int start = contentStart[line];
        int end = contentEnd[line];
        return end - start == 3 && text.charAt(start) == c && text.charAt(start + 1) == c && text.charAt(start + 2) == c;
    }

    private void scanBlock(
        int indent)
    {
        skipIgnorable();
        int line = cursor;
        if (lineIndent[line] != indent)
        {
            throw BAIL;
        }

        if (isSequence(line, indent))
        {
            scanSequence(indent);
        }
        else if (isExplicitKey(line) || mappingColon(contentStart[line], contentEnd[line]) != -1)
        {
            scanMapping(indent);
        }
        else
        {
            // a block that is a bare scalar value, possibly folded across lines (eager parseBlock ->
            // parsePlainLine); the document-root fold context applies at the value's own indent
            char first = text.charAt(contentStart[line]);
            if (first == '|' || first == '>')
            {
                throw BAIL;
            }
            cursor++;
            scanScalar(contentStart[line], contentEnd[line], indent, line, true, true);
        }
    }

    private void scanMapping(
        int indent)
    {
        int line = cursor;
        emit(START_OBJECT, contentStart[line], 0, null);
        scanMappingEntries(indent);
        emit(END_OBJECT, contentStart[line], 0, null);
    }

    private void scanMappingEntries(
        int indent)
    {
        while (true)
        {
            skipIgnorable();
            if (cursor >= lineCount)
            {
                break;
            }

            int line = cursor;
            if (lineIndent[line] != indent || isSequence(line, indent) ||
                documentMarker(line, '-') || documentMarker(line, '.'))
            {
                break;
            }
            if (isExplicitKey(line))
            {
                scanExplicitEntry(indent, line);
            }
            else
            {
                scanEntry(contentStart[line], contentEnd[line], indent, line);
            }
        }
    }

    private void scanExplicitEntry(
        int indent,
        int line)
    {
        int keyStart = skipSpace(contentStart[line] + 1, contentEnd[line]);
        int keyEnd = contentEnd[line];
        char keyFirst = keyStart < keyEnd ? text.charAt(keyStart) : 0;
        // only a simple single-line scalar key (plain or escape-free quoted) is supported
        if (keyStart == keyEnd || keyFirst == '{' || keyFirst == '[' || keyFirst == '|' || keyFirst == '>' ||
            keyFirst != '"' && keyFirst != '\'' && blockedStart(keyFirst) || mappingColon(keyStart, keyEnd) != -1)
        {
            throw BAIL;
        }
        cursor++;
        skipIgnorable();
        // a more-indented line before the value indicator is a key continuation, which is unsupported
        if (cursor < lineCount && lineIndent[cursor] > indent)
        {
            throw BAIL;
        }

        if (keyFirst == '"' || keyFirst == '\'')
        {
            validateQuoted(keyStart, keyEnd);
            emit(KEY_NAME, keyStart + 1, keyEnd - keyStart - 2, null);
        }
        else
        {
            emit(KEY_NAME, keyStart, keyEnd - keyStart, null);
        }

        boolean valued = cursor < lineCount && lineIndent[cursor] == indent &&
            contentStart[cursor] < contentEnd[cursor] && text.charAt(contentStart[cursor]) == ':' &&
            (contentEnd[cursor] == contentStart[cursor] + 1 || isSpace(text.charAt(contentStart[cursor] + 1)));
        if (valued)
        {
            int valueLine = cursor;
            int valueStart = skipSpace(contentStart[valueLine] + 1, contentEnd[valueLine]);
            int valueEnd = contentEnd[valueLine];
            cursor++;
            if (valueStart == valueEnd)
            {
                scanNestedValue(indent, valueLine);
            }
            else
            {
                scanScalar(valueStart, valueEnd, indent, valueLine, false, false);
            }
        }
        else
        {
            emit(VALUE_NULL, contentStart[line], 0, null);
        }
    }

    private void scanEntry(
        int start,
        int end,
        int indent,
        int line)
    {
        int colon = mappingColon(start, end);
        if (colon == -1)
        {
            throw BAIL;
        }

        int keyEnd = trimEnd(start, colon);
        char keyFirst = text.charAt(start);
        if (keyFirst == '"' || keyFirst == '\'')
        {
            validateQuoted(start, keyEnd);
            emit(KEY_NAME, start + 1, keyEnd - start - 2, null);
        }
        else if (keyEnd == start || isReservedStart(keyFirst) || isMergeKey(start, keyEnd) && !raw)
        {
            throw BAIL;
        }
        else
        {
            emit(KEY_NAME, start, keyEnd - start, null);
        }

        int valueStart = skipSpace(colon + 1, end);
        cursor++;
        if (valueStart == end)
        {
            scanNestedValue(indent, line);
        }
        else if (blockIndicator(valueStart, end))
        {
            scanBlockScalar(valueStart, end, indent, false);
        }
        else if (isCompactSequence(valueStart, end))
        {
            scanCompactSequence(valueStart, end, indent, line);
        }
        else
        {
            scanScalar(valueStart, end, indent, line, false, false);
        }
    }

    private void scanNestedValue(
        int indent,
        int line)
    {
        skipIgnorable();
        if (cursor < lineCount && lineIndent[cursor] > indent)
        {
            scanBlock(lineIndent[cursor]);
        }
        else if (cursor < lineCount && lineIndent[cursor] == indent && isSequence(cursor, indent))
        {
            scanSequence(indent);
        }
        else
        {
            emit(VALUE_NULL, contentStart[line], 0, null);
        }
    }

    private void scanSequence(
        int indent)
    {
        int line = cursor;
        emit(START_ARRAY, contentStart[line], 0, null);
        scanSequenceItems(indent);
        emit(END_ARRAY, contentStart[line], 0, null);
    }

    private void scanSequenceItems(
        int indent)
    {
        while (true)
        {
            skipIgnorable();
            if (cursor >= lineCount)
            {
                break;
            }

            int line = cursor;
            if (!isSequence(line, indent))
            {
                break;
            }

            int start = contentStart[line];
            int end = contentEnd[line];
            int itemAt = start + 1;
            while (itemAt < end && isSpace(text.charAt(itemAt)))
            {
                itemAt++;
            }

            if (itemAt == end)
            {
                cursor++;
                scanNestedSequenceValue(indent, line);
            }
            else if (isCompactSequence(itemAt, end))
            {
                cursor++;
                scanCompactSequence(itemAt, end, indent, line);
            }
            else if (mappingColon(itemAt, end) != -1)
            {
                scanSequenceItemMapping(indent, itemAt, end, line);
            }
            else if ((text.charAt(itemAt) == '|' || text.charAt(itemAt) == '>') && blockIndicator(itemAt, end))
            {
                cursor++;
                scanBlockScalar(itemAt, end, indent, true);
            }
            else
            {
                cursor++;
                scanScalar(itemAt, end, indent, line, false, true);
            }
        }
    }

    private void scanNestedSequenceValue(
        int indent,
        int line)
    {
        skipIgnorable();
        if (cursor < lineCount && lineIndent[cursor] > indent)
        {
            scanBlock(lineIndent[cursor]);
        }
        else
        {
            emit(VALUE_NULL, contentStart[line], 0, null);
        }
    }

    private void scanSequenceItemMapping(
        int indent,
        int start,
        int end,
        int line)
    {
        emit(START_OBJECT, start, 0, null);
        scanEntry(start, end, indent + 2, line);

        while (true)
        {
            skipIgnorable();
            if (cursor >= lineCount || lineIndent[cursor] <= indent)
            {
                break;
            }

            int next = cursor;
            if (isSequence(next, lineIndent[next]) || mappingColon(contentStart[next], contentEnd[next]) == -1)
            {
                throw BAIL;
            }
            scanMappingEntries(lineIndent[next]);
        }

        emit(END_OBJECT, start, 0, null);
    }

    /**
     * A compact sequence value ({@code key: - a} or a sequence item {@code - - a}) — mirrors
     * {@code YamlDocumentParser.parseCompactSequenceValue}. The single inline element opened by the {@code -}
     * is parsed in place (no cursor movement), then following sequence items indented at {@code indent + 2}
     * extend it. The caller has already advanced the cursor past the opening line.
     */
    private void scanCompactSequence(
        int seqStart,
        int end,
        int indent,
        int line)
    {
        emit(START_ARRAY, seqStart, 0, null);
        int elemStart = skipSpace(seqStart + 1, end);
        scanCompactNode(elemStart, end, indent + 2, line);
        scanSequenceItems(indent + 2);
        emit(END_ARRAY, seqStart, 0, null);
    }

    /**
     * The single node opened inline by a compact sequence {@code -} indicator: an empty item is null, a
     * further {@code -} nests another compact sequence (inline only — nested compacts have no line-level
     * continuation), and anything else is an inline scalar. A compact mapping node bails to the eager parser.
     */
    private void scanCompactNode(
        int elemStart,
        int end,
        int childIndent,
        int line)
    {
        if (elemStart == end)
        {
            emit(VALUE_NULL, elemStart, 0, null);
        }
        else if (isCompactSequence(elemStart, end))
        {
            emit(START_ARRAY, elemStart, 0, null);
            scanCompactNode(skipSpace(elemStart + 1, end), end, childIndent + 2, line);
            emit(END_ARRAY, elemStart, 0, null);
        }
        else if (mappingColon(elemStart, end) != -1)
        {
            throw BAIL;
        }
        else
        {
            scanInlineScalar(elemStart, end);
        }
    }

    /**
     * Emits a single-line scalar that occupies {@code [start, end)} with no folding or cursor movement —
     * used for compact sequence elements. Escape-free quoted scalars emit the verbatim slice, escaped ones
     * are materialized; flow collections, references, block scalars and other reserved starts bail.
     */
    private void scanInlineScalar(
        int start,
        int end)
    {
        char first = text.charAt(start);
        if (first == '"' || first == '\'')
        {
            if (quotedCloseEsc(start + 1, end, first) != end - 1)
            {
                throw BAIL;
            }
            String value = first == '"' ? unquoteDouble(start, end) : unquoteSingle(start, end);
            if (value == null)
            {
                emit(VALUE_STRING, start + 1, end - start - 2, null);
            }
            else
            {
                emit(VALUE_STRING, start, 0, value);
            }
        }
        else if (isReservedStart(first) || isCompactSequence(start, end) || mappingColon(start, end) != -1 ||
            isNonFinite(start, end))
        {
            throw BAIL;
        }
        else
        {
            emitClassifiedScalar(start, end);
        }
    }

    private void scanScalar(
        int start,
        int end,
        int refIndent,
        int line,
        boolean allowSameIndent,
        boolean allowIndentedSequence)
    {
        char first = text.charAt(start);
        if (raw && (first == '&' || first == '*' || first == '!'))
        {
            scanRef(start, end, refIndent, line);
            return;
        }
        if (first == '{' || first == '[')
        {
            scanFlowValue(start, refIndent);
            return;
        }
        if (first == '"' || first == '\'')
        {
            scanQuotedScalar(start, end, refIndent, line);
            return;
        }
        if (isReservedStart(first) || isCompactSequence(start, end) || mappingColon(start, end) != -1)
        {
            throw BAIL;
        }
        if (isNonFinite(start, end))
        {
            throw BAIL;
        }

        scanPlainScalar(start, end, refIndent, line, allowSameIndent, allowIndentedSequence);
    }

    /**
     * Folds a plain scalar that may continue onto more-indented (or, when {@code allowSameIndent}, equally
     * indented) following lines, mirroring {@code YamlDocumentParser.foldPlainScalar}: a single line break
     * between content folds to a space, a run of blank lines to that many line breaks. A folded multi-line
     * plain scalar always projects as a string. With no continuation the single-line classification path is
     * used unchanged. A continuation after a trailing comment bails so the eager parser reports the error.
     */
    private void scanPlainScalar(
        int start,
        int end,
        int refIndent,
        int line,
        boolean allowSameIndent,
        boolean allowIndentedSequence)
    {
        StringBuilder value = null;
        boolean commentTerminated = lineHasComment(line);
        while (cursor < lineCount)
        {
            int blankAt = cursor;
            int blankLines = 0;
            boolean blankComment = false;
            while (cursor < lineCount && contentStart[cursor] >= contentEnd[cursor])
            {
                blankComment |= lineHasComment(cursor);
                blankLines++;
                cursor++;
            }
            if (cursor >= lineCount || !plainContinuation(cursor, refIndent, allowSameIndent, allowIndentedSequence))
            {
                cursor = blankAt;
                break;
            }
            if (commentTerminated || blankComment)
            {
                throw BAIL;
            }
            if (value == null)
            {
                value = new StringBuilder(text.substring(start, end));
            }
            if (blankLines == 0)
            {
                value.append(' ');
            }
            else
            {
                appendLineBreaks(value, blankLines);
            }
            value.append(text, contentStart[cursor], contentEnd[cursor]);
            commentTerminated = lineHasComment(cursor);
            cursor++;
        }

        if (value == null)
        {
            foldGuard(refIndent);
            emitClassifiedScalar(start, end);
        }
        else
        {
            emit(VALUE_STRING, start, 0, value.toString());
        }
    }

    private boolean plainContinuation(
        int line,
        int indent,
        boolean allowSameIndent,
        boolean allowIndentedSequence)
    {
        boolean result;
        if (documentMarker(line, '-') || documentMarker(line, '.') || lineIndent[line] < indent)
        {
            result = false;
        }
        else if (lineIndent[line] == indent)
        {
            result = allowSameIndent && !isSequence(line, indent) && !isExplicitKey(line) &&
                mappingColon(contentStart[line], contentEnd[line]) == -1;
        }
        else if (!allowIndentedSequence && isSequence(line, lineIndent[line]))
        {
            result = false;
        }
        else
        {
            result = mappingColon(contentStart[line], contentEnd[line]) == -1;
        }
        return result;
    }

    private boolean lineHasComment(
        int line)
    {
        return commentIndex(contentStart[line], rawEnd(line)) != -1;
    }

    private boolean blockIndicator(
        int valueStart,
        int end)
    {
        boolean result = false;
        char style = text.charAt(valueStart);
        if (style == '|' || style == '>')
        {
            boolean ok = true;
            boolean chomp = false;
            boolean indent = false;
            int at = valueStart + 1;
            while (ok && at < end)
            {
                char c = text.charAt(at);
                if ((c == '-' || c == '+') && !chomp)
                {
                    chomp = true;
                    at++;
                }
                else if (c >= '1' && c <= '9' && !indent)
                {
                    indent = true;
                    at++;
                }
                else
                {
                    ok = false;
                }
            }
            result = ok;
        }
        return result;
    }

    private void scanBlockScalar(
        int valueStart,
        int end,
        int keyIndent,
        boolean allowSameIndent)
    {
        char style = text.charAt(valueStart);
        char chomp = 0;
        int explicitIndent = -1;
        for (int at = valueStart + 1; at < end; at++)
        {
            char c = text.charAt(at);
            if (c == '-' || c == '+')
            {
                chomp = c;
            }
            else if (c >= '1' && c <= '9')
            {
                explicitIndent = c - '0';
            }
        }
        int contentIndent = explicitIndent != -1 ? keyIndent + explicitIndent : blockScalarIndent(keyIndent, allowSameIndent);
        StringBuilder builder = new StringBuilder();
        boolean seenContent = false;
        boolean previousMoreIndented = false;
        boolean firstFolded = true;
        int blankLines = 0;
        while (cursor < lineCount)
        {
            int lineStartAt = lineStart[cursor];
            int lineEndAt = rawEnd(cursor);
            int indent = lineIndent[cursor];
            boolean spaceOnly = spaceOnlyLine(cursor);
            if (lineStartAt >= text.length())
            {
                break;
            }
            if (!spaceOnly && indent < contentIndent)
            {
                break;
            }
            if (!allowSameIndent && !spaceOnly && indent <= keyIndent)
            {
                break;
            }
            if (!seenContent && spaceOnly && lineEndAt > lineStartAt && indent > contentIndent)
            {
                throw BAIL;
            }

            int contentAt = lineStartAt + contentIndent;
            boolean empty = lineEndAt - lineStartAt <= contentIndent;
            if (style == '|')
            {
                if (!empty)
                {
                    builder.append(text, contentAt, lineEndAt);
                }
                builder.append('\n');
            }
            else if (empty)
            {
                blankLines++;
            }
            else
            {
                boolean moreIndented = !spaceOnly && indent > contentIndent;
                if (blankLines != 0)
                {
                    appendLineBreaks(builder, blankLines + (!firstFolded && (previousMoreIndented || moreIndented) ? 1 : 0));
                }
                else if (!firstFolded)
                {
                    builder.append(previousMoreIndented || moreIndented ? '\n' : ' ');
                }
                builder.append(text, contentAt, lineEndAt);
                previousMoreIndented = moreIndented;
                firstFolded = false;
                blankLines = 0;
            }
            seenContent |= !spaceOnly;
            cursor++;
        }
        if (style == '>')
        {
            appendLineBreaks(builder, blankLines);
        }

        String value = builder.toString();
        if (chomp == '-')
        {
            value = stripTrailingLineBreaks(value);
        }
        else if (chomp != '+')
        {
            value = clipTrailingLineBreaks(value);
        }
        emit(VALUE_STRING, valueStart, 0, value);
    }

    private static void appendLineBreaks(
        StringBuilder value,
        int count)
    {
        for (int at = 0; at < count; at++)
        {
            value.append('\n');
        }
    }

    private int blockScalarIndent(
        int keyIndent,
        boolean allowSameIndent)
    {
        int contentIndent = -1;
        for (int at = cursor; at < lineCount && contentIndent == -1; at++)
        {
            if (!spaceOnlyLine(at))
            {
                int indent = lineIndent[at];
                if (allowSameIndent || indent > keyIndent)
                {
                    contentIndent = indent;
                }
                else if (isSequence(at, indent) || isExplicitKey(at) ||
                    mappingColon(contentStart[at], contentEnd[at]) != -1 ||
                    isMarker(contentStart[at], contentEnd[at], '-') || isMarker(contentStart[at], contentEnd[at], '.'))
                {
                    contentIndent = keyIndent + 2;
                }
                else
                {
                    throw BAIL;
                }
            }
        }
        if (contentIndent == -1)
        {
            int blankIndent = -1;
            for (int at = cursor; at < lineCount && spaceOnlyLine(at); at++)
            {
                if (rawEnd(at) > lineStart[at])
                {
                    blankIndent = Math.max(blankIndent, lineIndent[at]);
                }
            }
            contentIndent = blankIndent != -1 ? blankIndent : keyIndent + 2;
        }
        return contentIndent;
    }

    private int rawEnd(
        int line)
    {
        int at = line + 1 < lineCount ? lineStart[line + 1] - 1 : text.length();
        if (at > lineStart[line] && text.charAt(at - 1) == '\r')
        {
            at--;
        }
        return at;
    }

    private boolean spaceOnlyLine(
        int line)
    {
        boolean spaceOnly = true;
        int at = lineStart[line];
        int lineEndAt = rawEnd(line);
        while (spaceOnly && at < lineEndAt)
        {
            spaceOnly = text.charAt(at) == ' ';
            at++;
        }
        return spaceOnly;
    }

    private static String stripTrailingLineBreaks(
        String value)
    {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '\n')
        {
            end--;
        }
        return value.substring(0, end);
    }

    private static String clipTrailingLineBreaks(
        String value)
    {
        String stripped = stripTrailingLineBreaks(value);
        return stripped.isEmpty() ? stripped : stripped + "\n";
    }

    /**
     * A node decorated with anchors/aliases ({@code &name}, {@code *name}), tokenized RAW for the
     * unresolved Parse layer (no dereferencing). Tags ({@code !}) and anchor+alias combinations bail.
     */
    private void scanRef(
        int start,
        int end,
        int refIndent,
        int line)
    {
        int at = start;
        boolean node = false;
        while (!node && at < end)
        {
            char c = text.charAt(at);
            if (c == '&')
            {
                int nameEnd = tokenEnd(at + 1, end);
                if (nameEnd == at + 1 || pendingAnchor != null)
                {
                    throw BAIL;
                }
                pendingAnchor = text.substring(at + 1, nameEnd);
                at = skipSpace(nameEnd, end);
            }
            else if (c == '*')
            {
                int nameEnd = tokenEnd(at + 1, end);
                if (nameEnd == at + 1 || pendingAnchor != null || pendingTag != null || skipSpace(nameEnd, end) != end)
                {
                    throw BAIL;
                }
                emitAlias(at + 1, text.substring(at + 1, nameEnd));
                foldGuard(refIndent);
                at = end;
            }
            else if (c == '!')
            {
                int tagEnd = tagEnd(at, end);
                if (pendingTag != null)
                {
                    throw BAIL;
                }
                pendingTag = normalizeTag(at, tagEnd);
                at = skipSpace(tagEnd, end);
            }
            else
            {
                node = true;
            }
        }

        if (node)
        {
            char r = text.charAt(at);
            if (r == '&' || r == '*' || r == '!')
            {
                throw BAIL;
            }
            scanScalar(at, end, refIndent, line, false, false);
        }
        else if (pendingAnchor != null || pendingTag != null)
        {
            scanNestedValue(refIndent, line);
        }
    }

    private void foldGuard(
        int refIndent)
    {
        skipIgnorable();
        if (cursor < lineCount && lineIndent[cursor] > refIndent)
        {
            throw BAIL;
        }
    }

    private int tokenEnd(
        int start,
        int end)
    {
        int at = start;
        while (at < end && !Character.isWhitespace(text.charAt(at)) &&
            text.charAt(at) != ',' && text.charAt(at) != ']' && text.charAt(at) != '}')
        {
            at++;
        }
        return at;
    }

    private int tagEnd(
        int start,
        int end)
    {
        int at;
        if (start + 1 < end && text.charAt(start + 1) == '<')
        {
            int close = start + 2;
            while (close < end && text.charAt(close) != '>')
            {
                close++;
            }
            at = close < end ? close + 1 : end;
        }
        else if (start + 1 < end && text.charAt(start + 1) == '!')
        {
            at = tokenEnd(start + 2, end);
        }
        else
        {
            at = tokenEnd(start + 1, end);
        }
        return at;
    }

    /**
     * Mirrors {@code YamlDocumentParser.normalizeTag} with the default tag handles ({@code !!} ->
     * {@code tag:yaml.org,2002:}); bails on malformed tags or unknown handles, since {@code %TAG}
     * directives are rejected before the scan.
     */
    private String normalizeTag(
        int start,
        int end)
    {
        String normalized;
        if (end - start == 1)
        {
            normalized = "!";
        }
        else if (text.charAt(start + 1) == '<' && text.charAt(end - 1) == '>')
        {
            normalized = text.substring(start + 2, end - 1);
        }
        else if (text.charAt(start + 1) == '!')
        {
            normalized = "tag:yaml.org,2002:" + text.substring(start + 2, end);
        }
        else
        {
            boolean malformed = false;
            for (int at = start + 1; at < end; at++)
            {
                char c = text.charAt(at);
                if (c == '!' || c == '{' || c == '}')
                {
                    malformed = true;
                    break;
                }
            }
            if (malformed)
            {
                throw BAIL;
            }
            normalized = text.substring(start, end);
        }
        return normalized;
    }

    /**
     * A flow collection used as a block mapping value or sequence item, restricted to a single line —
     * the flow must open and close within {@code [start, end)} (the current line's content). Multi-line
     * nested flow bails so the block line cursor never has to resync across the flow region.
     */
    private void scanFlowValue(
        int start,
        int refIndent)
    {
        int startLine = lineOf(start);
        flowAt = start;
        flowValue();
        // the flow collection may span lines; resync the block line cursor past its closing line
        int closeLine = lineOf(flowAt - 1);
        if (flowAt < contentEnd[closeLine])
        {
            throw BAIL;
        }
        // continuation lines of a flow nested in a block must be indented past the block
        for (int at = startLine + 1; at <= closeLine; at++)
        {
            if (lineIndent[at] <= refIndent)
            {
                throw BAIL;
            }
        }
        cursor = closeLine + 1;

        skipIgnorable();
        if (cursor < lineCount && lineIndent[cursor] > refIndent)
        {
            throw BAIL;
        }
    }

    private void emitClassifiedScalar(
        int start,
        int end)
    {
        char first = text.charAt(start);
        byte kind;
        String materialized = null;
        if (equalsIgnoreCase(start, end, "true"))
        {
            kind = VALUE_TRUE;
        }
        else if (equalsIgnoreCase(start, end, "false"))
        {
            kind = VALUE_FALSE;
        }
        else if (equalsIgnoreCase(start, end, "null") || end - start == 1 && first == '~')
        {
            kind = VALUE_NULL;
        }
        else if (hexMatcher().reset(classifyView(start, end)).matches())
        {
            kind = VALUE_NUMBER;
            materialized = numberText(start, end);
        }
        else if (numberMatcher().reset(classifyView(start, end)).matches())
        {
            kind = VALUE_NUMBER;
        }
        else
        {
            kind = VALUE_STRING;
        }

        emit(kind, start, end - start, materialized);
    }

    /**
     * Walks a single-document flow collection ({@code {...}} or {@code [...]}) over the raw text from
     * {@code first}, treating line breaks and spaces as separators so multi-line JSON-style documents
     * parse in one pass. Restricted to the JSON-shaped subset: nested flow mappings and sequences,
     * escape-free single-line quoted scalars, and JSON-typed bare scalars. Decorators ({@code & * !}),
     * tags, comments, explicit keys, merge keys, implicit-null entries and multi-line scalars bail.
     */
    private void scanFlowDocument(
        int first)
    {
        flowAt = first;
        flowValue();
        flowSkipWhitespace();
        if (flowAt != text.length())
        {
            throw BAIL;
        }
    }

    private void flowValue()
    {
        flowSkipWhitespace();
        if (flowAt >= text.length())
        {
            throw BAIL;
        }

        char c = text.charAt(flowAt);
        switch (c)
        {
        case '{' -> flowObject();
        case '[' -> flowArray();
        case '"', '\'' -> flowQuotedValue();
        default ->
        {
            if (raw && c == '*')
            {
                flowAlias();
            }
            else if (flowReserved(c))
            {
                throw BAIL;
            }
            else
            {
                flowBareScalar();
            }
        }
        }
    }

    private void flowAlias()
    {
        int start = flowAt + 1;
        int at = start;
        while (at < text.length())
        {
            char c = text.charAt(at);
            if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c))
            {
                break;
            }
            at++;
        }
        if (at == start)
        {
            throw BAIL;
        }
        emitAlias(start, text.substring(start, at));
        flowAt = at;
    }

    private void flowObject()
    {
        emit(START_OBJECT, flowAt, 0, null);
        flowAt++;
        flowSkipWhitespace();
        if (flowConsume('}'))
        {
            emit(END_OBJECT, flowAt - 1, 0, null);
        }
        else
        {
            boolean closed = false;
            while (!closed)
            {
                flowKey();
                flowSkipWhitespace();
                if (flowAt >= text.length() || text.charAt(flowAt) != ':')
                {
                    throw BAIL;
                }
                flowAt++;
                flowSkipWhitespace();
                char c = flowAt < text.length() ? text.charAt(flowAt) : 0;
                if (c == ',' || c == '}')
                {
                    throw BAIL;
                }
                flowValue();
                flowSkipWhitespace();
                if (flowConsume(','))
                {
                    flowSkipWhitespace();
                    closed = flowConsume('}');
                }
                else if (flowConsume('}'))
                {
                    closed = true;
                }
                else
                {
                    throw BAIL;
                }
            }
            emit(END_OBJECT, flowAt - 1, 0, null);
        }
    }

    private void flowArray()
    {
        emit(START_ARRAY, flowAt, 0, null);
        flowAt++;
        flowSkipWhitespace();
        if (flowConsume(']'))
        {
            emit(END_ARRAY, flowAt - 1, 0, null);
        }
        else
        {
            boolean closed = false;
            while (!closed)
            {
                flowValue();
                flowSkipWhitespace();
                if (flowConsume(','))
                {
                    flowSkipWhitespace();
                    closed = flowConsume(']');
                }
                else if (flowConsume(']'))
                {
                    closed = true;
                }
                else
                {
                    throw BAIL;
                }
            }
            emit(END_ARRAY, flowAt - 1, 0, null);
        }
    }

    private void flowKey()
    {
        flowSkipWhitespace();
        if (flowAt >= text.length())
        {
            throw BAIL;
        }

        char c = text.charAt(flowAt);
        if (c == '"' || c == '\'')
        {
            flowReadQuoted();
            emit(KEY_NAME, flowTokenStart, flowTokenEnd - flowTokenStart, null);
        }
        else if (flowReserved(c) || c == '{' || c == '[')
        {
            throw BAIL;
        }
        else
        {
            int start = flowAt;
            while (flowAt < text.length())
            {
                char ch = text.charAt(flowAt);
                if (ch == ',' || ch == '}' || ch == ':' && flowMappingColon(flowAt))
                {
                    break;
                }
                if (isLineBreak(ch))
                {
                    throw BAIL;
                }
                flowAt++;
            }
            int end = trimEnd(start, flowAt);
            if (end == start || isMergeKey(start, end))
            {
                throw BAIL;
            }
            emit(KEY_NAME, start, end - start, null);
        }
    }

    private void flowBareScalar()
    {
        int start = flowAt;
        while (flowAt < text.length())
        {
            char c = text.charAt(flowAt);
            if (c == ',' || c == ']' || c == '}')
            {
                break;
            }
            if (c == '#' && isSpace(text.charAt(flowAt - 1)))
            {
                break;
            }
            if (c == ':' && flowMappingColon(flowAt))
            {
                break;
            }
            if (isLineBreak(c))
            {
                throw BAIL;
            }
            flowAt++;
        }

        int end = trimEnd(start, flowAt);
        if (end == start || isFlowScalarMarker(start, end) || isNonFinite(start, end))
        {
            throw BAIL;
        }
        emitClassifiedScalar(start, end);
    }

    private void flowQuotedValue()
    {
        flowReadQuoted();
        emit(VALUE_STRING, flowTokenStart, flowTokenEnd - flowTokenStart, null);
    }

    private void flowReadQuoted()
    {
        char quote = text.charAt(flowAt);
        int inner = flowAt + 1;
        int i = inner;
        boolean closed = false;
        while (i < text.length() && !closed)
        {
            char c = text.charAt(i);
            if (isLineBreak(c))
            {
                throw BAIL;
            }
            if (quote == '"' && c == '\\')
            {
                throw BAIL;
            }
            if (c == quote)
            {
                if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'')
                {
                    throw BAIL;
                }
                closed = true;
            }
            else
            {
                i++;
            }
        }
        if (!closed)
        {
            throw BAIL;
        }
        flowTokenStart = inner;
        flowTokenEnd = i;
        flowAt = i + 1;
    }

    private boolean isFlowScalarMarker(
        int start,
        int end)
    {
        return end - start == 1 && text.charAt(start) == '-' ||
            isMarker(start, end, '-') || isMarker(start, end, '.');
    }

    private boolean flowMappingColon(
        int index)
    {
        char next = index + 1 >= text.length() ? 0 : text.charAt(index + 1);
        return index + 1 >= text.length() || Character.isWhitespace(next) ||
            next == ',' || next == ']' || next == '}';
    }

    private boolean flowConsume(
        char expected)
    {
        boolean match = flowAt < text.length() && text.charAt(flowAt) == expected;
        if (match)
        {
            flowAt++;
        }
        return match;
    }

    private void flowSkipWhitespace()
    {
        boolean done = false;
        while (flowAt < text.length() && !done)
        {
            char c = text.charAt(flowAt);
            if (Character.isWhitespace(c))
            {
                flowAt++;
            }
            else if (c == '#' && (flowAt == 0 || Character.isWhitespace(text.charAt(flowAt - 1))))
            {
                throw BAIL;
            }
            else
            {
                done = true;
            }
        }
    }

    private static boolean flowReserved(
        char c)
    {
        return c == ',' || c == ']' || c == '}' || c == '&' || c == '*' ||
            c == '!' || c == '?' || c == '#' || c == '@' || c == '`' || c == '%';
    }

    private static boolean isLineBreak(
        char c)
    {
        return c == '\n' || c == '\r' || c == '\f' || c == '\u000b' ||
            c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    private Matcher numberMatcher()
    {
        if (numberMatcher == null)
        {
            numberMatcher = NUMBER_PATTERN.matcher("");
        }
        return numberMatcher;
    }

    private Matcher hexMatcher()
    {
        if (hexMatcher == null)
        {
            hexMatcher = HEX_INTEGER_PATTERN.matcher("");
        }
        return hexMatcher;
    }

    private CharSequence classifyView(
        int start,
        int end)
    {
        if (classifyView == null)
        {
            classifyView = new CharSequenceView();
        }
        return classifyView.wrap(text, start, end - start);
    }

    private void scanQuotedScalar(
        int start,
        int end,
        int refIndent,
        int line)
    {
        char quote = text.charAt(start);
        int close = quotedCloseEsc(start + 1, end, quote);
        if (close == -1)
        {
            scanMultiLineQuoted(start, end, refIndent, quote);
        }
        else
        {
            if (close != end - 1)
            {
                throw BAIL;
            }
            skipIgnorable();
            if (cursor < lineCount && lineIndent[cursor] > refIndent)
            {
                throw BAIL;
            }
            String value = quote == '"' ? unquoteDouble(start, end) : unquoteSingle(start, end);
            if (value == null)
            {
                emit(VALUE_STRING, start + 1, end - start - 2, null);
            }
            else
            {
                emit(VALUE_STRING, start, 0, value);
            }
        }
    }

    /**
     * Collects an escape-free quoted scalar that spans physical lines, requiring each continuation to be
     * indented past {@code refIndent} (mirroring the eager parser, which errors on same-or-less indent),
     * folds the interior the way {@code YamlDocumentParser.foldQuotedLines} does, and emits the materialized
     * value. A document marker, a wrong-indented continuation or anything after the closing quote bails so
     * the eager parser can produce the authoritative result (or error).
     */
    private void scanMultiLineQuoted(
        int openStart,
        int openEnd,
        int refIndent,
        char quote)
    {
        // multi-line folding is only supported escape-free; an escape on the opening line bails
        for (int i = openStart + 1; i < openEnd; i++)
        {
            char c = text.charAt(i);
            if (quote == '"' && c == '\\' ||
                quote == '\'' && c == '\'' && i + 1 < openEnd && text.charAt(i + 1) == '\'')
            {
                throw BAIL;
            }
        }

        StringBuilder interior = new StringBuilder();
        interior.append(text, openStart + 1, openEnd);

        boolean closed = false;
        while (!closed)
        {
            if (cursor >= lineCount || documentMarker(cursor, '-') || documentMarker(cursor, '.'))
            {
                throw BAIL;
            }

            int cs = contentStart[cursor];
            int ce = contentEnd[cursor];
            interior.append('\n');
            if (cs < ce)
            {
                if (lineIndent[cursor] <= refIndent)
                {
                    throw BAIL;
                }
                int close = quotedClose(cs, ce, quote);
                if (close == -1)
                {
                    interior.append(text, cs, ce);
                }
                else if (close == ce - 1)
                {
                    interior.append(text, cs, close);
                    closed = true;
                }
                else
                {
                    throw BAIL;
                }
            }
            cursor++;
        }

        String value = foldQuoted(interior.toString());

        skipIgnorable();
        if (cursor < lineCount && lineIndent[cursor] > refIndent)
        {
            throw BAIL;
        }

        emit(VALUE_STRING, openStart, 0, value);
    }

    /**
     * Returns the index of the closing quote within {@code [from, end)}, or {@code -1} when an escape-free
     * quote does not close on this line. Bails on a {@code \} inside a double quote or a {@code ''} pair
     * inside a single quote, since those would need materializing rather than emitting the verbatim slice.
     */
    private int quotedClose(
        int from,
        int end,
        char quote)
    {
        int close = -1;
        for (int i = from; i < end && close == -1; i++)
        {
            char c = text.charAt(i);
            if (quote == '"' && c == '\\')
            {
                throw BAIL;
            }
            else if (c == quote)
            {
                if (quote == '\'' && i + 1 < end && text.charAt(i + 1) == '\'')
                {
                    throw BAIL;
                }
                close = i;
            }
        }
        return close;
    }

    /**
     * Returns the index of the closing quote within {@code [from, end)} honoring escapes — a {@code \X}
     * inside a double quote and a {@code ''} pair inside a single quote do not close — or {@code -1} when
     * the quote does not close on this line. Unlike {@link #quotedClose} this never bails; the caller
     * materializes the value via {@link #unquoteDouble} / {@link #unquoteSingle} when escapes are present.
     */
    private int quotedCloseEsc(
        int from,
        int end,
        char quote)
    {
        int close = -1;
        for (int i = from; i < end && close == -1; i++)
        {
            char c = text.charAt(i);
            if (quote == '"' && c == '\\')
            {
                i++;
            }
            else if (c == quote)
            {
                if (quote == '\'' && i + 1 < end && text.charAt(i + 1) == '\'')
                {
                    i++;
                }
                else
                {
                    close = i;
                }
            }
        }
        return close;
    }

    /**
     * Materializes a single-line double-quoted scalar's interior {@code [start+1, end-1)}, returning
     * {@code null} when it is escape-free so the caller can emit the verbatim slice instead. Mirrors
     * {@code YamlDocumentParser.unquoteDouble}; an unterminated escape bails to the eager parser.
     */
    private String unquoteDouble(
        int start,
        int end)
    {
        boolean escaped = false;
        for (int i = start + 1; i < end - 1 && !escaped; i++)
        {
            escaped = text.charAt(i) == '\\';
        }
        String result = null;
        if (escaped)
        {
            StringBuilder value = new StringBuilder();
            for (int i = start + 1; i < end - 1; i++)
            {
                char c = text.charAt(i);
                if (c == '\\')
                {
                    i++;
                    if (i >= end - 1)
                    {
                        throw BAIL;
                    }
                    i = appendEscape(value, i);
                }
                else
                {
                    value.append(c);
                }
            }
            result = value.toString();
        }
        return result;
    }

    /**
     * Materializes a single-line single-quoted scalar's interior, collapsing each {@code ''} pair to a
     * single quote, or {@code null} when no pair is present so the verbatim slice can be emitted. Mirrors
     * {@code YamlDocumentParser.unquoteSingle}; a lone interior quote cannot occur since it would have
     * closed the scalar.
     */
    private String unquoteSingle(
        int start,
        int end)
    {
        boolean doubled = false;
        for (int i = start + 1; i < end - 1 && !doubled; i++)
        {
            doubled = text.charAt(i) == '\'';
        }
        String result = null;
        if (doubled)
        {
            StringBuilder value = new StringBuilder();
            for (int i = start + 1; i < end - 1; i++)
            {
                char c = text.charAt(i);
                value.append(c);
                if (c == '\'')
                {
                    i++;
                }
            }
            result = value.toString();
        }
        return result;
    }

    private int appendEscape(
        StringBuilder value,
        int at)
    {
        int next = at;
        char escaped = text.charAt(at);
        switch (escaped)
        {
        case '0' -> value.append('\0');
        case 'a' -> value.append('\u0007');
        case '"' -> value.append('"');
        case '\\' -> value.append('\\');
        case '/' -> value.append('/');
        case 'b' -> value.append('\b');
        case 'e' -> value.append('\u001b');
        case 'f' -> value.append('\f');
        case 'n' -> value.append('\n');
        case 'r' -> value.append('\r');
        case 't', '\t' -> value.append('\t');
        case 'v' -> value.append('\u000b');
        case ' ' -> value.append(' ');
        case '_' -> value.append('\u00a0');
        case 'N' -> value.append('\u0085');
        case 'L' -> value.append('\u2028');
        case 'P' -> value.append('\u2029');
        case 'x' -> next = appendHexEscape(value, at, 2);
        case 'u' -> next = appendHexEscape(value, at, 4);
        case 'U' -> next = appendHexEscape(value, at, 8);
        default -> throw BAIL;
        }
        return next;
    }

    private int appendHexEscape(
        StringBuilder value,
        int at,
        int digits)
    {
        if (at + digits >= text.length())
        {
            throw BAIL;
        }
        int next;
        try
        {
            int codePoint = Integer.parseUnsignedInt(text.substring(at + 1, at + 1 + digits), 16);
            value.appendCodePoint(codePoint);
            next = at + digits;
        }
        catch (IllegalArgumentException ex)
        {
            throw BAIL;
        }
        return next;
    }

    private static String foldQuoted(
        String value)
    {
        StringBuilder folded = new StringBuilder();
        String[] lines = value.split("\\R", -1);
        folded.append(stripTrailingSpace(lines[0]));
        int blankLines = 0;
        for (int i = 1; i < lines.length; i++)
        {
            boolean last = i == lines.length - 1;
            String line = stripLeadingSpace(lines[i]);
            if (line.isEmpty())
            {
                if (last && blankLines == 0)
                {
                    folded.append(' ');
                }
                else if (last)
                {
                    appendLineBreaks(folded, blankLines);
                }
                else
                {
                    blankLines++;
                }
            }
            else
            {
                boolean separated = blankLines > 0;
                if (separated)
                {
                    appendLineBreaks(folded, blankLines);
                    blankLines = 0;
                }
                String normalized = last ? line : stripTrailingSpace(line);
                if (folded.length() > 0 && folded.charAt(folded.length() - 1) == '\\')
                {
                    folded.setLength(folded.length() - 1);
                }
                else if (!separated)
                {
                    folded.append(' ');
                }
                folded.append(normalized);
            }
        }
        return folded.toString();
    }

    private static String stripLeadingSpace(
        String value)
    {
        int start = 0;
        while (start < value.length() && isSpace(value.charAt(start)))
        {
            start++;
        }
        return value.substring(start);
    }

    private static String stripTrailingSpace(
        String value)
    {
        int end = value.length();
        while (end > 0 && isSpace(value.charAt(end - 1)))
        {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * Accepts only an escape-free, single-line quoted scalar whose value is the verbatim interior —
     * a double quote with no {@code \} or interior {@code "}, or a single quote with no interior
     * {@code '}. Anything with escapes or a {@code ''} pair (which the eager {@code unquote} would
     * transform) bails so the value is never misrepresented and never needs materializing here.
     */
    private void validateQuoted(
        int start,
        int end)
    {
        char quote = text.charAt(start);
        if (end - start < 2 || text.charAt(end - 1) != quote)
        {
            throw BAIL;
        }
        for (int i = start + 1; i < end - 1; i++)
        {
            char c = text.charAt(i);
            if (c == quote || quote == '"' && c == '\\')
            {
                throw BAIL;
            }
        }
    }

    private String numberText(
        int start,
        int end)
    {
        boolean negative = text.charAt(start) == '-';
        int digits = negative ? start + 1 : start;
        String value = new BigInteger(text.substring(digits + 2, end), 16).toString();
        return negative ? "-" + value : value;
    }

    private boolean isNonFinite(
        int start,
        int end)
    {
        return equalsIgnoreCase(start, end, ".nan") ||
            equalsIgnoreCase(start, end, ".inf") ||
            equalsIgnoreCase(start, end, "+.inf") ||
            equalsIgnoreCase(start, end, "-.inf");
    }

    private boolean isSequence(
        int line,
        int indent)
    {
        int start = contentStart[line];
        int end = contentEnd[line];
        return lineIndent[line] == indent && start < end && text.charAt(start) == '-' &&
            (end - start == 1 || isSpace(text.charAt(start + 1)));
    }

    private boolean isExplicitKey(
        int line)
    {
        int start = contentStart[line];
        int end = contentEnd[line];
        return start < end && text.charAt(start) == '?' &&
            (end - start == 1 || isSpace(text.charAt(start + 1)));
    }

    private boolean isCompactSequence(
        int start,
        int end)
    {
        return end - start > 1 && text.charAt(start) == '-' && isSpace(text.charAt(start + 1));
    }

    private boolean isMergeKey(
        int start,
        int end)
    {
        return end - start == 2 && text.charAt(start) == '<' && text.charAt(start + 1) == '<';
    }

    private int mappingColon(
        int start,
        int end)
    {
        boolean single = false;
        boolean doub = false;
        boolean escaped = false;
        int depth = 0;
        int found = -1;

        for (int i = start; i < end && found == -1; i++)
        {
            char c = text.charAt(i);
            if (doub)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    doub = false;
                }
            }
            else if (single)
            {
                if (c == '\'')
                {
                    if (i + 1 < end && text.charAt(i + 1) == '\'')
                    {
                        i++;
                    }
                    else
                    {
                        single = false;
                    }
                }
            }
            else
            {
                switch (c)
                {
                case '\'' ->
                {
                    if (isQuotedTokenStart(start, i))
                    {
                        single = true;
                    }
                }
                case '"' ->
                {
                    if (isQuotedTokenStart(start, i))
                    {
                        doub = true;
                    }
                }
                case '{', '[' -> depth++;
                case '}', ']' ->
                {
                    if (depth > 0)
                    {
                        depth--;
                    }
                }
                case ':' ->
                {
                    if (depth == 0 && (i + 1 == end || isSpace(text.charAt(i + 1))) && !isAnchorOrAliasColon(start, i))
                    {
                        found = i;
                    }
                }
                default ->
                {
                    // continue
                }
                }
            }
        }

        return found;
    }

    private boolean isAnchorOrAliasColon(
        int start,
        int colon)
    {
        int at = colon - 1;
        while (at >= start && !isSpace(text.charAt(at)))
        {
            at--;
        }
        at++;
        return at < colon && (text.charAt(at) == '&' || text.charAt(at) == '*');
    }

    private boolean isQuotedTokenStart(
        int start,
        int index)
    {
        char previous = index == start ? 0 : text.charAt(index - 1);
        return index == start || isSpace(previous) ||
            previous == '[' || previous == '{' || previous == ',' || previous == ':';
    }

    private static boolean isReservedStart(
        char c)
    {
        return c == '"' || c == '\'' || c == '[' || c == ']' || c == '{' || c == '}' ||
            c == '|' || c == '>' || c == '&' || c == '*' || c == '!' || c == '?' ||
            c == '#' || c == '%' || c == '@' || c == '`' || c == ',';
    }

    private boolean equalsIgnoreCase(
        int start,
        int end,
        String literal)
    {
        boolean match = end - start == literal.length();
        for (int i = 0; match && i < literal.length(); i++)
        {
            match = Character.toLowerCase(text.charAt(start + i)) == literal.charAt(i);
        }
        return match;
    }

    private int trimEnd(
        int start,
        int end)
    {
        int at = end;
        while (at > start && isSpace(text.charAt(at - 1)))
        {
            at--;
        }
        return at;
    }

    private int skipSpace(
        int start,
        int end)
    {
        int at = start;
        while (at < end && isSpace(text.charAt(at)))
        {
            at++;
        }
        return at;
    }

    private void skipIgnorable()
    {
        while (cursor < lineCount && contentStart[cursor] == contentEnd[cursor])
        {
            cursor++;
        }
    }

    private int lineOf(
        int offset)
    {
        int low = 0;
        int high = lineCount - 1;
        while (low < high)
        {
            int mid = low + high + 1 >>> 1;
            if (lineStart[mid] <= offset)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }
        return low;
    }

    private void emit(
        byte kind,
        int offset,
        int length,
        String materialized)
    {
        ensureEventCapacity();
        kinds[eventCount] = kind;
        offsets[eventCount] = offset;
        lengths[eventCount] = length;
        texts[eventCount] = materialized;
        if (raw)
        {
            anchors[eventCount] = pendingAnchor;
            aliases[eventCount] = null;
            tags[eventCount] = pendingTag;
            references |= pendingAnchor != null || pendingTag != null;
            pendingAnchor = null;
            pendingTag = null;
        }
        eventCount++;
    }

    private void emitAlias(
        int offset,
        String alias)
    {
        ensureEventCapacity();
        kinds[eventCount] = ALIAS;
        offsets[eventCount] = offset;
        lengths[eventCount] = 0;
        texts[eventCount] = null;
        anchors[eventCount] = pendingAnchor;
        aliases[eventCount] = alias;
        tags[eventCount] = pendingTag;
        references = true;
        pendingAnchor = null;
        pendingTag = null;
        eventCount++;
    }

    private void ensureEventCapacity()
    {
        if (kinds == null)
        {
            kinds = new byte[INITIAL_EVENTS];
            offsets = new int[INITIAL_EVENTS];
            lengths = new int[INITIAL_EVENTS];
            texts = new String[INITIAL_EVENTS];
            if (raw)
            {
                anchors = new String[INITIAL_EVENTS];
                aliases = new String[INITIAL_EVENTS];
                tags = new String[INITIAL_EVENTS];
            }
        }
        else if (eventCount == kinds.length)
        {
            int size = kinds.length << 1;
            kinds = copyOf(kinds, size);
            offsets = copyOf(offsets, size);
            lengths = copyOf(lengths, size);
            texts = copyOf(texts, size);
            if (raw)
            {
                anchors = copyOf(anchors, size);
                aliases = copyOf(aliases, size);
                tags = copyOf(tags, size);
            }
        }
    }

    private static int firstContent(
        String text)
    {
        int at = 0;
        while (at < text.length() && Character.isWhitespace(text.charAt(at)))
        {
            at++;
        }
        return at;
    }

    /**
     * Allocation-free gate run before any array is allocated. It walks each line computing its content
     * bounds inline (no line model, no event buffer) and rejects the cheap, common out-of-subset cases —
     * quoted/block scalars, flow collections, anchors, aliases, merge keys, tags, explicit keys, document
     * markers and directives — by applying the same per-line token checks {@link #scanScalar} and
     * {@link #scanEntry} apply. A line that passes here may still be rejected by the structural scan
     * (indentation, multi-line folding), but the expensive fallback documents bail here for free.
     */
    private void feasible(
        String text)
    {
        int start = 0;
        int length = text.length();
        int blockSkipIndent = -1;
        while (start < length)
        {
            int eol = text.indexOf('\n', start);
            int lineRawEnd = eol == -1 ? length : eol;
            int trimmedEnd = lineRawEnd > start && text.charAt(lineRawEnd - 1) == '\r' ? lineRawEnd - 1 : lineRawEnd;

            int cs = start;
            while (cs < trimmedEnd && isSpace(text.charAt(cs)))
            {
                cs++;
            }
            int indent = cs - start;
            boolean spaceOnly = cs == trimmedEnd;

            boolean skip = false;
            if (blockSkipIndent >= 0)
            {
                if (spaceOnly || indent > blockSkipIndent)
                {
                    skip = true;
                }
                else
                {
                    blockSkipIndent = -1;
                }
            }

            if (!skip)
            {
                int comment = commentIndex(cs, trimmedEnd);
                int ce = comment == -1 ? trimmedEnd : comment;
                while (ce > cs && isSpace(text.charAt(ce - 1)))
                {
                    ce--;
                }
                if (cs < ce)
                {
                    blockSkipIndent = feasibleLine(cs, ce, indent);
                }
            }

            start = eol == -1 ? length : eol + 1;
        }
    }

    private int feasibleLine(
        int start,
        int end,
        int indent)
    {
        int blockIndent = -1;
        char first = text.charAt(start);
        if (isMarker(start, end, '-') || isMarker(start, end, '.'))
        {
            // a bare document marker (--- or ...) is framing; inline marker content is not yet supported
            if (end - start != 3)
            {
                throw BAIL;
            }
        }
        else if (first == '%')
        {
            // %YAML and unknown directives are ignorable; %TAG changes tag handles, so bail
            if (text.startsWith("%TAG", start))
            {
                throw BAIL;
            }
        }
        else if (first == ':' && (end - start == 1 || isSpace(text.charAt(start + 1))))
        {
            // an explicit-key value indicator line; defer to the structural scan
            blockIndent = -1;
        }
        else if (first == '-' && (end - start == 1 || isSpace(text.charAt(start + 1))))
        {
            int item = start + 1;
            while (item < end && isSpace(text.charAt(item)))
            {
                item++;
            }
            if (item < end)
            {
                char it = text.charAt(item);
                if (it != '{' && it != '[' && !(raw && (it == '&' || it == '*' || it == '!')) &&
                    !isCompactSequence(item, end))
                {
                    int colon = mappingColon(item, end);
                    if (colon != -1)
                    {
                        blockIndent = feasibleEntry(item, end, colon, indent);
                    }
                    else if ((it == '|' || it == '>') && blockIndicator(item, end))
                    {
                        blockIndent = indent;
                    }
                    else if (blockedStart(it))
                    {
                        throw BAIL;
                    }
                }
            }
        }
        else
        {
            // a no-colon line may be an explicit key, a root scalar (or invalid); defer to the structural scan
            int colon = mappingColon(start, end);
            if (colon != -1)
            {
                blockIndent = feasibleEntry(start, end, colon, indent);
            }
        }
        return blockIndent;
    }

    private int feasibleEntry(
        int start,
        int end,
        int colon,
        int indent)
    {
        int keyEnd = trimEnd(start, colon);
        if (keyEnd == start || blockedStart(text.charAt(start)) || isMergeKey(start, keyEnd) && !raw)
        {
            throw BAIL;
        }

        int blockIndent = -1;
        int valueStart = skipSpace(colon + 1, end);
        if (valueStart < end)
        {
            char value = text.charAt(valueStart);
            if (blockIndicator(valueStart, end))
            {
                blockIndent = indent;
            }
            else if (value != '{' && value != '[' && !(raw && (value == '&' || value == '*' || value == '!')) &&
                !isCompactSequence(valueStart, end) &&
                (blockedStart(value) || mappingColon(valueStart, end) != -1))
            {
                throw BAIL;
            }
        }
        return blockIndent;
    }

    private static boolean blockedStart(
        char c)
    {
        return isReservedStart(c) && c != '"' && c != '\'';
    }

    private boolean isMarker(
        int start,
        int end,
        char c)
    {
        int length = end - start;
        return length >= 3 && text.charAt(start) == c && text.charAt(start + 1) == c && text.charAt(start + 2) == c &&
            (length == 3 || isSpace(text.charAt(start + 3)));
    }

    private void splitLines(
        String text)
    {
        int count = 1;
        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == '\n')
            {
                count++;
            }
        }

        lineStart = new int[count];
        lineIndent = new int[count];
        contentStart = new int[count];
        contentEnd = new int[count];
        lineCount = count;

        int start = 0;
        for (int line = 0; line < count; line++)
        {
            int eol = text.indexOf('\n', start);
            int rawEnd = eol == -1 ? text.length() : eol;
            int trimmedEnd = rawEnd > start && text.charAt(rawEnd - 1) == '\r' ? rawEnd - 1 : rawEnd;

            int indent = start;
            while (indent < trimmedEnd && isSpace(text.charAt(indent)))
            {
                indent++;
            }

            int comment = commentIndex(indent, trimmedEnd);
            int end = comment == -1 ? trimmedEnd : comment;
            while (end > indent && isSpace(text.charAt(end - 1)))
            {
                end--;
            }

            lineStart[line] = start;
            lineIndent[line] = indent - start;
            contentStart[line] = indent;
            contentEnd[line] = end;

            start = eol == -1 ? text.length() : eol + 1;
        }
    }

    private int commentIndex(
        int start,
        int end)
    {
        boolean single = false;
        boolean doub = false;
        boolean escaped = false;
        int found = -1;

        for (int i = start; i < end && found == -1; i++)
        {
            char c = text.charAt(i);
            if (doub)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    doub = false;
                }
            }
            else if (single)
            {
                if (c == '\'')
                {
                    if (i + 1 < end && text.charAt(i + 1) == '\'')
                    {
                        i++;
                    }
                    else
                    {
                        single = false;
                    }
                }
            }
            else if (c == '\'')
            {
                single = true;
            }
            else if (c == '"')
            {
                doub = true;
            }
            else if (c == '#' && (i == start || Character.isWhitespace(text.charAt(i - 1))))
            {
                found = i;
            }
        }

        return found;
    }

    private static boolean isSpace(
        char c)
    {
        return c == ' ' || c == '\t';
    }

    private static byte[] copyOf(
        byte[] source,
        int size)
    {
        byte[] target = new byte[size];
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    private static int[] copyOf(
        int[] source,
        int size)
    {
        int[] target = new int[size];
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    private static String[] copyOf(
        String[] source,
        int size)
    {
        String[] target = new String[size];
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    private static final class Bail extends RuntimeException
    {
        private Bail()
        {
            super(null, null, false, false);
        }
    }
}
