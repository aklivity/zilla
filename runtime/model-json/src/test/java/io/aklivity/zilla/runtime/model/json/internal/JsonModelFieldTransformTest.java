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
package io.aklivity.zilla.runtime.model.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

// Exercises JsonModelFieldTransform's real (not merely observed) FIELD/REPLACED/DECLINED handling,
// through the same JsonModelDecoderPipeline a caller actually drives -- proving a renamed or remapped
// field genuinely changes the destination bytes, at any nesting depth, not only the top level
// JsonModelDecoderPipelineTest's own observation-only field-extraction tests already cover.
public class JsonModelFieldTransformTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_NONE = 0x00;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String ANY_SCHEMA = """
        {
            "type": "object"
        }""";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldRenameTopLevelField()
    {
        String output = decode(renaming("$.max_tokens", "$.maxOutputTokens"),
            "{\"model\":\"gpt-4o\",\"max_tokens\":256}");

        assertThat(output, equalTo("{\"model\":\"gpt-4o\",\"maxOutputTokens\":256}"));
    }

    @Test
    public void shouldRemapScalarValueAtSamePath()
    {
        String output = decode(remappingValue("$.finish_reason", "tool_calls", "tool_call"),
            "{\"finish_reason\":\"tool_calls\"}");

        assertThat(output, equalTo("{\"finish_reason\":\"tool_call\"}"));
    }

    @Test
    public void shouldForwardUnmatchedValueUnchanged()
    {
        String output = decode(remappingValue("$.finish_reason", "tool_calls", "tool_call"),
            "{\"finish_reason\":\"stop\"}");

        assertThat(output, equalTo("{\"finish_reason\":\"stop\"}"));
    }

    @Test
    public void shouldDeclineFieldAsNullPlaceholder()
    {
        String output = decode(declining("$.secret"), "{\"id\":\"1\",\"secret\":\"shh\"}");

        assertThat(output, equalTo("{\"id\":\"1\",\"secret\":null}"));
    }

    @Test
    public void shouldRenameNestedObjectField()
    {
        String output = decode(renaming("$.usage.prompt_tokens", "$.usage.inputTokens"),
            "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");

        assertThat(output, equalTo("{\"usage\":{\"inputTokens\":10,\"completion_tokens\":5}}"));
    }

    @Test
    public void shouldRenameFieldInsideEachArrayElement()
    {
        String output = decode(renamingArrayElementField("choices", "index", "choiceIndex"),
            "{\"choices\":[{\"index\":0,\"text\":\"a\"},{\"index\":1,\"text\":\"b\"}]}");

        assertThat(output, equalTo("{\"choices\":[{\"choiceIndex\":0,\"text\":\"a\"},{\"choiceIndex\":1,\"text\":\"b\"}]}"));
    }

    @Test
    public void shouldForwardContainerValuedMembersUnchanged()
    {
        String output = decode(renaming("$.max_tokens", "$.maxOutputTokens"),
            "{\"max_tokens\":1,\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"f\",\"arguments\":\"{}\"}}]}");

        assertThat(output, equalTo(
            "{\"maxOutputTokens\":1,\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"f\",\"arguments\":\"{}\"}}]}"));
    }

    @Test
    public void shouldRenameFieldSpanningInputWindow()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, renaming("$.note", "$.comment"), ModelCache.NONE);

        String longValue = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        byte[] head = "{\"note\":".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[512]);

        ModelPipelineResult r1 = pipeline.transform(0L, 0L, 0L, 0x02,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertThat(r1.status(), equalTo(ModelStatus.UNDERFLOW));
        String produced1 = text(dst, r1.produced());

        byte[] remainder = concat(head, r1.consumed(), new byte[0]);
        byte[] tail = ("\"" + longValue + "\"}").getBytes(UTF_8);
        byte[] window2 = concat(remainder, 0, tail);
        ModelPipelineResult r2 = pipeline.transform(0L, 0L, 0L, 0x01,
            new UnsafeBufferEx(window2), 0, window2.length, dst, 0, dst.capacity());

        assertThat(r2.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(produced1 + text(dst, r2.produced()), equalTo("{\"comment\":\"" + longValue + "\"}"));
    }

    @Test
    public void shouldResumeRenamedFieldWriteAfterOutputOverflow()
    {
        String output = decodeWithSmallOutput(renaming("$.max_tokens", "$.maxOutputTokens"),
            "{\"max_tokens\":256}", 3);

        assertThat(output, equalTo("{\"maxOutputTokens\":256}"));
    }

    @Test
    public void shouldResumeContainerKeyWriteAfterOutputOverflow()
    {
        String output = decodeWithSmallOutput(renaming("$.max_tokens", "$.maxOutputTokens"),
            "{\"usage\":{\"prompt_tokens\":10},\"max_tokens\":256}", 3);

        assertThat(output, equalTo("{\"usage\":{\"prompt_tokens\":10},\"maxOutputTokens\":256}"));
    }

    @Test
    public void shouldRoundTripThroughEncoderWithSameRename()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelEnvelope.NONE, renaming("$.max_tokens", "$.maxOutputTokens"));

        byte[] in = "{\"maxOutputTokens\":256}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertThat(result.status(), equalTo(ModelStatus.COMPLETE));
    }

    private String decode(
        ModelTransform transform,
        String json)
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, transform, ModelCache.NONE);

        byte[] in = json.getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[1024]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertThat(result.status(), equalTo(ModelStatus.COMPLETE));
        return text(dst, result.produced());
    }

    // drives the pipeline with a deliberately small output window so a renamed field's own multi-step
    // write (key then value, or a container's own key before it opens) overflows mid-write at least
    // once, forcing resume() to continue exactly where the prior call left off
    private String decodeWithSmallOutput(
        ModelTransform transform,
        String json,
        int dstCapacity)
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, transform, ModelCache.NONE);

        byte[] in = json.getBytes(UTF_8);
        UnsafeBufferEx src = new UnsafeBufferEx(in);
        StringBuilder output = new StringBuilder();
        boolean overflowed = false;
        ModelStatus status;
        int iterations = 0;
        int flags = FLAGS_COMPLETE;
        do
        {
            assertThat("exceeded iteration bound without completing", ++iterations, lessThan(1000));
            MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[dstCapacity]);
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags,
                src, 0, in.length, dst, 0, dst.capacity());
            output.append(text(dst, result.produced()));
            status = result.status();
            overflowed |= status == ModelStatus.OVERFLOW;
            flags = FLAGS_NONE;
        } while (status == ModelStatus.OVERFLOW);

        assertThat(status, equalTo(ModelStatus.COMPLETE));
        assertThat(overflowed, equalTo(true));
        return output.toString();
    }

    // renames a top-level or nested-object scalar field found at fromPath to toPath, forwarding every
    // other field unchanged
    private static ModelTransform renaming(
        String fromPath,
        String toPath)
    {
        return new ModelTransform()
        {
            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                ModelStatus status;
                if (event == ModelEvent.FIELD && fromPath.equals(source.getPath()))
                {
                    status = sink.transform(control, new Renamed(toPath, source.getValue()), ModelEvent.REPLACED);
                }
                else
                {
                    status = sink.transform(control, source, event);
                }
                return status;
            }
        };
    }

    // renames one field name found inside every element of a top-level array, regardless of index
    private static ModelTransform renamingArrayElementField(
        String arrayName,
        String fromField,
        String toField)
    {
        String prefix = "$." + arrayName + "[";
        return new ModelTransform()
        {
            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                ModelStatus status;
                String path = source.getPath();
                if (event == ModelEvent.FIELD && path != null && path.startsWith(prefix) && path.endsWith("." + fromField))
                {
                    String toPath = path.substring(0, path.length() - fromField.length()) + toField;
                    status = sink.transform(control, new Renamed(toPath, source.getValue()), ModelEvent.REPLACED);
                }
                else
                {
                    status = sink.transform(control, source, event);
                }
                return status;
            }
        };
    }

    // remaps a known scalar value at one path to a different value, leaving the path (and any other
    // value at that path) unchanged
    private static ModelTransform remappingValue(
        String path,
        String fromValue,
        String toValue)
    {
        return new ModelTransform()
        {
            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                ModelStatus status;
                DirectBufferEx value = source.getValue();
                String text = event == ModelEvent.FIELD && path.equals(source.getPath())
                    ? value.getStringWithoutLengthUtf8(0, value.capacity())
                    : null;
                if (fromValue.equals(text))
                {
                    status = sink.transform(control, new Renamed(path, toValue), ModelEvent.REPLACED);
                }
                else
                {
                    status = sink.transform(control, source, event);
                }
                return status;
            }
        };
    }

    private static ModelTransform declining(
        String path)
    {
        return new ModelTransform()
        {
            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                return event == ModelEvent.FIELD && path.equals(source.getPath())
                    ? sink.transform(control, source, ModelEvent.DECLINED)
                    : sink.transform(control, source, event);
            }
        };
    }

    private static byte[] concat(
        byte[] head,
        int headOffset,
        byte[] tail)
    {
        int headLength = head.length - headOffset;
        byte[] result = new byte[headLength + tail.length];
        System.arraycopy(head, headOffset, result, 0, headLength);
        System.arraycopy(tail, 0, result, headLength, tail.length);
        return result;
    }

    private static String text(
        MutableDirectBufferEx dst,
        int produced)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        return new String(chunk, UTF_8);
    }

    private JsonModelHandlerImpl newHandler()
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(ANY_SCHEMA)
                .build()
            .build();
        JsonModelConfig model = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(0)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new JsonModelHandlerImpl(model, context, List.of());
    }

    // a substitute ModelSource a ModelTransform constructs to answer REPLACED: value bytes wrapped
    // directly (no copy), a path that may equal the original (a value-only remap) or name a sibling key
    // (a rename)
    private static final class Renamed implements ModelSource
    {
        private final String path;
        private final DirectBufferEx value;

        private Renamed(
            String path,
            DirectBufferEx value)
        {
            this.path = path;
            this.value = value;
        }

        private Renamed(
            String path,
            String value)
        {
            this(path, new UnsafeBufferEx(value.getBytes(UTF_8)));
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return value;
        }
    }
}
