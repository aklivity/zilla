--[[

    Copyright 2021-2026 Aklivity Inc

    Licensed under the Aklivity Community License (the "License"); you may not use
    this file except in compliance with the License.  You may obtain a copy of the
    License at

      https://www.aklivity.io/aklivity-community-license/

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OF ANY KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations under the License.

]]
-- sse dissector
SSE_ID = 0x03409e2e

add_field("sse_ext_scheme_length", ProtoField.int16("zilla.sse_ext.scheme_length", "Length", base.DEC))
add_field("sse_ext_scheme", ProtoField.string("zilla.sse_ext.scheme", "Scheme", base.NONE))
add_field("sse_ext_authority_length", ProtoField.int16("zilla.sse_ext.authority_length", "Length", base.DEC))
add_field("sse_ext_authority", ProtoField.string("zilla.sse_ext.authority", "Authority", base.NONE))
add_field("sse_ext_path_length", ProtoField.int16("zilla.sse_ext.path_length", "Length", base.DEC))
add_field("sse_ext_path", ProtoField.string("zilla.sse_ext.path", "Path", base.NONE))
add_field("sse_ext_last_id_length", ProtoField.int8("zilla.sse_ext.last_id_length", "Length", base.DEC))
add_field("sse_ext_last_id", ProtoField.string("zilla.sse_ext.last_id", "Last ID", base.NONE))
add_field("sse_ext_timestamp", ProtoField.uint64("zilla.sse_ext.timestamp", "Timestamp", base.HEX))
add_field("sse_ext_id_length", ProtoField.int8("zilla.sse_ext.id_length", "Length", base.DEC))
add_field("sse_ext_id", ProtoField.string("zilla.sse_ext.id", "ID", base.NONE))
add_field("sse_ext_type_length", ProtoField.int8("zilla.sse_ext.type_length", "Length", base.DEC))
add_field("sse_ext_type", ProtoField.string("zilla.sse_ext.type", "Type", base.NONE))

function handle_sse_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == REDIRECT_ID then
        handle_sse_begin_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == DATA_ID then
        handle_sse_data_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == END_ID then
        handle_sse_end_extension(buffer, offset, ext_subtree)
    end
end

function handle_sse_begin_extension(buffer, offset, ext_subtree)
    -- scheme
    local scheme_offset = offset
    local scheme_length, slice_scheme_length, slice_scheme_text = dissect_length_value(buffer, scheme_offset, 2)
    add_string_as_subtree(buffer(scheme_offset, scheme_length), ext_subtree, "Scheme: %s", slice_scheme_length,
        slice_scheme_text, fields.sse_ext_scheme_length, fields.sse_ext_scheme)
    -- authority
    local authority_offset = scheme_offset + scheme_length
    local authority_length, slice_authority_length, slice_authority_text = dissect_length_value(buffer, authority_offset, 2)
    add_string_as_subtree(buffer(authority_offset, authority_length), ext_subtree, "Authority: %s", slice_authority_length,
        slice_authority_text, fields.sse_ext_authority_length, fields.sse_ext_authority)
    -- path
    local path_offset = authority_offset + authority_length
    local path_length, slice_path_length, slice_path_text = dissect_length_value(buffer, path_offset, 2)
    add_string_as_subtree(buffer(path_offset, path_length), ext_subtree, "Path: %s", slice_path_length,
        slice_path_text, fields.sse_ext_path_length, fields.sse_ext_path)
    -- last_id
    local last_id_offset = path_offset + path_length
    local last_id_length, slice_last_id_length, slice_last_id_text = dissect_length_value(buffer, last_id_offset, 1)
    add_string_as_subtree(buffer(last_id_offset, last_id_length), ext_subtree, "Last ID: %s", slice_last_id_length,
        slice_last_id_text, fields.sse_ext_last_id_length, fields.sse_ext_last_id)
end

function handle_sse_data_extension(buffer, offset, ext_subtree)
    -- timestamp
    local timestamp_offset = offset
    local timestamp_length = 8
    local slice_timestamp = buffer(timestamp_offset, timestamp_length)
    ext_subtree:add_le(fields.sse_ext_timestamp, slice_timestamp)
    -- id
    local id_offset = timestamp_offset + timestamp_length
    local id_length, slice_id_length, slice_id_text = dissect_length_value(buffer, id_offset, 1)
    add_string_as_subtree(buffer(id_offset, id_length), ext_subtree, "ID: %s", slice_id_length,
        slice_id_text, fields.sse_ext_id_length, fields.sse_ext_id)
    -- type
    local type_offset = id_offset + id_length
    local type_length, slice_type_length, slice_type_text = dissect_length_value(buffer, type_offset, 1)
    add_string_as_subtree(buffer(type_offset, type_length), ext_subtree, "Type: %s", slice_type_length,
        slice_type_text, fields.sse_ext_type_length, fields.sse_ext_type)
end

function handle_sse_end_extension(buffer, offset, ext_subtree)
    local id_length, slice_id_length, slice_id_text = dissect_length_value(buffer, offset, 1)
    add_string_as_subtree(buffer(offset, id_length), ext_subtree, "Id: %s", slice_id_length,
        slice_id_text, fields.sse_ext_id_length, fields.sse_ext_id)
end

register_dissector(SSE_ID, "sse", handle_sse_extension)

