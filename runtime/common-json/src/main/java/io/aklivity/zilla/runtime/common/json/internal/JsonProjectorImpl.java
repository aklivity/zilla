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
import java.util.List;
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
 * Resumable, event-driven {@link JsonTransform} that projects a document down to a set of retained
 * RFC 6901 pointers, forwarding the kept events to the downstream {@code sink} passed into each
 * {@link #transform(JsonController, JsonSource, JsonEvent, JsonSink)}. This class holds the per-value descent
 * state only; the downstream is bound once at assembly and supplied per event.
 * <p>
 * The retained pointer set is compiled once into a {@link Node trie}: each node has children keyed by
 * segment plus a {@code keepAll} terminal flag (a pointer ends here, so the whole subtree is retained).
 * Descent tracks a per-depth stack of node references ({@code frameNode}) rather than the absolute path,
 * so matching a key is an {@code O(children)} lookup of the live {@link JsonSource#getStringView()} in the
 * current node's children — no ancestor key chain is ever retained and no absolute pointer is manifested.
 */
public final class JsonProjectorImpl implements JsonTransform
{
    private static final int MAX_DEPTH = 64;
    private static final String WILDCARD = "-";

    private enum Decision
    {
        KEEP_ALL, DESCEND, SKIP
    }

    private final Node root;

    private final boolean[] frameInArray = new boolean[MAX_DEPTH];
    private final boolean[] frameEmit = new boolean[MAX_DEPTH];
    private final boolean[] frameKeepAll = new boolean[MAX_DEPTH];
    private final int[] frameNextIndex = new int[MAX_DEPTH];
    // The trie node whose children are matched against the entries of this container — the node reference
    // is the position in the path, replacing the retained ancestor key chain.
    private final Node[] frameNode = new Node[MAX_DEPTH];

    // Only a DESCEND key is deferred, and only as far as its value's first event: its value may be a
    // container (forward the key) or a scalar under a deeper-only pointer (drop the key), so the key cannot
    // be forwarded until the value's kind is seen. A KEEP_ALL key is forwarded live at onKey and never
    // buffered. At most one key is ever pending — the value that follows consumes it before the next key.
    private final StringBuilder pendingKeyBuffer = new StringBuilder();

    private final KeySource keySource = new KeySource();

    private final DownstreamControl downstreamControl = new DownstreamControl();

    private JsonController upstreamControl;
    private int containers;
    private Decision keyDecision;
    private Node keyNode;
    private Node valueNode;
    private CharSequence pendingKey;
    private boolean rootDone;
    private boolean downstreamDemand;
    private Status downstream;
    private SegMode segMode = SegMode.NONE;
    private JsonEvent deferredStart;
    private boolean scalarPending;
    private boolean scalarEmit;
    // true while a buffered key is being forwarded downstream: the parser delivered that key live and already
    // moved on, so the sink's consumed() pushback for it must be absorbed rather than relayed to the parser's
    // now-current (value) char cursor
    private boolean forwardingKey;

    private enum SegMode
    {
        NONE, AWAITING, FORWARDING
    }

    public JsonProjectorImpl(
        List<String> pointers)
    {
        this.root = compile(pointers);
    }

    @Override
    public void reset()
    {
        containers = 0;
        keyDecision = null;
        keyNode = null;
        valueNode = null;
        pendingKey = null;
        rootDone = false;
        downstreamDemand = false;
        segMode = SegMode.NONE;
        deferredStart = null;
        scalarPending = false;
        scalarEmit = false;
        forwardingKey = false;
    }

    private void onDownstreamSegmentable()
    {
        downstreamDemand = true;
    }

    // Relays the sink's consumed() pushback to the projector's own upstream, the same way it relays
    // segmentable(); the upstream control is captured per feed/resume.
    private final class DownstreamControl implements JsonController
    {
        @Override
        public void segmentable()
        {
            onDownstreamSegmentable();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            // a buffered key's pushback is absorbed (the parser already delivered that key live); a value's
            // pushback relays to the parser so it re-exposes the value remainder on resume
            if (!forwardingKey)
            {
                upstreamControl.consumed(sourceBytes);
            }
        }
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        downstream = Status.ADVANCED;
        if (segMode == SegMode.AWAITING)
        {
            onAwaiting(control, source, event, sink);
        }
        else if (segMode == SegMode.FORWARDING)
        {
            onForwarding(source, event, sink);
        }
        else
        {
            route(control, source, event, sink);
        }
        Status status;
        if (downstream == Status.REJECTED)
        {
            status = Status.REJECTED;
        }
        else if (downstream == Status.SUSPENDED)
        {
            status = Status.SUSPENDED;
        }
        else if (downstream == Status.STARVED)
        {
            // onKey declined an in-flight key fragment directly (no forward(), so rank() never sees it):
            // the pump must wait for more input rather than treat this event as advanced.
            status = Status.STARVED;
        }
        else if (rootDone)
        {
            status = Status.COMPLETED;
        }
        else
        {
            status = Status.ADVANCED;
        }
        return status;
    }

    // Forwards one event downstream, retaining the most terminal status seen across the (possibly
    // several) downstream feeds a single upstream event triggers, so backpressure (SUSPENDED) and
    // rejection (REJECTED) propagate while the value is still in progress.
    private void forward(
        JsonSink sink,
        JsonSource source,
        JsonEvent event)
    {
        Status status = sink.transform(downstreamControl, source, event);
        if (rank(status) > rank(downstream))
        {
            downstream = status;
        }
    }

    private static int rank(
        Status status)
    {
        return switch (status)
        {
        case REJECTED -> 3;
        case SUSPENDED -> 2;
        case COMPLETED -> 1;
        default -> 0;
        };
    }

    private void route(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_DOCUMENT:
        case END_DOCUMENT:
            forward(sink, source, event);
            break;
        case KEY_NAME:
            onKey(control, source, sink);
            break;
        case START_OBJECT:
        case START_ARRAY:
            onStart(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            onEnd(source, event, sink);
            break;
        default:
            onScalar(source, event, sink);
            break;
        }
    }

    private void onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        boolean parentKeepAll = containers > 0 && frameKeepAll[containers - 1];
        if (parentKeepAll)
        {
            // every child of a KEEP_ALL parent is retained regardless of its key content, so a key
            // fragmented across input windows streams straight through with nothing to decide — the same
            // as any other kept scalar/segment value.
            keyNode = null;
            keyDecision = Decision.KEEP_ALL;
            forwardKey(control, source, sink);
        }
        else if (source.deferredBytes())
        {
            // a fragmented key cannot be matched against the trie until it is complete — the trie's
            // children are compared as whole strings. Decline the fragment (consumed(0)) so the source
            // accumulates it whole (the same fallback a content-needing scalar value uses) and re-presents
            // it complete on a later window, then decide once the final fragment arrives.
            control.consumed(0);
            downstream = Status.STARVED;
        }
        else
        {
            Node parentNode = containers > 0 ? frameNode[containers - 1] : root;
            keyNode = lookup(parentNode, source.getStringView());
            Decision d = decide(keyNode);
            keyDecision = d;
            boolean parentEmit = containers == 0 || frameEmit[containers - 1];
            if (parentEmit && d == Decision.KEEP_ALL)
            {
                // A KEEP_ALL value is always emitted, so the key is forwarded live from the still-valid
                // view — no copy and no deferral. The match above also used the live view, so no ancestor
                // key is retained. Then arm the kept value for verbatim segment delivery (best-effort,
                // demand-gated).
                forwardKey(control, source, sink);
            }
            else
            {
                // A DESCEND key is buffered for deferral: its value's kind is still unknown, and a scalar
                // under a deeper-only pointer is dropped, so the key cannot be forwarded until the value
                // event. A SKIP key copies nothing.
                pendingKey = d == Decision.SKIP ? null : copyKey(source.getStringView());
            }
        }
    }

    // Forwards a KEEP_ALL key live from the still-valid source view and arms the kept value for verbatim
    // segment delivery (best-effort, demand-gated).
    private void forwardKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        forward(sink, source, JsonEvent.KEY_NAME);
        pendingKey = null;
        if (downstreamDemand)
        {
            control.segmentable();
        }
    }

    private CharSequence copyKey(
        CharSequence view)
    {
        pendingKeyBuffer.setLength(0);
        pendingKeyBuffer.append(view);
        return pendingKeyBuffer;
    }

    private void forwardPendingKey(
        JsonSink sink)
    {
        if (pendingKey != null)
        {
            forwardingKey = true;
            forward(sink, keySource.with(pendingKey), JsonEvent.KEY_NAME);
            forwardingKey = false;
            pendingKey = null;
        }
    }

    private void pushFrame(
        JsonEvent event,
        boolean emit,
        Decision d)
    {
        frameInArray[containers] = event == JsonEvent.START_ARRAY;
        frameEmit[containers] = emit;
        frameKeepAll[containers] = d == Decision.KEEP_ALL;
        frameNextIndex[containers] = 0;
        frameNode[containers] = valueNode;
        containers++;
    }

    private void onStart(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Decision d = enterValue();
        boolean parentEmit = containers == 0 || frameEmit[containers - 1];
        boolean emit = parentEmit && d != Decision.SKIP;
        if (emit && d == Decision.KEEP_ALL && downstreamDemand)
        {
            forwardPendingKey(sink);
            control.segmentable();
            segMode = SegMode.AWAITING;
            deferredStart = event;
        }
        else if (emit)
        {
            forwardPendingKey(sink);
            forward(sink, source, event);
            pushFrame(event, true, d);
        }
        else
        {
            pendingKey = null;
            pushFrame(event, false, d);
        }
    }

    private void onAwaiting(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (event == JsonEvent.SEGMENT)
        {
            segMode = SegMode.FORWARDING;
            deferredStart = null;
            forward(sink, source, event);
            if (!source.deferredBytes())
            {
                finishSegment();
            }
        }
        else
        {
            segMode = SegMode.NONE;
            forward(sink, source, deferredStart);
            pushFrame(deferredStart, true, Decision.KEEP_ALL);
            deferredStart = null;
            route(control, source, event, sink);
        }
    }

    private void onForwarding(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        forward(sink, source, event);
        if (!source.deferredBytes())
        {
            finishSegment();
        }
    }

    private void finishSegment()
    {
        segMode = SegMode.NONE;
        if (containers == 0)
        {
            rootDone = true;
        }
    }

    private void onEnd(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        containers--;
        if (frameEmit[containers])
        {
            forward(sink, source, event);
        }
        if (containers == 0)
        {
            rootDone = true;
        }
    }

    private void onScalar(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (scalarPending)
        {
            // a continuation fragment of a kept/dropped scalar value split across input windows: the value
            // was already entered on its first fragment, so forward (if kept) without re-entering and only
            // account for the consumed value once its closing fragment arrives (deferredBytes false)
            if (scalarEmit)
            {
                forward(sink, source, event);
            }
            if (!source.deferredBytes())
            {
                finishScalar();
            }
        }
        else
        {
            Decision d = enterValue();
            boolean parentEmit = containers == 0 || frameEmit[containers - 1];
            scalarEmit = parentEmit && d == Decision.KEEP_ALL;
            if (scalarEmit)
            {
                forwardPendingKey(sink);
                forward(sink, source, event);
            }
            else
            {
                pendingKey = null;
            }
            if (source.deferredBytes())
            {
                scalarPending = true;
            }
            else
            {
                finishScalar();
            }
        }
    }

    private void finishScalar()
    {
        scalarPending = false;
        if (containers == 0)
        {
            rootDone = true;
        }
    }

    private Decision enterValue()
    {
        Decision result;
        if (containers == 0)
        {
            valueNode = root;
            result = decide(root);
        }
        else
        {
            int parent = containers - 1;
            if (frameInArray[parent])
            {
                int index = frameNextIndex[parent];
                frameNextIndex[parent] = index + 1;
                if (frameKeepAll[parent])
                {
                    valueNode = null;
                    result = Decision.KEEP_ALL;
                }
                else
                {
                    valueNode = lookupIndex(frameNode[parent], index);
                    result = decide(valueNode);
                }
            }
            else
            {
                valueNode = keyNode;
                result = keyDecision;
                keyNode = null;
                keyDecision = null;
            }
        }
        return result;
    }

    private static Decision decide(
        Node node)
    {
        Decision result;
        if (node == null)
        {
            result = Decision.SKIP;
        }
        else if (node.keepAll)
        {
            result = Decision.KEEP_ALL;
        }
        else if (node.keys.length > 0)
        {
            result = Decision.DESCEND;
        }
        else
        {
            result = Decision.SKIP;
        }
        return result;
    }

    // Matches an object key against a node's children by the live char view, allocation-free.
    private static Node lookup(
        Node node,
        CharSequence key)
    {
        Node result = null;
        if (node != null)
        {
            for (int i = 0; result == null && i < node.keys.length; i++)
            {
                if (charsEqual(node.keys[i], key))
                {
                    result = node.nodes[i];
                }
            }
        }
        return result;
    }

    // Matches an array index against a node's children, preferring an explicit canonical-index child over
    // the "-" wildcard; the wildcard applies only to arrays, while an object key "-" matches via lookup.
    private static Node lookupIndex(
        Node node,
        int index)
    {
        Node result = null;
        if (node != null)
        {
            Node wildcard = null;
            for (int i = 0; result == null && i < node.keys.length; i++)
            {
                String segment = node.keys[i];
                if (WILDCARD.equals(segment))
                {
                    wildcard = node.nodes[i];
                }
                else if (matchesIndex(segment, index))
                {
                    result = node.nodes[i];
                }
            }
            if (result == null)
            {
                result = wildcard;
            }
        }
        return result;
    }

    private static boolean charsEqual(
        String segment,
        CharSequence key)
    {
        boolean matches = segment.length() == key.length();
        for (int i = 0; matches && i < segment.length(); i++)
        {
            matches = segment.charAt(i) == key.charAt(i);
        }
        return matches;
    }

    private static boolean matchesIndex(
        String segment,
        int index)
    {
        boolean matches = !segment.isEmpty() && (segment.length() == 1 || segment.charAt(0) != '0');
        int value = 0;
        for (int i = 0; matches && i < segment.length(); i++)
        {
            char c = segment.charAt(i);
            matches = Character.isDigit(c);
            if (matches)
            {
                int digit = c - '0';
                matches = value <= (Integer.MAX_VALUE - digit) / 10;
                if (matches)
                {
                    value = value * 10 + digit;
                }
            }
        }
        return matches && value == index;
    }

    private static Node compile(
        List<String> pointers)
    {
        NodeBuilder builder = new NodeBuilder();
        for (String pointer : pointers)
        {
            NodeBuilder node = builder;
            for (String segment : segments(pointer))
            {
                node = node.child(segment);
            }
            node.keepAll = true;
        }
        return builder.build();
    }

    private static String[] segments(
        String pointer)
    {
        String[] result;
        if (pointer.isEmpty())
        {
            result = new String[0];
        }
        else
        {
            String[] parts = pointer.substring(1).split("/", -1);
            for (int i = 0; i < parts.length; i++)
            {
                parts[i] = parts[i].replace("~1", "/").replace("~0", "~");
            }
            result = parts;
        }
        return result;
    }

    // An immutable trie node: children are parallel key/node arrays scanned linearly (a handful of children
    // per node), and keepAll marks a node where a retained pointer terminates.
    private static final class Node
    {
        private final String[] keys;
        private final Node[] nodes;
        private final boolean keepAll;

        private Node(
            String[] keys,
            Node[] nodes,
            boolean keepAll)
        {
            this.keys = keys;
            this.nodes = nodes;
            this.keepAll = keepAll;
        }
    }

    private static final class NodeBuilder
    {
        private final Map<String, NodeBuilder> children = new LinkedHashMap<>();
        private boolean keepAll;

        private NodeBuilder child(
            String segment)
        {
            return children.computeIfAbsent(segment, ignored -> new NodeBuilder());
        }

        private Node build()
        {
            int size = children.size();
            String[] keys = new String[size];
            Node[] nodes = new Node[size];
            int i = 0;
            for (Map.Entry<String, NodeBuilder> entry : children.entrySet())
            {
                keys[i] = entry.getKey();
                nodes[i] = entry.getValue().build();
                i++;
            }
            return new Node(keys, nodes, keepAll);
        }
    }

    private static final class KeySource implements JsonSource
    {
        private CharSequence key;

        private KeySource with(
            CharSequence key)
        {
            this.key = key;
            return this;
        }

        @Override
        public String getString()
        {
            return key == null ? null : key.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return key;
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
