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
-- http dissector
HTTP_ID = 0x8ab62046

--     headers
add_field("http_ext_headers_array_length", ProtoField.int8("zilla.http_ext.headers_array_length", "Length", base.DEC))
add_field("http_ext_headers_array_size", ProtoField.int8("zilla.http_ext.headers_array_size", "Size", base.DEC))
add_field("http_ext_header_name_length", ProtoField.int8("zilla.http_ext.header_name_length", "Length", base.DEC))
add_field("http_ext_header_name", ProtoField.string("zilla.http_ext.header_name", "Name", base.NONE))
add_field("http_ext_header_value_length", ProtoField.int16("zilla.http_ext.header_value_length", "Length", base.DEC))
add_field("http_ext_header_value", ProtoField.string("zilla.http_ext.header_value", "Value", base.NONE))
--    promise id
add_field("http_ext_promise_id", ProtoField.uint64("zilla.promise_id", "Promise ID", base.HEX))

function handle_http_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == RESET_ID or frame_type_id == CHALLENGE_ID or
            frame_type_id == REDIRECT_ID then
        dissect_and_add_http_headers(buffer, offset, ext_subtree, "Headers", "Header")
    elseif frame_type_id == END_ID then
        dissect_and_add_http_headers(buffer, offset, ext_subtree, "Trailers", "Trailer")
    elseif frame_type_id == FLUSH_ID then
        slice_promise_id = buffer(offset, 8)
        ext_subtree:add_le(fields.http_ext_promise_id, slice_promise_id)
        dissect_and_add_http_headers(buffer, offset + 8, ext_subtree, "Promises", "Promise")
    end
end

function dissect_and_add_http_headers(buffer, offset, tree, plural_name, singular_name)
    local label = string.format("%s (%%d items)", plural_name)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, label,
        fields.http_ext_headers_array_length, fields.http_ext_headers_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local name_length, slice_name_length, slice_name = dissect_length_value(buffer, item_offset, 1)
        local value_offset = item_offset + name_length
        local value_length, slice_value_length, slice_value = dissect_length_value(buffer, value_offset, 2)
        local label = string.format("%s: %s: %s", singular_name, slice_name:string(), slice_value:string())
        local subtree = tree:add(zilla_protocol, buffer(item_offset, name_length + value_length), label)
        subtree:add_le(fields.http_ext_header_name_length, slice_name_length)
        subtree:add(fields.http_ext_header_name, slice_name)
        subtree:add_le(fields.http_ext_header_value_length, slice_value_length)
        subtree:add(fields.http_ext_header_value, slice_value)
        item_offset = item_offset + name_length + value_length
    end
end

function resolve_http_dissector(payload)
    if payload:range(0, 3):int() + 9 == payload:len() then
        return Dissector.get("http2")
    elseif payload:range(0, 3):string() == "PRI" then
        return Dissector.get("http2")
    elseif payload:range(0, 4):string() == "HTTP" then
        return Dissector.get("http")
    elseif payload:range(0, 3):string() == "GET" then
        return Dissector.get("http")
    elseif payload:range(0, 4):string() == "POST" then
        return Dissector.get("http")
    elseif payload:range(0, 3):string() == "PUT" then
        return Dissector.get("http")
    elseif payload:range(0, 6):string() == "DELETE" then
        return Dissector.get("http")
    elseif payload:range(0, 4):string() == "HEAD" then
        return Dissector.get("http")
    elseif payload:range(0, 7):string() == "OPTIONS" then
        return Dissector.get("http")
    elseif payload:range(0, 5):string() == "TRACE" then
        return Dissector.get("http")
    elseif payload:range(0, 7):string() == "CONNECT" then
        return Dissector.get("http")
    else
        return nil
    end
end

register_dissector(HTTP_ID, "http", handle_http_extension, resolve_http_dissector)

