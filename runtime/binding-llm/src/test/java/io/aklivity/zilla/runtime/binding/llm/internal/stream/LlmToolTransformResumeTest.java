/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

public class LlmToolTransformResumeTest
{
    private static final String LONG = "x".repeat(100);
    private static final int RENDER_BOUND = 8192;
    private static final int BIT_OPEN_KEY = 1 << 0;
    private static final int BIT_OPEN_VALUE = 1 << 1;
    private static final int BIT_CLOSE_KEY = 1 << 2;
    private static final int BIT_CLOSE_VALUE = 1 << 3;
    private static final int BIT_CLOSE_END = 1 << 4;
    private static final int BIT_DESCEND_KEY = 1 << 5;
    private static final int BIT_DESCEND_VALUE = 1 << 6;
    private static final int BIT_REPLACE_KEY = 1 << 7;
    private static final int BIT_REPLACE_VALUE = 1 << 8;
    private static final int BIT_ESCAPED_KEY = 1 << 9;
    private static final int BIT_ESCAPED_VALUE = 1 << 10;
    private static final int BIT_CAPTURED_KEY = 1 << 11;
    private static final int BIT_CAPTURED_VALUE = 1 << 12;
    private static final int BIT_CAPTURED_AGAIN_KEY = 1 << 13;
    private static final int BIT_CAPTURED_AGAIN_VALUE = 1 << 14;
    private static final int BIT_LOOP_ITEMS = 1 << 15;
    private static final int BIT_LOOP_COPY = 1 << 16;

    @Test
    public void shouldPassValues()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("a", LlmToolTransform.PASS_VALUE)
                .action("s", LlmToolTransform.PASS_VALUE)
                .action("o", LlmToolTransform.PASS_VALUE)
                .action("l", LlmToolTransform.PASS_VALUE),
            json("{'a':1,'s':'text','junk':'x','o':{'k':[1,2,{'z':null}],'e':{}},'l':'" + LONG + "'}"));
    }

    @Test
    public void shouldEmitFromHooks()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND)
                .action("p", LlmToolTransform.PASS_VALUE)
                .action("a", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1,'q':'x','p2':2},'a':1}"));
    }

    @Test
    public void shouldDescendKept()
    {
        assertResumesAs(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND_KEPT)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1,'x':2},'e':{'p':3}}"),
            json("{'open':'o','d':{'descend':'d2','p':1},'close':'c0'}"));
    }

    @Test
    public void shouldDescendKeptOrReplace()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND_KEPT_OR_REPLACE)
                .action("r", LlmToolTransform.DESCEND_KEPT_OR_REPLACE)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1},'r':'" + LONG + "'}"));
    }

    @Test
    public void shouldDescendOrReplace()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND_OR_REPLACE)
                .action("r", LlmToolTransform.DESCEND_OR_REPLACE)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1},'r':7}"));
    }

    @Test
    public void shouldDescendOrPass()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND_OR_PASS)
                .action("r", LlmToolTransform.DESCEND_OR_PASS)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1},'r':'" + LONG + "'}"));
    }

    @Test
    public void shouldDescendKeptOrElements()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("d", LlmToolTransform.DESCEND_KEPT_OR_ELEMENTS)
                .action("a", LlmToolTransform.DESCEND_KEPT_OR_ELEMENTS)
                .action("s", LlmToolTransform.DESCEND_KEPT_OR_ELEMENTS)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'d':{'p':1},'a':[1,'two',{'k':3}],'s':'" + LONG + "'}"));
    }

    @Test
    public void shouldDescendElements()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("a", LlmToolTransform.DESCEND_ELEMENTS)
                .action("n", LlmToolTransform.DESCEND_ELEMENTS)
                .nest("a", "wrap"),
            json("{'a':[1,'two',{'k':3},[4]],'n':null}"));
    }

    @Test
    public void shouldNestMembers()
    {
        assertResumesAs(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("a", LlmToolTransform.PASS_VALUE)
                .action("b", LlmToolTransform.PASS_VALUE)
                .action("c", LlmToolTransform.PASS_VALUE)
                .action("d", LlmToolTransform.PASS_STRING)
                .nest("a", "wrap")
                .nest("b", "wrap")
                .nest("d", "other"),
            json("{'a':1,'b':{'x':[1,{'y':2}]},'c':2,'d':'" + LONG + "'}"),
            json("{'wrap':{'a':1},'wrap':{'b':{'x':[1,{'y':2}]}},'c':2,'other':{'d':'" + LONG + "'}}"));
    }

    @Test
    public void shouldConvertStrings()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("s", LlmToolTransform.CONVERT_STRING)
                .action("w", LlmToolTransform.WRAP_STRING_AS_ELEMENT)
                .action("a", LlmToolTransform.CONVERT_STRING)
                .action("v", LlmToolTransform.WRAP_STRING_AS_ELEMENT)
                .nest("s", "wrap"),
            json("{'s':'" + LONG + "','w':'one','a':['p',{'q':1}],'v':['r']}"));
    }

    @Test
    public void shouldLimitStrings()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("s", LlmToolTransform.LIMIT_STRING)
                .action("t", LlmToolTransform.LIMIT_STRING)
                .action("n", LlmToolTransform.LIMIT_STRING)
                .nest("s", "wrap"),
            json("{'s':'short','t':'" + LONG + "','n':1,'u':'xy'}"));
    }

    @Test
    public void shouldClampNumbers()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("lo", LlmToolTransform.CLAMP_NUMBER)
                .action("in", LlmToolTransform.CLAMP_NUMBER)
                .action("hi", LlmToolTransform.CLAMP_NUMBER)
                .nest("hi", "wrap"),
            json("{'lo':-5,'in':3,'hi':99}"));
    }

    @Test
    public void shouldRangeIntegers()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("in", LlmToolTransform.INTEGER_RANGE)
                .action("out", LlmToolTransform.INTEGER_RANGE)
                .action("frac", LlmToolTransform.INTEGER_RANGE)
                .action("nul", LlmToolTransform.INTEGER_RANGE)
                .nest("in", "wrap"),
            json("{'in':3,'out':99,'frac':2.5,'nul':null}"));
    }

    @Test
    public void shouldHoldStrings()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("h", LlmToolTransform.HOLD_STRING)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'h':'" + LONG + "','p':1}"));
    }

    @Test
    public void shouldEscapeObjects()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("e", LlmToolTransform.ESCAPE_OBJECT)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'p':1,'e':{'a':[1,{'b':'" + LONG + "'}],'c':{}}}"));
    }

    @Test
    public void shouldEscapeObjectsWithScanner()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .scanned("e")
                .action("e", LlmToolTransform.ESCAPE_OBJECT),
            json("{'e':{'properties':{'a':{'type':'string'}},'type':'object'}}"));
    }

    @Test
    public void shouldUnescapeObjects()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("u", LlmToolTransform.UNESCAPE_OBJECT)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'p':1,'u':") + quote(json("{'a':[1,{'b':'" + LONG + "'}],'c':{}}")) + "}");
    }

    @Test
    public void shouldUnescapeEmptyObjects()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("u", LlmToolTransform.UNESCAPE_OBJECT),
            json("{'u':''}"));
    }

    @Test
    public void shouldPassDataUri()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("i", LlmToolTransform.PASS_DATA_URI),
            attributes -> attributes.mediaType("image/png"),
            json("{'i':'" + LONG + LONG + "'}"));
    }

    @Test
    public void shouldStripDataUri()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("i", LlmToolTransform.STRIP_DATA_URI),
            attributes -> attributes.mediaType("image/png"),
            json("{'i':'data:image/png;base64," + LONG + LONG + "'}"));
    }

    @Test
    public void shouldStripDataUriWithNest()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("i", LlmToolTransform.STRIP_DATA_URI)
                .nest("i", "wrap"),
            attributes -> attributes.mediaType("image/png"),
            json("{'i':'data:image/png;base64,abc'}"));
    }

    @Test
    public void shouldCaptureValues()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("c", LlmToolTransform.CAPTURE_VALUE)
                .action("d", LlmToolTransform.CAPTURE_VALUE)
                .action("n", LlmToolTransform.CAPTURE_VALUE)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'c':{'a':[1,'two',{'b':null}],'e':true},'p':1,'d':'" + LONG + "','n':3.5}"));
    }

    @Test
    public void shouldCaptureValuesTwice()
    {
        assertResumesAs(
            probe(LlmToolTransform.MODE_OBJECT)
                .repeating()
                .action("c", LlmToolTransform.CAPTURE_VALUE),
            json("{'c':{'a':[1,'two',{'b':null}]}}"),
            json("{'c':{'a':[1,'two',{'b':null}]},'again':{'a':[1,'two',{'b':null}]}}"));
    }

    @Test
    public void shouldReplaceValues()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .marking()
                .action("r", LlmToolTransform.REPLACE_VALUE)
                .action("s", LlmToolTransform.REPLACE_VALUE)
                .action("p", LlmToolTransform.PASS_VALUE),
            json("{'r':{'a':[1,2]},'s':'" + LONG + "','p':1}"));
    }

    @Test
    public void shouldPassStringOrDrop()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .action("s", LlmToolTransform.PASS_STRING_OR_DROP)
                .action("o", LlmToolTransform.PASS_STRING_OR_DROP)
                .action("p", LlmToolTransform.PASS_OBJECT),
            json("{'s':'" + LONG + "','o':{'k':[1]},'p':{'q':1}}"));
    }

    @Test
    public void shouldPassArrayElements()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_ARRAY)
                .action("a", LlmToolTransform.PASS_VALUE),
            json("[{'a':1},{},{'a':2,'x':3}]"));
    }

    @Test
    public void shouldPassArrayElementsWithMarkers()
    {
        assertResumesAs(
            probe(LlmToolTransform.MODE_ARRAY)
                .marking()
                .action("a", LlmToolTransform.PASS_VALUE)
                .action("d", LlmToolTransform.DESCEND_KEPT),
            json("[{'a':1},{},{'a':2,'x':3},{'d':{'a':4}}]"),
            json("[{'open':'o','a':1,'close':'c0'},{'open':'o','close':'c1'},{'open':'o','a':2,'close':'c2'}," +
                "{'open':'o','d':{'descend':'d2','a':4},'close':'c3'}]"));
    }

    @Test
    public void shouldCloseArrayElementsOpenedByTheirClose()
    {
        assertResumesAs(
            probe(LlmToolTransform.MODE_ARRAY)
                .closing()
                .action("a", LlmToolTransform.PASS_VALUE),
            json("[{'a':1},{},{'x':2}]"),
            json("[{'a':1,'close':'c0'},{'close':'c1'},{'close':'c2'}]"));
    }

    @Test
    public void shouldPassValue()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_VALUE),
            json("{'a':1,'b':{'c':[2,'" + LONG + "']}}"));
    }

    @Test
    public void shouldExtractValue()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_EXTRACT)
                .action("v", LlmToolTransform.PASS_STRING),
            json("{'w':3,'d':{'z':1},'v':'" + LONG + "'}"));
    }

    @Test
    public void shouldExtractNestedValue()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_EXTRACT, 2)
                .action("x", LlmToolTransform.DESCEND)
                .action("y", LlmToolTransform.PASS_STRING),
            json("{'v':4,'x':{'z':[1,2],'y':'" + LONG + "','w':3}}"));
    }

    @Test
    public void shouldScanSchemas()
    {
        assertResumes(
            probe(LlmToolTransform.MODE_OBJECT)
                .scanned("schema")
                .action("schema", LlmToolTransform.PASS_VALUE),
            json("{'schema':{'properties':{'a':{'type':'string','description':'" + LONG + "'}},'type':'object'}}"));
    }

    @Test
    public void shouldRejectKeywordInScannedSchema()
    {
        assertRejects(
            probe(LlmToolTransform.MODE_OBJECT)
                .scanned("schema")
                .resumable(true)
                .action("schema", LlmToolTransform.PASS_VALUE),
            json("{'schema':{'properties':{'a':{'not':{'type':'string'}}},'type':'object'}}"));
    }

    @Test
    public void shouldRejectKeywordInScannedSchemaAcrossBounds()
    {
        assertRejectsAcrossBounds(
            probe(LlmToolTransform.MODE_OBJECT)
                .scanned("schema")
                .resumable(true)
                .action("schema", LlmToolTransform.PASS_VALUE),
            json("{'schema':{'properties':{'a':{'type':'string'},'b':{'not':{'type':'string'}}},'type':'object'}}"));
    }

    @Test
    public void shouldRejectKeywordInEscapedScannedSchemaAcrossBounds()
    {
        assertRejectsAcrossBounds(
            probe(LlmToolTransform.MODE_OBJECT)
                .scanned("schema")
                .resumable(true)
                .action("schema", LlmToolTransform.ESCAPE_OBJECT),
            json("{'schema':{'properties':{'a':{'type':'string'},'b':{'if':{'type':'string'}}},'type':'object'}}"));
    }

    @Test
    public void shouldRequireBitToEmitWhenResumable()
    {
        final Probe probe = probe(LlmToolTransform.MODE_OBJECT).unbitted().resumable(true);
        final LlmSectionPipeline pipeline = new LlmSectionPipeline(probe, new UnsafeBufferEx(new byte[RENDER_BOUND]));
        final byte[] bytes = json("{'a':1}").getBytes(UTF_8);

        pipeline.begin(new LlmSectionAttributes());

        assertThrows(IllegalStateException.class, () ->
            pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true, (buffer, index, size, done) ->
            {
            }));
    }

    @Test
    public void shouldRejectMembers()
    {
        assertRejects(
            probe(LlmToolTransform.MODE_OBJECT)
                .resumable(true)
                .action("r", LlmToolTransform.REJECT)
                .action("a", LlmToolTransform.PASS_VALUE),
            json("{'a':1,'r':2}"));
    }

    @Test
    public void shouldRejectMismatchedTypes()
    {
        assertRejects(
            probe(LlmToolTransform.MODE_OBJECT)
                .resumable(true)
                .action("s", LlmToolTransform.PASS_STRING),
            json("{'s':1}"));
        assertRejects(
            probe(LlmToolTransform.MODE_OBJECT)
                .resumable(true)
                .action("d", LlmToolTransform.DESCEND_KEPT),
            json("{'d':1}"));
    }

    @Test
    public void shouldLoopOverNoItems()
    {
        assertResumesLiteral(
            loopProbe(),
            json("{'c':[]}"),
            json("{'between':'b','copy':[],'end':'e'}"));
    }

    @Test
    public void shouldLoopOverOneItem()
    {
        assertResumesLiteral(
            loopProbe(),
            json("{'c':[2]}"),
            json("{'k2':2,'between':'b','copy':[2],'end':'e'}"));
    }

    @Test
    public void shouldLoopWithExtraEventsOnSomeIterations()
    {
        assertResumesLiteral(
            loopProbe(),
            json("{'c':[1,2,3,4,5]}"),
            json("{'k1':1,'odd1':true,'k2':2,'k3':3,'odd3':true,'k4':4,'k5':5,'odd5':true," +
                "'between':'b','copy':[1,2,3,4,5],'end':'e'}"));
    }

    @Test
    public void shouldLoopAgainInTheNextDispatch()
    {
        assertResumesLiteral(
            loopProbe(),
            json("{'c':[1,2],'d':[3]}"),
            json("{'k1':1,'odd1':true,'k2':2,'between':'b','copy':[1,2],'end':'e'," +
                "'k3':3,'odd3':true,'between':'b','copy':[3],'end':'e'}"));
    }

    @Test
    public void shouldRequireResumableToLoop()
    {
        final Probe probe = loopProbe().resumable(false);
        final LlmSectionPipeline pipeline = new LlmSectionPipeline(probe, new UnsafeBufferEx(new byte[RENDER_BOUND]));
        final byte[] bytes = json("{'c':[1]}").getBytes(UTF_8);

        pipeline.begin(new LlmSectionAttributes());

        assertThrows(IllegalStateException.class, () ->
            pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true, (buffer, index, size, done) ->
            {
            }));
    }

    private static Probe loopProbe()
    {
        return probe(LlmToolTransform.MODE_OBJECT)
            .looping()
            .action("c", LlmToolTransform.CAPTURE_VALUE)
            .action("d", LlmToolTransform.CAPTURE_VALUE);
    }

    private static Probe probe(
        int mode)
    {
        return new Probe(mode, 1);
    }

    private static Probe probe(
        int mode,
        int extractDepth)
    {
        return new Probe(mode, extractDepth);
    }

    private static void assertResumes(
        Probe probe,
        String json)
    {
        assertResumes(probe, null, json);
    }

    private static void assertResumesLiteral(
        Probe probe,
        String json,
        String literal)
    {
        assertThat(render(probe.copy().resumable(true), null, json), equalTo(literal));
        assertTransforms(probe.copy().resumable(true), null, null, json, literal);
    }

    private static void assertResumesAs(
        Probe probe,
        String json,
        String literal)
    {
        assertThat(render(probe.copy().resumable(false), null, json), equalTo(literal));
        assertResumes(probe, null, json);
    }

    private static void assertResumes(
        Probe probe,
        Consumer<LlmSectionAttributes> early,
        String json)
    {
        final String expected = render(probe.copy().resumable(false), early, json);

        assertThat(expected, not(emptyString()));
        assertTransforms(probe.copy().resumable(true), early, null, json, expected);
    }

    private static void assertRejectsAcrossBounds(
        Probe probe,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);

        for (int window : new int[] {1, 7, 64})
        {
            for (int bound = 20; bound <= 160; bound++)
            {
                final LlmSectionPipeline pipeline = new LlmSectionPipeline(probe, new UnsafeBufferEx(new byte[bound]));
                boolean accepted = true;

                pipeline.begin(new LlmSectionAttributes());

                for (int offset = 0; accepted && offset < bytes.length; offset += window)
                {
                    final int length = Math.min(window, bytes.length - offset);

                    accepted = pipeline.feed(input, offset, length, offset + length == bytes.length, (buffer, at, size, done) ->
                    {
                    });
                }

                assertThat("rejected window " + window + " bound " + bound, accepted, is(false));
            }
        }
    }

    private static String render(
        Probe probe,
        Consumer<LlmSectionAttributes> early,
        String json)
    {
        final LlmSectionPipeline pipeline = new LlmSectionPipeline(probe, new UnsafeBufferEx(new byte[RENDER_BOUND]));
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final LlmSectionAttributes attributes = new LlmSectionAttributes();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        if (early != null)
        {
            early.accept(attributes);
        }

        pipeline.begin(attributes);

        final boolean accepted = pipeline.feed(input, 0, bytes.length, true, (buffer, index, size, done) ->
        {
            final byte[] chunk = new byte[size];

            buffer.getBytes(index, chunk);
            output.write(chunk, 0, size);
        });

        assertThat("accepted", accepted, is(true));

        return output.toString(UTF_8);
    }

    private static final class Probe extends LlmToolTransform
    {
        private final Map<String, Integer> actions;
        private final Map<String, String> containers;
        private final Set<String> scans;
        private final LlmSchemaScanner scanner;
        private final int[] opened;
        private final int shape;
        private final int extractDepth;

        private boolean resumable;
        private boolean unbitted;
        private boolean marking;
        private boolean closing;
        private boolean repeating;
        private boolean looping;
        private int closes;

        private Probe(
            int mode,
            int extractDepth)
        {
            super(mode, null, extractDepth);
            this.shape = mode;
            this.extractDepth = extractDepth;
            this.opened = new int[8];
            this.actions = new HashMap<>();
            this.containers = new HashMap<>();
            this.scans = new HashSet<>();
            this.scanner = new LlmSchemaScanner(LlmSchemaScanner.TARGET_GEMINI);
        }

        private Probe(
            Probe that)
        {
            super(that.shape, null, that.extractDepth);
            this.shape = that.shape;
            this.extractDepth = that.extractDepth;
            this.opened = new int[8];
            this.actions = that.actions;
            this.containers = that.containers;
            this.scans = that.scans;
            this.scanner = that.scanner;
            this.marking = that.marking;
            this.closing = that.closing;
            this.repeating = that.repeating;
            this.looping = that.looping;
        }

        private Probe copy()
        {
            return new Probe(this);
        }

        private Probe resumable(
            boolean resumable)
        {
            this.resumable = resumable;
            return this;
        }

        private Probe unbitted()
        {
            this.unbitted = true;
            return this;
        }

        private Probe repeating()
        {
            this.repeating = true;
            return this;
        }

        private Probe looping()
        {
            this.looping = true;
            return this;
        }

        private Probe closing()
        {
            this.closing = true;
            return this;
        }

        private Probe marking()
        {
            this.marking = true;
            return this;
        }

        private Probe action(
            String key,
            int action)
        {
            actions.put(key, action);
            return this;
        }

        private Probe nest(
            String key,
            String container)
        {
            containers.put(key, container);
            return this;
        }

        private Probe scanned(
            String key)
        {
            scans.add(key);
            return this;
        }

        @Override
        protected boolean resumable()
        {
            return resumable;
        }

        @Override
        public void reset()
        {
            super.reset();
            closes = 0;
        }

        @Override
        protected int member(
            int depth,
            CharSequence key)
        {
            final String name = key.toString();
            final int action = actions.getOrDefault(name, DROP);
            int result = action;

            target = action == DESCEND || action == DROP ? null : name;
            opened[depth] = action;

            if (containers.containsKey(name))
            {
                nest(containers.get(name));
            }

            switch (action)
            {
            case LIMIT_STRING:
                result = limitString(5);
                break;
            case CLAMP_NUMBER:
                result = clamp(name, 0, 10);
                break;
            case INTEGER_RANGE:
                result = rangeInteger(name, 0, 10);
                break;
            case CAPTURE_VALUE:
                result = capture(key);
                break;
            default:
                break;
            }

            if (scans.contains(name))
            {
                scan(scanner, name);
            }

            return result;
        }

        @Override
        protected void onOpen()
        {
            if (unbitted)
            {
                emit(JsonEvent.KEY_NAME, "k");
            }

            if (marking)
            {
                mark(BIT_OPEN_KEY, BIT_OPEN_VALUE, "open", "o");
            }
        }

        @Override
        protected Status onClose(
            int depth)
        {
            final boolean marked = (marking || closing) && depth == 1;
            final boolean counted = !emitted(ends(depth) ? BIT_CLOSE_END : BIT_CLOSE_VALUE);
            final boolean closed = !marked ||
                emit(BIT_CLOSE_KEY, JsonEvent.KEY_NAME, "close") &&
                emit(BIT_CLOSE_VALUE, JsonEvent.VALUE_STRING, "c" + closes);

            if (closed && (!ends(depth) || pass(BIT_CLOSE_END, JsonEvent.END_OBJECT)) && marked && counted)
            {
                closes++;
            }

            return Status.ADVANCED;
        }

        @Override
        protected Status onDescend(
            int depth)
        {
            if (marking)
            {
                mark(BIT_DESCEND_KEY, BIT_DESCEND_VALUE, "descend", "d" + depth);
            }

            return Status.ADVANCED;
        }

        @Override
        protected Status onReplace(
            JsonEvent event)
        {
            if (marking)
            {
                mark(BIT_REPLACE_KEY, BIT_REPLACE_VALUE, "replaced", event.name());
            }

            return Status.ADVANCED;
        }

        @Override
        protected void onEscapedEnd()
        {
            if (marking)
            {
                mark(BIT_ESCAPED_KEY, BIT_ESCAPED_VALUE, "escaped", "z");
            }
        }

        @Override
        protected Status onCaptured(
            LlmCapturedValue value)
        {
            if (looping)
            {
                onLooped(value);
            }
            else if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, capturedMember()) &&
                emitCaptured(BIT_CAPTURED_VALUE, value, 0) &&
                repeating &&
                emit(BIT_CAPTURED_AGAIN_KEY, JsonEvent.KEY_NAME, "again"))
            {
                emitCaptured(BIT_CAPTURED_AGAIN_VALUE, value, 0);
            }

            return Status.ADVANCED;
        }

        @Override
        protected String converted(
            String value)
        {
            return value.toUpperCase();
        }

        @Override
        protected String adapted(
            String value)
        {
            return '<' + value + '>';
        }

        private void onLooped(
            LlmCapturedValue value)
        {
            if (emitItems(value) &&
                emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, "between") &&
                emit(BIT_CAPTURED_VALUE, JsonEvent.VALUE_STRING, "b") &&
                emitCopy(value) &&
                emit(BIT_CAPTURED_AGAIN_KEY, JsonEvent.KEY_NAME, "end"))
            {
                emit(BIT_CAPTURED_AGAIN_VALUE, JsonEvent.VALUE_STRING, "e");
            }
        }

        private boolean emitItems(
            LlmCapturedValue value)
        {
            final int last = value.end(0) - 1;

            for (int at = 1; at < last; at++)
            {
                final String number = value.text(at);
                final boolean odd = Integer.parseInt(number) % 2 != 0;

                if (emitLoop(BIT_LOOP_ITEMS, JsonEvent.KEY_NAME, "k" + number) &&
                    emitLoop(BIT_LOOP_ITEMS, JsonEvent.VALUE_NUMBER, number) &&
                    odd &&
                    emitLoop(BIT_LOOP_ITEMS, JsonEvent.KEY_NAME, "odd" + number))
                {
                    emitLoop(BIT_LOOP_ITEMS, JsonEvent.VALUE_TRUE);
                }
            }

            return endLoop(BIT_LOOP_ITEMS);
        }

        private boolean emitCopy(
            LlmCapturedValue value)
        {
            emitLoop(BIT_LOOP_COPY, JsonEvent.KEY_NAME, "copy");
            emitLoopCaptured(BIT_LOOP_COPY, value, 0);

            return endLoop(BIT_LOOP_COPY);
        }

        private boolean ends(
            int depth)
        {
            final int action = opened[depth - 1];

            return shape == MODE_OBJECT &&
                (depth == 1 ||
                    action == DESCEND_KEPT || action == DESCEND_KEPT_OR_REPLACE || action == DESCEND_KEPT_OR_ELEMENTS) ||
                shape == MODE_ARRAY && depth > 1 &&
                    (action == DESCEND_KEPT || action == DESCEND_KEPT_OR_REPLACE || action == DESCEND_KEPT_OR_ELEMENTS);
        }

        private void mark(
            int keyBit,
            int valueBit,
            String key,
            String value)
        {
            emit(keyBit, JsonEvent.KEY_NAME, key);
            emit(valueBit, JsonEvent.VALUE_STRING, value);
        }
    }
}
