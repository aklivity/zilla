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

import java.util.BitSet;
import java.util.Locale;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

final class LlmGeminiBlockWriter
{
    static final int MORE = 0;
    static final int VALUE = 1;
    static final int REJECT = 2;

    static final int ACTION_DROP = 0;
    static final int ACTION_COPY = 1;
    static final int ACTION_FUNCTION = 2;
    static final int ACTION_ESCAPE = 3;
    static final int ACTION_DESCEND = 4;
    static final int ACTION_TEXT = 5;
    static final int ACTION_ID = 6;
    static final int ACTION_SCHEMA = 7;
    static final int ACTION_OPTIONAL = 8;
    static final int ACTION_DATA = 9;

    static final int MODE_CALL = 0;
    static final int MODE_RESULT = 1;
    static final int MODE_SERVER_CALL = 2;
    static final int MODE_SERVER_RESULT = 3;
    static final int MODE_DEFINITION = 4;
    static final int MODE_MEDIA = 5;
    static final int MODE_CONTROL = 6;
    static final int MODE_VALUE = 7;

    static final int CAPTURE_TOOL_CONFIG = 1;
    static final int CAPTURE_SERVICE_TIER = 2;

    private static final String PREFIX = LlmGeminiDialectFactorySpi.NAME + ":";
    private static final String MEMBER_FUNCTION_CALL = "functionCall";
    private static final String MEMBER_FUNCTION_RESPONSE = "functionResponse";
    private static final String MEMBER_RESPONSE = "response";
    private static final String MEMBER_EXECUTABLE_CODE = "executableCode";
    private static final String MEMBER_EXECUTABLE_CODE_SNAKE = "executable_code";
    private static final String MEMBER_WIRE_ID = "gemini:id";
    private static final String MEMBER_OUTPUT = "output";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_DESCRIPTION = "description";
    private static final String MEMBER_PARAMETERS = "parameters";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_FUNCTION = "function";
    private static final int OPTIONAL_UNDECIDED = 0;
    private static final int OPTIONAL_COPY = 1;
    private static final int OPTIONAL_DROP = 2;
    private static final int PENDING_NONE = 0;
    private static final int PENDING_TYPE = 1;
    private static final int PENDING_MAP = 2;
    private static final int PENDING_SCHEMA = 3;
    private static final int PENDING_PLAIN = 4;
    private static final int REQUIRE_NONE = 0;
    private static final int REQUIRE_OBJECT = 1;
    private static final int REQUIRE_STRING = 2;
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_ARGS = "args";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final int CAPTURE_MAX = 768;
    private static final String MEMBER_TOOL_CONFIG_SNAKE = "tool_config";
    private static final String MEMBER_TOOL_CONFIG_NATIVE = "gemini:toolConfig";
    private static final String MEMBER_TOOL_CONFIG_NATIVE_SNAKE = "gemini:tool_config";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_FUNCTION_CALLING_CONFIG = "functionCallingConfig";
    private static final String MEMBER_FUNCTION_CALLING_CONFIG_SNAKE = "function_calling_config";
    private static final String MEMBER_MODE = "mode";
    private static final String MEMBER_ALLOWED_FUNCTION_NAMES = "allowedFunctionNames";
    private static final String MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE = "allowed_function_names";
    private static final String[] CALLING_CONFIG_MEMBERS =
    {
        MEMBER_MODE, MEMBER_ALLOWED_FUNCTION_NAMES, MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE
    };
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_ANY = "ANY";
    private static final String MODE_NONE = "NONE";
    private static final int HEADROOM = 16;
    private static final String[] TYPES = {"STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT", "NULL"};

    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();

    private MutableDirectBufferEx buffer;
    private int origin;
    private int depth;
    private boolean functioned;
    private boolean argumented;
    private int required;
    private int mode;
    private boolean named;
    private int optionalMode;
    private int dataState;
    private String mediaCanonical;
    private String mediaContainer;
    private String mediaData;
    private String mediaUrl;
    private boolean formatted;
    private String mediaType;
    private final LlmCapturedValue captured = new LlmCapturedValue();
    private final LlmToolChoice choice = new LlmToolChoice();
    private int captureKind;
    private String captureKey;
    private boolean separating;
    private byte separator;
    private boolean reference;
    private int pending;
    private int rawDepth;
    private final BitSet names = new BitSet();
    private final BitSet types = new BitSet();
    private String heldId;
    private boolean shaped;

    LlmGeminiBlockWriter(
        LlmOutputSink sink,
        JsonGeneratorEx generator)
    {
        this.sink = sink;
        this.generator = generator;
    }

    void begin(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, MODE_CALL, false);
    }

    void begin(
        MutableDirectBufferEx buffer,
        int origin,
        int mode,
        boolean shaped)
    {
        this.mode = mode;
        this.separating = false;
        this.heldId = null;
        this.shaped = shaped;
        this.buffer = buffer;
        this.origin = origin;
        this.depth = 0;
        this.functioned = false;
        this.argumented = false;
        this.required = REQUIRE_NONE;
        this.named = false;
        this.optionalMode = OPTIONAL_UNDECIDED;
        this.dataState = 0;
        this.pending = PENDING_NONE;
        this.rawDepth = 0;
        this.names.clear();
        this.types.clear();

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());

        if (mode != MODE_VALUE)
        {
            generator.writeStartObject();
        }

        if (mode == MODE_DEFINITION)
        {
            generator.write(MEMBER_TYPE, MEMBER_FUNCTION);
            generator.writeStartObject(MEMBER_FUNCTION);
        }
        else if (mode == MODE_MEDIA)
        {
            generator.write(MEMBER_TYPE, mediaCanonical);
        }
    }

    void beginMember(
        MutableDirectBufferEx buffer,
        int origin,
        char separator)
    {
        begin(buffer, origin, MODE_CONTROL, false);

        this.separator = (byte) separator;
        this.separating = true;
    }

    void beginValue(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, MODE_VALUE, false);
    }

    boolean topLevel()
    {
        return depth == 0;
    }

    void memberKey(
        CharSequence name)
    {
        writePrefixedKey(name);
    }

    void beginCapture(
        MutableDirectBufferEx buffer,
        int origin,
        char separator,
        int kind,
        CharSequence key)
    {
        beginMember(buffer, origin, separator);

        this.captured.clear();
        this.captureKind = kind;
        this.captureKey = key.toString();
    }

    int capture(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        final boolean text = event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING ||
            event == JsonEvent.VALUE_NUMBER;

        if (!(text && parser.deferredBytes()) &&
            captured.add(event, text ? parser.getStringView().toString() : null, CAPTURE_MAX))
        {
            depth += step(event);
            outcome = MORE;

            if (depth == 0)
            {
                convert();
                outcome = VALUE;
            }
        }

        return outcome;
    }

    void endMember()
    {
        flush(false);
    }

    void media(
        String canonicalType,
        String container,
        String dataMember,
        String urlMember,
        boolean formatted,
        String mediaType,
        boolean reference)
    {
        this.mediaCanonical = canonicalType;
        this.mediaContainer = container;
        this.mediaData = dataMember;
        this.mediaUrl = urlMember;
        this.formatted = formatted;
        this.mediaType = mediaType;
        this.reference = reference;
    }

    int pending()
    {
        return generator.length();
    }

    boolean functioned()
    {
        return functioned;
    }

    boolean reserve(
        JsonEvent event,
        JsonParserEx parser)
    {
        int need = HEADROOM;

        if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
        {
            need += PREFIX.length();
        }

        if (generator.remaining() < need)
        {
            flush(false);
        }

        return generator.remaining() >= need;
    }

    int rootAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (mode == MODE_DEFINITION)
        {
            action = definitionAction(name);
        }
        else if (mode == MODE_SERVER_RESULT)
        {
            generator.writeKey(name);
        }
        else if (functionKey(name))
        {
            functioned = true;
            action = ACTION_FUNCTION;
        }
        else
        {
            writePrefixedKey(name);
        }

        return action;
    }

    void enterFunction()
    {
        if (mode == MODE_SERVER_CALL)
        {
            generator.writeKey(MEMBER_ARGUMENTS);
            generator.writeStartObject();
        }
        else if (mode == MODE_MEDIA)
        {
            generator.writeKey(mediaContainer);
            generator.writeStartObject();
        }
    }

    void holdId(
        String id)
    {
        this.heldId = id;
    }

    boolean complete()
    {
        return mode == MODE_SERVER_RESULT || mode == MODE_MEDIA || mode == MODE_DEFINITION && named || functioned;
    }

    int functionAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (mode == MODE_SERVER_CALL)
        {
            action = serverCallAction(name);
        }
        else if (mode == MODE_MEDIA)
        {
            action = mediaAction(name);
        }
        else if (mode == MODE_RESULT)
        {
            action = resultAction(name);
        }
        else if (MEMBER_ARGS.contentEquals(name))
        {
            generator.writeKey(MEMBER_ARGUMENTS);
            argumented = true;
            required = REQUIRE_OBJECT;
            action = ACTION_COPY;
        }
        else if (!MEMBER_NAME.contentEquals(name) && !MEMBER_ID.contentEquals(name))
        {
            writePrefixedKey(name);
            action = ACTION_COPY;
        }

        return action;
    }

    int definitionAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (MEMBER_NAME.contentEquals(name))
        {
            generator.writeKey(MEMBER_NAME);
            named = true;
            required = REQUIRE_STRING;
        }
        else if (MEMBER_DESCRIPTION.contentEquals(name))
        {
            optionalMode = OPTIONAL_UNDECIDED;
            action = ACTION_OPTIONAL;
        }
        else if ("parametersJsonSchema".contentEquals(name) || "parameters_json_schema".contentEquals(name))
        {
            generator.writeKey(MEMBER_PARAMETERS);
            required = REQUIRE_OBJECT;
        }
        else if (MEMBER_PARAMETERS.contentEquals(name))
        {
            generator.writeKey(MEMBER_PARAMETERS);
            required = REQUIRE_OBJECT;
            names.clear();
            types.clear();
            pending = PENDING_NONE;
            rawDepth = 0;
            action = ACTION_SCHEMA;
        }
        else
        {
            writePrefixedKey(name);
        }

        return action;
    }

    int optional(
        JsonEvent event,
        JsonParserEx parser)
    {
        if (optionalMode == OPTIONAL_UNDECIDED)
        {
            if (event == JsonEvent.VALUE_STRING)
            {
                generator.writeKey(MEMBER_DESCRIPTION);
                optionalMode = OPTIONAL_COPY;
            }
            else
            {
                optionalMode = OPTIONAL_DROP;
            }
        }

        return optionalMode == OPTIONAL_COPY ? copyValue(event, parser) : drop(event, parser);
    }

    int schema(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (required == REQUIRE_NONE || event == JsonEvent.START_OBJECT)
        {
            required = REQUIRE_NONE;
            outcome = schemaEvent(event, parser);
        }

        return outcome;
    }

    int mediaAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if ("mimeType".contentEquals(name) || "mime_type".contentEquals(name))
        {
            action = ACTION_DROP;
        }
        else if ("data".contentEquals(name))
        {
            dataState = 0;
            action = ACTION_DATA;
        }
        else if ("fileUri".contentEquals(name) || "file_uri".contentEquals(name))
        {
            generator.writeKey(reference ? "file_id" : mediaUrl);
            required = REQUIRE_STRING;
        }
        else
        {
            writePrefixedKey(name);
        }

        return action;
    }

    int dataValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (dataState != 0 || event == JsonEvent.VALUE_STRING && (formatted || mediaType != null))
        {
            if (dataState == 0)
            {
                generator.writeKey(mediaData);

                if (!formatted)
                {
                    generator.write("data:" + mediaType + ";base64,", Completion.INCOMPLETE);
                }

                dataState = 1;
            }

            copy(event, parser);
            outcome = deferred(event, parser) ? MORE : VALUE;
        }

        return outcome;
    }

    int serverCallAction(
        CharSequence name)
    {
        int action = ACTION_ID;

        if (!MEMBER_ID.contentEquals(name))
        {
            generator.writeKey(name);
            action = ACTION_COPY;
        }

        return action;
    }

    int resultAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_RESPONSE.contentEquals(name))
        {
            if (shaped)
            {
                action = ACTION_DESCEND;
            }
            else
            {
                generator.writeKey(MEMBER_TEXT);
                generator.writeStartEscaped();
                required = REQUIRE_OBJECT;
                action = ACTION_ESCAPE;
            }
        }
        else if (!MEMBER_NAME.contentEquals(name) && !MEMBER_ID.contentEquals(name))
        {
            writePrefixedKey(name);
            action = ACTION_COPY;
        }

        return action;
    }

    int responseAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (MEMBER_OUTPUT.contentEquals(name) || MEMBER_ERROR.contentEquals(name))
        {
            generator.writeKey(MEMBER_TEXT);
            required = REQUIRE_STRING;
            action = ACTION_TEXT;
        }
        else
        {
            writePrefixedKey(name);
        }

        return action;
    }

    int escape(
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = copyValue(event, parser);

        if (outcome == VALUE)
        {
            generator.writeEndEscaped();
        }

        return outcome;
    }

    void closeFunction()
    {
        if (mode == MODE_SERVER_CALL)
        {
            generator.writeEnd();

            if (heldId != null)
            {
                generator.write(MEMBER_WIRE_ID, heldId);
            }
        }
        else if (mode == MODE_MEDIA)
        {
            final String format = formatted ? LlmGeminiMediaFormat.formatOf(mediaType) : null;

            if (format != null)
            {
                generator.write("format", format);
            }

            generator.writeEnd();
        }
        else if (mode == MODE_CALL && !argumented)
        {
            generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            argumented = true;
        }
    }

    private boolean functionKey(
        CharSequence name)
    {
        return switch (mode)
        {
        case MODE_CALL -> MEMBER_FUNCTION_CALL.contentEquals(name);
        case MODE_RESULT -> MEMBER_FUNCTION_RESPONSE.contentEquals(name);
        case MODE_MEDIA -> "inlineData".contentEquals(name) || "inline_data".contentEquals(name) ||
            "fileData".contentEquals(name) || "file_data".contentEquals(name);
        default -> MEMBER_EXECUTABLE_CODE.contentEquals(name) || MEMBER_EXECUTABLE_CODE_SNAKE.contentEquals(name);
        };
    }

    int drop(
        JsonEvent event,
        JsonParserEx parser)
    {
        depth += step(event);

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    int copyValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (required == REQUIRE_NONE || required == REQUIRE_OBJECT && event == JsonEvent.START_OBJECT ||
            required == REQUIRE_STRING && event == JsonEvent.VALUE_STRING)
        {
            required = REQUIRE_NONE;
            copy(event, parser);
            outcome = depth == 0 && !deferred(event, parser) ? VALUE : MORE;
        }

        return outcome;
    }

    void finish()
    {
        if (mode == MODE_DEFINITION)
        {
            generator.writeEnd();
        }

        generator.writeEnd();
        flush(true);
    }

    void flush(
        boolean last)
    {
        final int length = generator.length();

        if (length > 0 || last)
        {
            if (separating && length > 0)
            {
                buffer.putByte(origin, separator);
                separating = false;
            }

            sink.put(buffer, origin, length, last);
            generator.wrap(buffer, origin, buffer.capacity());
        }
    }

    private int schemaEvent(
        JsonEvent event,
        JsonParserEx parser)
    {
        final int level = depth;

        if (rawDepth > 0)
        {
            rawDepth += step(event);
            copy(event, parser);
        }
        else if (event == JsonEvent.KEY_NAME)
        {
            schemaKey(level, parser.getStringView());
            copy(event, parser);
        }
        else if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
        {
            copy(event, parser);

            if (pending == PENDING_PLAIN)
            {
                rawDepth = 1;
            }
            else
            {
                names.set(depth, pending == PENDING_MAP);
                types.set(depth, pending == PENDING_TYPE);
            }

            pending = PENDING_NONE;
        }
        else if (event == JsonEvent.VALUE_STRING && (pending == PENDING_TYPE || types.get(level)))
        {
            final String lower = lowerType(parser.getStringView());

            if (lower != null && !parser.deferredBytes())
            {
                generator.write(lower);
            }
            else
            {
                copy(event, parser);
            }

            pending = PENDING_NONE;
        }
        else
        {
            copy(event, parser);
            pending = PENDING_NONE;
        }

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    private void schemaKey(
        int level,
        JsonStringView name)
    {
        if (names.get(level))
        {
            pending = PENDING_SCHEMA;
        }
        else if (MEMBER_TYPE.contentEquals(name))
        {
            pending = PENDING_TYPE;
        }
        else if ("properties".contentEquals(name) || "patternProperties".contentEquals(name) ||
            "$defs".contentEquals(name) || "definitions".contentEquals(name))
        {
            pending = PENDING_MAP;
        }
        else if ("items".contentEquals(name) || "additionalProperties".contentEquals(name) ||
            "anyOf".contentEquals(name) || "oneOf".contentEquals(name) || "allOf".contentEquals(name))
        {
            pending = PENDING_SCHEMA;
        }
        else
        {
            pending = PENDING_PLAIN;
        }
    }

    private static String lowerType(
        CharSequence value)
    {
        String lower = null;

        for (String type : TYPES)
        {
            if (type.contentEquals(value))
            {
                lower = type.toLowerCase(Locale.ROOT);
            }
        }

        return lower;
    }

    private void convert()
    {
        if (captureKind == CAPTURE_SERVICE_TIER)
        {
            final String tier = LlmServiceTiers.canonicalGemini(captured.string(0));

            if (tier != null)
            {
                generator.write(MEMBER_SHARED_SERVICE_TIER, tier);
            }
            else
            {
                writePrefixedKey(captureKey);
                writeCaptured(0);
            }
        }
        else
        {
            convertToolConfig();
        }
    }

    private void convertToolConfig()
    {
        final String residueKey = MEMBER_TOOL_CONFIG_SNAKE.equals(captureKey)
            ? MEMBER_TOOL_CONFIG_NATIVE_SNAKE
            : MEMBER_TOOL_CONFIG_NATIVE;

        if (captured.isObject(0))
        {
            int config = captured.member(0, MEMBER_FUNCTION_CALLING_CONFIG);

            if (config == LlmCapturedValue.ABSENT)
            {
                config = captured.member(0, MEMBER_FUNCTION_CALLING_CONFIG_SNAKE);
            }

            final boolean lifted = config != LlmCapturedValue.ABSENT && parseCallingConfig(config);

            if (lifted)
            {
                generator.writeKey(MEMBER_SHARED_TOOL_CHOICE);
                choice.emitCanonical(generator);
            }

            writeResidue(residueKey, lifted ? config : LlmCapturedValue.ABSENT);
        }
        else
        {
            generator.writeKey(residueKey);
            writeCaptured(0);
        }
    }

    private void writeResidue(
        String residueKey,
        int liftedAt)
    {
        boolean residue = false;
        int index = 1;

        while (captured.event(index) == JsonEvent.KEY_NAME)
        {
            if (index + 1 != liftedAt)
            {
                if (!residue)
                {
                    residue = true;
                    generator.writeStartObject(residueKey);
                }

                generator.writeKey(captured.text(index));
                writeCaptured(index + 1);
            }

            index = captured.end(index + 1);
        }

        if (residue)
        {
            generator.writeEnd();
        }
        else if (liftedAt == LlmCapturedValue.ABSENT)
        {
            generator.writeStartObject(residueKey).writeEnd();
        }
    }

    private void writeCaptured(
        int index)
    {
        final int end = captured.end(index);

        for (int at = index; at < end; at++)
        {
            final String text = captured.text(at);

            switch (captured.event(at))
            {
            case START_OBJECT:
                generator.writeStartObject();
                break;
            case START_ARRAY:
                generator.writeStartArray();
                break;
            case END_OBJECT, END_ARRAY:
                generator.writeEnd();
                break;
            case KEY_NAME:
                generator.writeKey(text);
                break;
            case VALUE_NUMBER:
                generator.writeNumber(text, Completion.COMPLETE);
                break;
            case VALUE_TRUE:
                generator.write(true);
                break;
            case VALUE_FALSE:
                generator.write(false);
                break;
            case VALUE_NULL:
                generator.writeNull();
                break;
            default:
                generator.write(text);
                break;
            }
        }
    }

    private boolean parseCallingConfig(
        int config)
    {
        choice.clear();

        if (captured.onlyMembers(config, CALLING_CONFIG_MEMBERS))
        {
            int names = captured.member(config, MEMBER_ALLOWED_FUNCTION_NAMES);

            if (names == LlmCapturedValue.ABSENT)
            {
                names = captured.member(config, MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE);
            }

            if (names == LlmCapturedValue.ABSENT || readNames(names))
            {
                chooseMode(captured.string(captured.member(config, MEMBER_MODE)));
            }
        }

        return choice.kind() != LlmToolChoice.UNRECOGNIZED;
    }

    private boolean readNames(
        int names)
    {
        boolean valid = captured.isArray(names);
        int index = names + 1;

        while (valid && captured.event(index) != JsonEvent.END_ARRAY)
        {
            final String entry = captured.string(index);

            valid = entry != null;

            if (valid)
            {
                choice.names().add(entry);
            }

            index++;
        }

        return valid;
    }

    private void chooseMode(
        String mode)
    {
        final int count = choice.names().size();

        if (MODE_AUTO.equals(mode))
        {
            if (count == 0)
            {
                choice.mode(LlmToolChoice.MODE_AUTO);
            }
            else
            {
                choice.allowed(LlmToolChoice.MODE_AUTO);
            }
        }
        else if (MODE_ANY.equals(mode))
        {
            if (count == 0)
            {
                choice.mode(LlmToolChoice.MODE_REQUIRED);
            }
            else if (count == 1)
            {
                choice.function(choice.names().get(0));
                choice.names().clear();
            }
            else
            {
                choice.allowed(LlmToolChoice.MODE_REQUIRED);
            }
        }
        else if (MODE_NONE.equals(mode) && count == 0)
        {
            choice.mode(LlmToolChoice.MODE_NONE);
        }
    }

    private void writePrefixedKey(
        CharSequence name)
    {
        key.setLength(0);
        key.append(PREFIX).append(name);
        generator.writeKey(key);
    }

    private void copy(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT:
            generator.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            break;
        case END_OBJECT, END_ARRAY:
            generator.writeEnd();
            depth--;
            break;
        case VALUE_TRUE:
            generator.write(true);
            break;
        case VALUE_FALSE:
            generator.write(false);
            break;
        case VALUE_NULL:
            generator.writeNull();
            break;
        default:
            copyText(event, parser.getStringView(), parser.deferredBytes());
            break;
        }
    }

    private void copyText(
        JsonEvent event,
        JsonStringView chars,
        boolean deferred)
    {
        final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        final int length = chars.length();

        int taken = 0;

        do
        {
            final CharSequence rest = taken == 0 ? chars : chars.subSequence(taken, length);
            final int before = generator.consumed();

            if (event == JsonEvent.KEY_NAME)
            {
                generator.writeKey(rest, completion);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                generator.writeNumber(rest, completion);
            }
            else
            {
                generator.write(rest, completion);
            }

            final int advanced = generator.consumed() - before;

            taken += advanced;

            if (taken < length)
            {
                if (advanced == 0 && generator.length() == 0)
                {
                    throw new JsonException("insufficient room to write the next character");
                }

                flush(false);
            }
        }
        while (taken < length);
    }

    private static int step(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT, START_ARRAY -> 1;
        case END_OBJECT, END_ARRAY -> -1;
        default -> 0;
        };
    }

    private static boolean deferred(
        JsonEvent event,
        JsonParserEx parser)
    {
        return event == JsonEvent.VALUE_STRING && parser.deferredBytes();
    }
}
