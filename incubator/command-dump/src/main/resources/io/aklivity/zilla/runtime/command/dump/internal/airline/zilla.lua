--[[

    Copyright 2021-2024 Aklivity Inc

    Licensed under the Aklivity Community License (the "License"); you may not use
    this file except in compliance with the License.  You may obtain a copy of the
    License at

      https://www.aklivity.io/aklivity-community-license/

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OF ANY KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations under the License.

]]
local zilla_version = "@version@"
if zilla_version == string.format("@%s@", "version") or zilla_version == "develop-SNAPSHOT" then
    zilla_version = "dev"
end

local zilla_info = {
    version = zilla_version,
    author = "Aklivity, Inc.",
    repository = "https://github.com/aklivity/zilla",
    description = "Dissector for the internal protocol of Zilla"
}
set_plugin_info(zilla_info)

local zilla_protocol = Proto("Zilla", "Zilla Frames")

HEADER_OFFSET = 0
LABELS_OFFSET = 16

BEGIN_ID = 0x00000001
DATA_ID = 0x00000002
END_ID = 0x00000003
ABORT_ID = 0x00000004
FLUSH_ID = 0x00000005
RESET_ID = 0x40000001
WINDOW_ID = 0x40000002
SIGNAL_ID = 0x40000003
CHALLENGE_ID = 0x40000004
REDIRECT_ID = 0x40000005

local flags_types = {
    [0] = "Not set",
    [1] = "Set"
}

-- dissector registry
local dissector_types = {}
local dissector_ext_handlers = {}
local dissector_payloads = {}

local fields = {}

function add_field(key, field)
    fields[key] = field
end

function register_dissector(type_id, name, ext_handler, payload_resolver)
    dissector_types[type_id] = name
    if ext_handler then
        dissector_ext_handlers[type_id] = ext_handler
    end
    if payload_resolver then
        dissector_payloads[name] = payload_resolver
    end
end

-- core frame fields
-- header
add_field("frame_type_id", ProtoField.uint32("zilla.frame_type_id", "Frame Type ID", base.HEX))
add_field("frame_type", ProtoField.string("zilla.frame_type", "Frame Type", base.NONE))
add_field("protocol_type_id", ProtoField.uint32("zilla.protocol_type_id", "Protocol Type ID", base.HEX))
add_field("protocol_type", ProtoField.string("zilla.protocol_type", "Protocol Type", base.NONE))
add_field("stream_type_id", ProtoField.uint32("zilla.stream_type_id", "Stream Type ID", base.HEX))
add_field("stream_type", ProtoField.string("zilla.stream_type", "Stream Type", base.NONE))
add_field("worker", ProtoField.int32("zilla.worker", "Worker", base.DEC))
add_field("offset", ProtoField.uint32("zilla.offset", "Offset", base.HEX))

-- labels
add_field("origin_namespace", ProtoField.string("zilla.origin_namespace", "Origin Namespace", base.STRING))
add_field("origin_binding", ProtoField.string("zilla.origin_binding", "Origin Binding", base.STRING))
add_field("routed_namespace", ProtoField.string("zilla.routed_namespace", "Routed Namespace", base.STRING))
add_field("routed_binding", ProtoField.string("zilla.routed_binding", "Routed Binding", base.STRING))

-- all frames
add_field("origin_id", ProtoField.uint64("zilla.origin_id", "Origin ID", base.HEX))
add_field("routed_id", ProtoField.uint64("zilla.routed_id", "Routed ID", base.HEX))
add_field("stream_id", ProtoField.uint64("zilla.stream_id", "Stream ID", base.HEX))
add_field("direction", ProtoField.string("zilla.direction", "Direction", base.NONE))
add_field("initial_id", ProtoField.uint64("zilla.initial_id", "Initial ID", base.HEX))
add_field("reply_id", ProtoField.uint64("zilla.reply_id", "Reply ID", base.HEX))
add_field("sequence", ProtoField.int64("zilla.sequence", "Sequence", base.DEC))
add_field("acknowledge", ProtoField.int64("zilla.acknowledge", "Acknowledge", base.DEC))
add_field("maximum", ProtoField.int32("zilla.maximum", "Maximum", base.DEC))
add_field("timestamp", ProtoField.uint64("zilla.timestamp", "Timestamp", base.HEX))
add_field("trace_id", ProtoField.uint64("zilla.trace_id", "Trace ID", base.HEX))
add_field("authorization", ProtoField.uint64("zilla.authorization", "Authorization", base.HEX))

-- begin frame
add_field("affinity", ProtoField.uint64("zilla.affinity", "Affinity", base.HEX))

-- data frame
add_field("flags", ProtoField.uint8("zilla.flags", "Flags", base.HEX))
add_field("flags_fin", ProtoField.uint8("zilla.flags_fin", "FIN", base.DEC, flags_types, 0x01))
add_field("flags_init", ProtoField.uint8("zilla.flags_init", "INIT", base.DEC, flags_types, 0x02))
add_field("flags_incomplete", ProtoField.uint8("zilla.flags_incomplete", "INCOMPLETE", base.DEC, flags_types, 0x04))
add_field("flags_skip", ProtoField.uint8("zilla.flags_skip", "SKIP", base.DEC, flags_types, 0x08))
add_field("budget_id", ProtoField.uint64("zilla.budget_id", "Budget ID", base.HEX))
add_field("reserved", ProtoField.int32("zilla.reserved", "Reserved", base.DEC))
add_field("payload_length", ProtoField.int32("zilla.payload_length", "Length", base.DEC))
add_field("progress", ProtoField.int64("zilla.progress", "Progress", base.DEC))
add_field("progress_maximum", ProtoField.string("zilla.progress_maximum", "Progress/Maximum", base.NONE))
add_field("payload", ProtoField.protocol("zilla.payload", "Payload", base.HEX))

-- window frame
add_field("padding", ProtoField.int32("zilla.padding", "Padding", base.DEC))
add_field("minimum", ProtoField.int32("zilla.minimum", "Minimum", base.DEC))
add_field("capabilities", ProtoField.uint8("zilla.capabilities", "Capabilities", base.HEX))

-- signal frame
add_field("cancel_id", ProtoField.uint64("zilla.cancel_id", "Cancel ID", base.HEX))
add_field("signal_id", ProtoField.uint32("zilla.signal_id", "Signal ID", base.HEX))
add_field("context_id", ProtoField.uint32("zilla.context_id", "Context ID", base.HEX))

function resolve_frame_type(frame_type_id)
    local frame_type = ""
        if frame_type_id == BEGIN_ID     then frame_type = "BEGIN"
    elseif frame_type_id == DATA_ID      then frame_type = "DATA"
    elseif frame_type_id == END_ID       then frame_type = "END"
    elseif frame_type_id == ABORT_ID     then frame_type = "ABORT"
    elseif frame_type_id == FLUSH_ID     then frame_type = "FLUSH"
    elseif frame_type_id == RESET_ID     then frame_type = "RESET"
    elseif frame_type_id == WINDOW_ID    then frame_type = "WINDOW"
    elseif frame_type_id == SIGNAL_ID    then frame_type = "SIGNAL"
    elseif frame_type_id == CHALLENGE_ID then frame_type = "CHALLENGE"
    elseif frame_type_id == REDIRECT_ID  then frame_type = "REDIRECT"
    end
    return frame_type
end

function resolve_type(type_id)
    return dissector_types[type_id] or ""
end

function resolve_dissector(protocol_type, payload)
    local resolver = dissector_payloads[protocol_type]
    local dissector
    if resolver then
        dissector = resolver(payload)
    end
    return dissector
end

function handle_extension(buffer, subtree, pinfo, info, offset, frame_type_id)
    if buffer:len() > offset then
        local slice_stream_type_id = buffer(offset, 4)
        local stream_type_id = slice_stream_type_id:le_uint();
        local stream_type = resolve_type(stream_type_id)
        local extension_label = string.format("Extension: %s", stream_type)
        local slice_extension = buffer(offset)
        local ext_subtree = subtree:add(zilla_protocol, slice_extension, extension_label)
        ext_subtree:add(fields.stream_type_id, slice_stream_type_id)
        ext_subtree:add(fields.stream_type, stream_type)

        local extension_offset = offset + 8 + 4;
        local ext_handler = dissector_ext_handlers[stream_type_id]
        if ext_handler then
            ext_handler(buffer, extension_offset, ext_subtree, frame_type_id)
        end

        if stream_type and stream_type ~= "" then
            pinfo.cols.info:set(string.format("%s s=%s", info, stream_type))
        end
    end
end

function handle_begin_frame(buffer, offset, subtree, pinfo, info)
    local slice_affinity = buffer(offset, 8)
    subtree:add_le(fields.affinity, slice_affinity)
    handle_extension(buffer, subtree, pinfo, info, offset + 8, BEGIN_ID)
end

function handle_redirect_frame(buffer, offset, subtree, pinfo, info)
    local slice_affinity = buffer(offset, 8)
    subtree:add_le(fields.affinity, slice_affinity)
    handle_extension(buffer, subtree, pinfo, info, offset + 8, REDIRECT_ID)
end

function handle_data_frame(buffer, offset, tree, subtree, sequence, acknowledge, maximum, pinfo, info, protocol_type)
    local slice_flags = buffer(offset, 1)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.flags_fin, slice_flags)
    flags_subtree:add_le(fields.flags_init, slice_flags)
    flags_subtree:add_le(fields.flags_incomplete, slice_flags)
    flags_subtree:add_le(fields.flags_skip, slice_flags)

    local slice_budget_id = buffer(offset + 1, 8)
    local slice_reserved = buffer(offset + 9, 4)
    local reserved = slice_reserved:le_int();
    local progress = sequence - acknowledge + reserved;
    local progress_maximum = string.format("%s/%s", progress, maximum)
    subtree:add_le(fields.budget_id, slice_budget_id)
    subtree:add_le(fields.reserved, slice_reserved)
    subtree:add(fields.progress, progress)
    subtree:add(fields.progress_maximum, progress_maximum)
    pinfo.cols.info:set(string.format("%s [%s]", info, progress_maximum))

    local slice_payload_length = buffer(offset + 13, 4)
    local payload_length = math.max(slice_payload_length:le_int(), 0)
    local slice_payload = buffer(offset + 17, payload_length)
    local payload_subtree = subtree:add(zilla_protocol, buffer(offset + 13, 4 + payload_length), "Payload")
    payload_subtree:add_le(fields.payload_length, slice_payload_length)
    if (payload_length > 0) then
        payload_subtree:add(fields.payload, slice_payload)
    end

    handle_extension(buffer, subtree, pinfo, info, offset + 17 + payload_length, DATA_ID)

    local dissector = resolve_dissector(protocol_type, slice_payload:tvb())
    if dissector then
        dissector:call(slice_payload:tvb(), pinfo, tree)
    end
end

function handle_flush_frame(buffer, offset, subtree, pinfo, info)
    local slice_budget_id = buffer(offset, 8)
    local slice_reserved = buffer(offset + 8, 4)
    subtree:add_le(fields.budget_id, slice_budget_id)
    subtree:add_le(fields.reserved, slice_reserved)
    handle_extension(buffer, subtree, pinfo, info, offset + 12, FLUSH_ID)
end

function handle_end_frame(buffer, offset, subtree, pinfo, info)
    local slice_budget_id = buffer(offset, 8)
    local slice_reserved = buffer(offset + 8, 4)
    subtree:add_le(fields.budget_id, slice_budget_id)
    subtree:add_le(fields.reserved, slice_reserved)
    handle_extension(buffer, subtree, pinfo, info, offset + 12, END_ID)
end

function handle_window_frame(buffer, offset, subtree, sequence, acknowledge, maximum, pinfo, info)
    local slice_budget_id = buffer(offset, 8)
    local slice_padding = buffer(offset + 8, 4)
    local slice_minimum = buffer(offset + 12, 4)
    local slice_capabilities = buffer(offset + 16, 1)
    subtree:add_le(fields.budget_id, slice_budget_id)
    subtree:add_le(fields.padding, slice_padding)
    subtree:add_le(fields.minimum, slice_minimum)
    subtree:add_le(fields.capabilities, slice_capabilities)
    local progress = sequence - acknowledge;
    local progress_maximum = string.format("%s/%s", progress, maximum)
    subtree:add(fields.progress, progress)
    subtree:add(fields.progress_maximum, progress_maximum)
    pinfo.cols.info:set(string.format("%s [%s]", info, progress_maximum))
end

function handle_signal_frame(buffer, offset, subtree, pinfo, info)
    local slice_cancel_id = buffer(offset, 8)
    local slice_signal_id = buffer(offset + 8, 4)
    local slice_context_id = buffer(offset + 12, 4)
    subtree:add_le(fields.cancel_id, slice_cancel_id)
    subtree:add_le(fields.signal_id, slice_signal_id)
    subtree:add_le(fields.context_id, slice_context_id)
    -- payload
    local slice_payload_length = buffer(offset + 16, 4)
    local payload_length = math.max(slice_payload_length:le_int(), 0)
    local slice_payload = buffer(offset + 20, payload_length)
    local payload_subtree = subtree:add(zilla_protocol, slice_payload, "Payload")
    payload_subtree:add_le(fields.payload_length, slice_payload_length)
    if (payload_length > 0) then
        payload_subtree:add(fields.payload, slice_payload)
    end
end

-- shared parsing helpers
function dissect_length_value(buffer, offset, length_length)
    local slice_length = buffer(offset, length_length)
    local length = math.max(slice_length:le_int(), 0)
    local slice_value = buffer(offset + length_length, length)
    local item_length = length + length_length
    return item_length, slice_length, slice_value
end

function add_string_as_subtree(buffer, tree, label_format, slice_length, slice_text, field_length, field_text)
    local text = slice_text:string()
    local label = string.format(label_format, text)
    local subtree = tree:add(zilla_protocol, buffer, label)
    subtree:add_le(field_length, slice_length)
    subtree:add(field_text, slice_text)
end

function add_varint_as_subtree(buffer, tree, label_format, slice, value, field_varint, field_value)
    local label = string.format(label_format, value)
    local subtree = tree:add(zilla_protocol, buffer, label)
    subtree:add_le(field_varint, slice)
    subtree:add(field_value, value)
end

function decode_varint32(buffer, offset)
    local value = 0
    local i = 0
    local pos = offset
    local b = buffer(pos, 1):le_int()

    while bit.band(b, 0x80) ~= 0 do
        value = bit.bor(value, bit.lshift(bit.band(b, 0x7F), i))
        i = i + 7
        if i > 35 then
            error("varint32 value too long")
        end
        pos = pos + 1
        b = buffer(pos, 1):le_int()
    end

    local unsigned = bit.bor(value, bit.lshift(b, i))
    local result = bit.rshift(bit.bxor(bit.rshift(bit.lshift(unsigned, 31), 31), unsigned), 1)
    result = bit.bxor(result, bit.band(unsigned, bit.lshift(1, 31)))
    local length = pos - offset + 1
    return result, buffer(offset, length), length
end

function decode_varuint32(buffer, offset)
    local max_length = 5
    local limit = math.min(buffer:len(), offset + max_length)
    local value = 0
    local progress = offset

    if progress < limit then
        local shift = 0
        local bits
        repeat
            bits = buffer(progress, 1):uint()
            value = bit.bor(value, bit.lshift(bit.band(bits, 0x7F), shift))
            shift = shift + 7
            progress = progress + 1
        until progress >= limit or bit.band(bits, 0x80) == 0
    end

    local length = progress - offset
    return value, buffer(offset, length), length
end

function dissect_and_add_array_header_as_subtree(buffer, offset, tree, label_format, field_array_length, field_array_size)
    local slice_array_length = buffer(offset, 4)
    local slice_array_size = buffer(offset + 4, 4)
    local header_length = 4 + 4
    local array_size = slice_array_size:le_int()
    local label = string.format(label_format, array_size)
    local array_subtree = tree:add(zilla_protocol, buffer(offset, header_length), label)
    array_subtree:add_le(field_array_length, slice_array_length)
    array_subtree:add_le(field_array_size, slice_array_size)
    return header_length, array_size
end

function resolve_length_of_array(buffer, offset)
    local slice_array_length = buffer(offset, 4)
    return 4 + slice_array_length:le_int()
end

function field_exists(list_fields, position)
    return bit.band(tostring(list_fields), bit.lshift(1, position)) > 0
end

-- @dissectors@
zilla_protocol.fields = fields;

function zilla_protocol.dissector(buffer, pinfo, tree)
    if buffer:len() == 0 then return end
    local subtree = tree:add(zilla_protocol, buffer(), "Zilla Frame")

    -- header
    local slice_frame_type_id = buffer(HEADER_OFFSET, 4)
    local frame_type_id = slice_frame_type_id:le_uint()
    local frame_type = resolve_frame_type(frame_type_id)
    subtree:add_le(fields.frame_type_id, slice_frame_type_id)
    subtree:add(fields.frame_type, frame_type)

    local slice_protocol_type_id = buffer(HEADER_OFFSET + 4, 4)
    local protocol_type_id = slice_protocol_type_id:le_uint()
    local protocol_type = resolve_type(protocol_type_id)
    subtree:add_le(fields.protocol_type_id, slice_protocol_type_id)
    subtree:add(fields.protocol_type, protocol_type)

    local slice_worker = buffer(HEADER_OFFSET + 8, 4)
    local slice_offset = buffer(HEADER_OFFSET + 12, 4)
    subtree:add_le(fields.worker, slice_worker)
    subtree:add_le(fields.offset, slice_offset)

    -- labels
    local slice_labels_length = buffer(LABELS_OFFSET, 4)
    local labels_length = slice_labels_length:le_uint()

    -- origin id
    local frame_offset = LABELS_OFFSET + labels_length
    local slice_origin_id = buffer(frame_offset + 4, 8)
    subtree:add_le(fields.origin_id, slice_origin_id)

    local label_offset = LABELS_OFFSET + 4;
    local origin_namespace_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    local slice_origin_namespace = buffer(label_offset, origin_namespace_length)
    label_offset = label_offset + origin_namespace_length
    if (origin_namespace_length > 0) then
        subtree:add(fields.origin_namespace, slice_origin_namespace)
    end

    local origin_binding_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    local slice_origin_binding = buffer(label_offset, origin_binding_length)
    label_offset = label_offset + origin_binding_length
    if (origin_binding_length > 0) then
        subtree:add(fields.origin_binding, slice_origin_binding)
    end

    -- routed id
    local slice_routed_id = buffer(frame_offset + 12, 8)
    subtree:add_le(fields.routed_id, slice_routed_id)

    local routed_namespace_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    slice_routed_namespace = buffer(label_offset, routed_namespace_length)
    label_offset = label_offset + routed_namespace_length
    if (routed_namespace_length > 0) then
        subtree:add(fields.routed_namespace, slice_routed_namespace)
    end

    local routed_binding_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    local slice_routed_binding = buffer(label_offset, routed_binding_length)
    label_offset = label_offset + routed_binding_length
    if (routed_binding_length > 0) then
        subtree:add(fields.routed_binding, slice_routed_binding)
    end

    -- stream id
    local slice_stream_id = buffer(frame_offset + 20, 8)
    local stream_id = slice_stream_id:le_uint64();
    subtree:add_le(fields.stream_id, slice_stream_id)
    local direction
    local initial_id
    local reply_id
    if stream_id == UInt64(0) then
        direction = ""
    else
        if (stream_id % 2) == UInt64(0) then
            direction = "REP"
            initial_id = stream_id + UInt64(1)
            reply_id = stream_id
        else
            direction = "INI"
            initial_id = stream_id
            reply_id = stream_id - UInt64(1)
        end
        subtree:add(fields.initial_id, initial_id)
        subtree:add(fields.reply_id, reply_id)
    end
    subtree:add(fields.direction, direction)

    -- more frame properties
    local slice_sequence = buffer(frame_offset + 28, 8)
    local sequence = slice_sequence:le_int64();
    local slice_acknowledge = buffer(frame_offset + 36, 8)
    local acknowledge = slice_acknowledge:le_int64();
    local slice_maximum = buffer(frame_offset + 44, 4)
    local maximum = slice_maximum:le_int();
    local slice_timestamp = buffer(frame_offset + 48, 8)
    local slice_trace_id = buffer(frame_offset + 56, 8)
    local slice_authorization = buffer(frame_offset + 64, 8)
    subtree:add_le(fields.sequence, slice_sequence)
    subtree:add_le(fields.acknowledge, slice_acknowledge)
    subtree:add_le(fields.maximum, slice_maximum)
    subtree:add_le(fields.timestamp, slice_timestamp)
    subtree:add_le(fields.trace_id, slice_trace_id)
    subtree:add_le(fields.authorization, slice_authorization)

    pinfo.cols.protocol = zilla_protocol.name
    local info = string.format("ZILLA %s %s", frame_type, direction)
    if protocol_type and protocol_type ~= "" then
        info = string.format("%s p=%s", info, protocol_type)
    end
    pinfo.cols.info:set(info)

    local next_offset = frame_offset + 72
    if frame_type_id == BEGIN_ID then
        handle_begin_frame(buffer, next_offset, subtree, pinfo, info)
    elseif frame_type_id == DATA_ID then
        handle_data_frame(buffer, next_offset, tree, subtree, sequence, acknowledge, maximum, pinfo, info, protocol_type)
    elseif frame_type_id == FLUSH_ID then
        handle_flush_frame(buffer, next_offset, subtree, pinfo, info)
    elseif frame_type_id == END_ID then
        handle_end_frame(buffer, next_offset, subtree, pinfo, info)
    elseif frame_type_id == WINDOW_ID then
        handle_window_frame(buffer, next_offset, subtree, sequence, acknowledge, maximum, pinfo, info)
    elseif frame_type_id == SIGNAL_ID then
        handle_signal_frame(buffer, next_offset, subtree, pinfo, info)
    elseif frame_type_id == ABORT_ID or frame_type_id == RESET_ID or frame_type_id == CHALLENGE_ID then
        handle_extension(buffer, subtree, pinfo, info, next_offset, frame_type_id)
    elseif frame_type_id == REDIRECT_ID then
        handle_redirect_frame(buffer, next_offset, subtree, pinfo, info)
    end
end

local data_dissector = DissectorTable.get("tcp.port")
data_dissector:add(7114, zilla_protocol)
