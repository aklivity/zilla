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
package io.aklivity.zilla.runtime.common.json.internal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Resumable {@link JsonTransform} that hoists each of a set of dotted accessor paths (e.g. {@code pr.title})
 * up to a target top-level key (e.g. {@code title}), discarding every ancestor wrapper container the accessor
 * passes through on the way — unlike {@link JsonProjectorImpl}, which preserves ancestor structure while
 * pruning siblings. Everything beneath a matched accessor (a scalar, or a whole object/array subtree) is
 * forwarded unchanged under the new key. Chain this downstream of a {@link JsonProjectorImpl} retaining the
 * same accessors' pointers so only the targeted paths ever reach this stage — every key this stage sees is
 * assumed to be part of some accessor's path, an ancestor of one, or (defensively) neither, in which case it
 * and its value are dropped rather than misrouted.
 * <p>
 * This stage always mediates its own {@link JsonController} downstream: it never opts into segmentable or
 * verbatim delivery, and it absorbs the same regardless of what its own downstream requests, because it must
 * see every ancestor key and container structurally to suppress or rename it — an opaque byte segment for a
 * matched subtree would hide the very keys this stage needs to rewrite.
 */
public final class JsonFlattenerImpl implements JsonTransform
{
    private static final int MAX_DEPTH = 64;

    private enum Mode
    {
        ANCESTOR, VERBATIM_SCALAR, VERBATIM_CONTAINER
    }

    private final Node root;
    private final Node[] frameNode = new Node[MAX_DEPTH];
    private final KeySource keySource = new KeySource();
    private final UpstreamControl upstreamControl = new UpstreamControl();

    private JsonController control;
    private Mode mode = Mode.ANCESTOR;
    private Node currentNode;
    private Node pendingChild;
    private boolean pendingIsTerminal;
    private CharSequence renamedKey;
    private int depth;
    private int verbatimContainerDepth;
    // True from the moment a renamed key's forward begins until that KEY_NAME event fully completes --
    // spanning however many resume() calls a bounded output takes, not just the initial transform() call.
    private boolean keyInFlight;
    // True once a key has been proven longer than every child at the current node (see onKey): the
    // remaining fragments are drained without inspection until the key completes.
    private boolean keySkipping;

    public JsonFlattenerImpl(
        Map<String, String> accessorTargets)
    {
        this.root = compile(accessorTargets);
    }

    @Override
    public void reset()
    {
        mode = Mode.ANCESTOR;
        currentNode = null;
        pendingChild = null;
        pendingIsTerminal = false;
        renamedKey = null;
        keyInFlight = false;
        keySkipping = false;
        depth = 0;
        verbatimContainerDepth = 0;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        return switch (mode)
        {
        case VERBATIM_SCALAR -> onVerbatimScalar(source, event, sink);
        case VERBATIM_CONTAINER -> onVerbatimContainer(source, event, sink);
        default -> onAncestor(source, event, sink);
        };
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        // keySource already holds the renamed key trimmed to whatever consumed() advanced it past on the
        // prior attempt -- re-initializing it here (as onKey's initial forward does) would reset that
        // offset and re-present the whole key, duplicating what was already written.
        JsonSource resumed = event == JsonEvent.KEY_NAME && keyInFlight
            ? keySource
            : source;
        Status status = sink.resume(upstreamControl, resumed, event);
        if (event == JsonEvent.KEY_NAME && status != Status.SUSPENDED)
        {
            keyInFlight = false;
        }
        return status;
    }

    private Status onAncestor(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_DOCUMENT:
        case END_DOCUMENT:
            status = sink.transform(upstreamControl, source, event);
            break;
        case KEY_NAME:
            status = onKey(source, sink);
            break;
        case START_OBJECT:
        case START_ARRAY:
            status = onValueStart(source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            status = onValueEnd(source, event, sink);
            break;
        default:
            status = onValueScalar(source, event, sink);
            break;
        }
        return status;
    }

    private Status onKey(
        JsonSource source,
        JsonSink sink)
    {
        Status status;
        CharSequence view = source.getStringView();
        boolean complete = !source.deferredBytes();
        int maxKeyLength = currentNode == null ? 0 : currentNode.maxKeyLength;
        if (keySkipping)
        {
            control.consumed(view.length());
            keySkipping = !complete;
            status = complete ? Status.ADVANCED : Status.STARVED;
        }
        else if (!complete && view.length() <= maxKeyLength)
        {
            // still short enough that some child could yet match once more of the key arrives; decline
            // the fragment (consumed(0)) so the source accumulates it whole and re-presents it complete
            // on a later window, then match it.
            control.consumed(0);
            status = Status.STARVED;
        }
        else if (!complete)
        {
            // already longer than the longest child at this node, so no child can match regardless of
            // what the rest of the key contains -- the same "unmatched dead branch" outcome onCompleteKey
            // reaches below, which never forwards the key either, so drop the remaining fragments without
            // ever buffering the key.
            pendingIsTerminal = false;
            pendingChild = null;
            control.consumed(view.length());
            keySkipping = true;
            status = Status.STARVED;
        }
        else
        {
            status = onCompleteKey(source, sink);
        }
        return status;
    }

    private Status onCompleteKey(
        JsonSource source,
        JsonSink sink)
    {
        Node child = currentNode == null ? null : currentNode.children.get(source.getStringView().toString());
        Status status;
        if (child != null && child.target != null)
        {
            pendingIsTerminal = true;
            pendingChild = null;
            renamedKey = child.target;
            keyInFlight = true;
            status = sink.transform(upstreamControl, keySource.with(renamedKey), JsonEvent.KEY_NAME);
            if (status != Status.SUSPENDED)
            {
                keyInFlight = false;
            }
        }
        else
        {
            // an ancestor to suppress-and-descend, or (defensively) an unmatched dead branch to drop —
            // either way the key itself is never forwarded
            pendingIsTerminal = false;
            pendingChild = child;
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onValueStart(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (depth == 0)
        {
            // the document root -- always forwarded so the output stays a well-formed object, and its
            // children are matched against the whole accessor trie
            status = sink.transform(upstreamControl, source, event);
            currentNode = root;
            depth++;
        }
        else if (pendingIsTerminal)
        {
            status = sink.transform(upstreamControl, source, event);
            pendingIsTerminal = false;
            mode = Mode.VERBATIM_CONTAINER;
            verbatimContainerDepth = 1;
        }
        else
        {
            frameNode[depth] = currentNode;
            currentNode = pendingChild;
            pendingChild = null;
            depth++;
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onValueEnd(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        depth--;
        Status status;
        if (depth == 0)
        {
            status = sink.transform(upstreamControl, source, event);
        }
        else
        {
            currentNode = frameNode[depth];
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onValueScalar(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (pendingIsTerminal)
        {
            pendingIsTerminal = false;
            status = sink.transform(upstreamControl, source, event);
            if (source.deferredBytes())
            {
                mode = Mode.VERBATIM_SCALAR;
            }
        }
        else
        {
            pendingChild = null;
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status onVerbatimScalar(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status = sink.transform(upstreamControl, source, event);
        if (!source.deferredBytes())
        {
            mode = Mode.ANCESTOR;
        }
        return status;
    }

    private Status onVerbatimContainer(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            verbatimContainerDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            verbatimContainerDepth--;
            break;
        default:
            break;
        }
        Status status = sink.transform(upstreamControl, source, event);
        if (verbatimContainerDepth == 0)
        {
            mode = Mode.ANCESTOR;
        }
        return status;
    }

    // Absorbs segmentable()/verbatim() unconditionally (via the JsonController defaults below) so the
    // upstream projector always delivers structured events -- an opaque byte segment for a matched subtree
    // would hide the ancestor keys this stage needs to suppress and the terminal key it needs to rename.
    // consumed() is absorbed only while forwarding a renamed key (keySource is a small, fully-materialized
    // substitute with no upstream cursor to advance); otherwise it relays, since forwarded scalars and
    // whole hoisted subtrees are the live upstream source and their consumed cursor must advance normally.
    private final class UpstreamControl implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            // a renamed key's consumed count is relative to keySource's own small, fully-materialized
            // substitute, not the live upstream token, so it advances keySource's own offset (the
            // "expose the remainder on resume" contract keySource implements locally) rather than the
            // real upstream's cursor -- otherwise the real upstream would misinterpret it as progress
            // against a token it never actually forwarded.
            if (keyInFlight)
            {
                keySource.advance(sourceBytes);
            }
            else
            {
                control.consumed(sourceBytes);
            }
        }
    }

    private static Node compile(
        Map<String, String> accessorTargets)
    {
        NodeBuilder builder = new NodeBuilder();
        for (Map.Entry<String, String> entry : accessorTargets.entrySet())
        {
            NodeBuilder node = builder;
            for (String segment : entry.getKey().split("\\."))
            {
                node = node.child(segment);
            }
            node.target = entry.getValue();
        }
        return builder.build();
    }

    private static final class Node
    {
        private final Map<String, Node> children;
        private final String target;
        private final int maxKeyLength;

        private Node(
            Map<String, Node> children,
            String target)
        {
            this.children = children;
            this.target = target;
            int longest = 0;
            for (String key : children.keySet())
            {
                longest = Math.max(longest, key.length());
            }
            this.maxKeyLength = longest;
        }
    }

    private static final class NodeBuilder
    {
        private final Map<String, NodeBuilder> children = new LinkedHashMap<>();
        private String target;

        private NodeBuilder child(
            String segment)
        {
            return children.computeIfAbsent(segment, ignored -> new NodeBuilder());
        }

        private Node build()
        {
            Map<String, Node> built = new LinkedHashMap<>();
            for (Map.Entry<String, NodeBuilder> entry : children.entrySet())
            {
                built.put(entry.getKey(), entry.getValue().build());
            }
            return new Node(built, target);
        }
    }

    // Exposes accessorTargets' small, fully-materialized target names as a JsonSource, trimmed by offset
    // so a resumed write sees only the remainder JsonSinkImpl.writeKeyName's control.consumed() report
    // didn't already write -- the local analog of a live parser's own cursor advance, since this key
    // never reaches the real upstream to advance its cursor instead.
    private static final class KeySource implements JsonSource
    {
        private CharSequence key;
        private int offset;

        private KeySource with(
            CharSequence key)
        {
            this.key = key;
            this.offset = 0;
            return this;
        }

        private void advance(
            int consumed)
        {
            offset += consumed;
        }

        @Override
        public String getString()
        {
            return key == null ? null : getStringView().toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return key == null ? null : key.subSequence(offset, key.length());
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }
}
