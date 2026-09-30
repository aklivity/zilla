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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

/**
 * The canonical streaming-response vocabulary a dialect's decode {@code JsonTransform} emits and a
 * (possibly different) dialect's encode {@code JsonSink} consumes -- purely in-process {@link
 * io.aklivity.zilla.runtime.common.json.JsonEvent} traffic between the two, never itself serialized to any
 * wire. Each action is one flat object, framed by its own {@code START_DOCUMENT}/{@code END_DOCUMENT}
 * cycle: {@code {"type":"messageStart",...}}, {@code {"type":"blockStart",...}}, {@code
 * {"type":"data","text":...}}, {@code {"type":"blockEnd",...}}, {@code {"type":"finish",...}}, {@code
 * {"type":"usage",...}}, {@code {"type":"end"}}. Shared field-name constants so an emitter and a consumer
 * that may be a mismatched dialect pair (e.g. OpenAI decode feeding Anthropic encode) agree byte-for-byte.
 */
final class LlmCanonicalEvent
{
    static final String TYPE = "type";

    static final String TYPE_MESSAGE_START = "messageStart";
    static final String TYPE_BLOCK_START = "blockStart";
    static final String TYPE_DATA = "data";
    static final String TYPE_BLOCK_END = "blockEnd";
    static final String TYPE_FINISH = "finish";
    static final String TYPE_USAGE = "usage";
    static final String TYPE_END = "end";

    static final String CHOICE_INDEX = "choiceIndex";
    static final String ID = "id";
    static final String MODEL = "model";
    static final String ROLE = "role";
    static final String BLOCK_ID = "blockId";
    static final String KIND = "kind";
    static final String KIND_TEXT = "text";
    static final String KIND_TOOL_CALL = "toolCall";
    static final String TOOL_ID = "toolId";
    static final String TOOL_NAME = "toolName";
    static final String TEXT = "text";
    static final String REASON = "reason";
    static final String INPUT_TOKENS = "inputTokens";
    static final String OUTPUT_TOKENS = "outputTokens";

    private LlmCanonicalEvent()
    {
    }
}
