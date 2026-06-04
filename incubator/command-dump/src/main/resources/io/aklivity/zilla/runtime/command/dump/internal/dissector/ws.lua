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
-- ws dissector
WS_ID = 0x569dcde9

add_field("ws_ext_protocol_length", ProtoField.int8("zilla.ws_ext.protocol_length", "Length", base.DEC))
add_field("ws_ext_protocol", ProtoField.string("zilla.ws_ext.protocol", "Protocol", base.NONE))
add_field("ws_ext_scheme_length", ProtoField.int8("zilla.ws_ext.scheme_length", "Length", base.DEC))
add_field("ws_ext_scheme", ProtoField.string("zilla.ws_ext.scheme", "Scheme", base.NONE))
add_field("ws_ext_authority_length", ProtoField.int8("zilla.ws_ext.authority_length", "Length", base.DEC))
add_field("ws_ext_authority", ProtoField.string("zilla.ws_ext.authority", "Authority", base.NONE))
add_field("ws_ext_path_length", ProtoField.int8("zilla.ws_ext.path_length", "Length", base.DEC))
add_field("ws_ext_path", ProtoField.string("zilla.ws_ext.path", "Path", base.NONE))
add_field("ws_ext_flags", ProtoField.uint8("zilla.ws_ext.flags", "Flags", base.HEX))
add_field("ws_ext_info", ProtoField.bytes("zilla.ws_ext.info", "Info", base.NONE))
add_field("ws_ext_code", ProtoField.int16("zilla.ws_ext.code", "Code", base.DEC))
add_field("ws_ext_reason_length", ProtoField.int8("zilla.ws_ext.reason_length", "Length", base.DEC))
add_field("ws_ext_reason", ProtoField.string("zilla.ws_ext.reason", "Reason", base.NONE))

function handle_ws_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == REDIRECT_ID then
        handle_ws_begin_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == DATA_ID then
        handle_ws_data_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == END_ID then
        handle_ws_end_extension(buffer, offset, ext_subtree)
    end
end

function handle_ws_begin_extension(buffer, offset, ext_subtree)
    -- protocol
    local protocol_offset = offset
    local protocol_length, slice_protocol_length, slice_protocol_text = dissect_length_value(buffer, protocol_offset, 1)
    add_string_as_subtree(buffer(protocol_offset, protocol_length), ext_subtree, "Protocol: %s",
        slice_protocol_length, slice_protocol_text, fields.ws_ext_protocol_length, fields.ws_ext_protocol)
    -- scheme
    local scheme_offset = protocol_offset + protocol_length
    local scheme_length, slice_scheme_length, slice_scheme_text = dissect_length_value(buffer, scheme_offset, 1)
    add_string_as_subtree(buffer(scheme_offset, scheme_length), ext_subtree, "Scheme: %s",
        slice_scheme_length, slice_scheme_text, fields.ws_ext_scheme_length, fields.ws_ext_scheme)
    -- authority
    local authority_offset = scheme_offset + scheme_length
    local authority_length, slice_authority_length, slice_authority_text = dissect_length_value(buffer, authority_offset, 1)
    add_string_as_subtree(buffer(authority_offset, authority_length), ext_subtree, "Authority: %s",
        slice_authority_length, slice_authority_text, fields.ws_ext_authority_length, fields.ws_ext_authority)
    -- path
    local path_offset = authority_offset + authority_length
    local path_length, slice_path_length, slice_path_text = dissect_length_value(buffer, path_offset, 1)
    add_string_as_subtree(buffer(path_offset, path_length), ext_subtree, "Path: %s",
        slice_path_length, slice_path_text, fields.ws_ext_path_length, fields.ws_ext_path)
end

function handle_ws_data_extension(buffer, offset, ext_subtree)
    -- flags
    local flags_offset = offset
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    ext_subtree:add(fields.ws_ext_flags, slice_flags)
    -- info
    local info_offset = flags_offset + flags_length
    if (info_offset < buffer:len()) then
        ext_subtree:add(fields.ws_ext_info, buffer(info_offset))
    end
end

function handle_ws_end_extension(buffer, offset, ext_subtree)
    -- code
    local code_offset = offset
    local code_length = 2
    local slice_code = buffer(code_offset, code_length)
    ext_subtree:add_le(fields.ws_ext_code, slice_code)
    -- reason
    local reason_offset = code_offset + code_length
    local reason_length, slice_reason_length, slice_reason_text = dissect_length_value(buffer, reason_offset, 1)
    add_string_as_subtree(buffer(reason_offset, reason_length), ext_subtree, "Reason: %s",
        slice_reason_length, slice_reason_text, fields.ws_ext_reason_length, fields.ws_ext_reason)
end

register_dissector(WS_ID, "ws", handle_ws_extension)

