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
-- proxy dissector
PROXY_ID = 0x8dcea850

local proxy_ext_address_family_types = {
    [0] = "INET",
    [1] = "INET4",
    [2] = "INET6",
    [3] = "UNIX",
    [4] = "NONE",
}

local proxy_ext_address_protocol_types = {
    [0] = "STREAM",
    [1] = "DATAGRAM",
}

local proxy_ext_info_types = {
    [0x01] = "ALPN",
    [0x02] = "AUTHORITY",
    [0x05] = "IDENTITY",
    [0x20] = "SECURE",
    [0x30] = "NAMESPACE",
}

local proxy_ext_secure_info_types = {
    [0x21] = "VERSION",
    [0x22] = "NAME",
    [0x23] = "CIPHER",
    [0x24] = "SIGNATURE",
    [0x25] = "KEY",
}

--     address
add_field("proxy_ext_address_family", ProtoField.uint8("zilla.proxy_ext.address_family", "Family", base.DEC,
        proxy_ext_address_family_types))
add_field("proxy_ext_address_protocol", ProtoField.uint8("zilla.proxy_ext.address_protocol", "Protocol", base.DEC,
        proxy_ext_address_protocol_types))
add_field("proxy_ext_address_inet_source_port", ProtoField.uint16("zilla.proxy_ext.address_inet_source_port", "Source Port",
        base.DEC))
add_field("proxy_ext_address_inet_destination_port", ProtoField.uint16("zilla.proxy_ext.address_inet_destination_port",
        "Destination Port", base.DEC))
add_field("proxy_ext_address_inet_source", ProtoField.string("zilla.proxy_ext.address_inet_source", "Source", base.NONE))
add_field("proxy_ext_address_inet_destination", ProtoField.string("zilla.proxy_ext.address_inet_destination", "Destination",
        base.NONE))
add_field("proxy_ext_address_inet4_source", ProtoField.new("Source", "zilla.proxy_ext.address_inet4_source", ftypes.IPv4))
add_field("proxy_ext_address_inet4_destination", ProtoField.new("Destination", "zilla.proxy_ext.address_inet4_destination",
        ftypes.IPv4))
add_field("proxy_ext_address_inet6_source", ProtoField.new("Source", "zilla.proxy_ext.address_inet6_source", ftypes.IPv6))
add_field("proxy_ext_address_inet6_destination", ProtoField.new("Destination", "zilla.proxy_ext.address_inet6_destination",
        ftypes.IPv6))
add_field("proxy_ext_address_unix_source", ProtoField.string("zilla.proxy_ext.address_unix_source", "Source", base.NONE))
add_field("proxy_ext_address_unix_destination", ProtoField.string("zilla.proxy_ext.address_unix_destination", "Destination",
        base.NONE))
--     info
add_field("proxy_ext_info_array_length", ProtoField.int8("zilla.proxy_ext.info_array_length", "Length", base.DEC))
add_field("proxy_ext_info_array_size", ProtoField.int8("zilla.proxy_ext.info_array_size", "Size", base.DEC))
add_field("proxy_ext_info_type", ProtoField.uint8("zilla.proxy_ext.info_type", "Type", base.HEX, proxy_ext_info_types))
add_field("proxy_ext_info_length", ProtoField.int16("zilla.proxy_ext.info_length", "Length", base.DEC))
add_field("proxy_ext_info_alpn", ProtoField.string("zilla.proxy_ext.info_alpn", "Value", base.NONE))
add_field("proxy_ext_info_authority", ProtoField.string("zilla.proxy_ext.info_authority", "Value", base.NONE))
add_field("proxy_ext_info_identity", ProtoField.bytes("zilla.proxy_ext.info_identity", "Value", base.NONE))
add_field("proxy_ext_info_namespace", ProtoField.string("zilla.proxy_ext.info_namespace", "Value", base.NONE))
add_field("proxy_ext_info_secure", ProtoField.string("zilla.proxy_ext.info_secure", "Value", base.NONE))
add_field("proxy_ext_info_secure_type", ProtoField.uint8("zilla.proxy_ext.info_secure_type", "Secure Type", base.HEX,
        proxy_ext_secure_info_types))

function handle_proxy_extension(buffer, offset, ext_subtree)
    -- BEGIN frame
    -- address
    local slice_address_family = buffer(offset, 1)
    local address_family_id = slice_address_family:le_int()
    local address_family = proxy_ext_address_family_types[address_family_id]
    local address_subtree_label = string.format("Address: %s", address_family)
    local info_offset
    if address_family == "INET" then
        local length = dissect_and_add_inet_address(buffer, offset, ext_subtree, address_subtree_label)
        info_offset = offset + length
    elseif address_family == "INET4" then
        local length = dissect_and_add_inet4_address(buffer, offset, ext_subtree, address_subtree_label)
        info_offset = offset + length
    elseif address_family == "INET6" then
        local length = dissect_and_add_inet6_address(buffer, offset, ext_subtree, address_subtree_label)
        info_offset = offset + length;
    elseif address_family == "UNIX" then
        local length = dissect_and_add_unix_address(buffer, offset, ext_subtree, address_subtree_label)
        info_offset = offset + length
    elseif address_family == "NONE" then
        local length = dissect_and_add_none_address(buffer, offset, ext_subtree, address_subtree_label)
        info_offset = offset + length
    end
    -- info
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, info_offset, ext_subtree, "Info (%d items)",
        fields.proxy_ext_info_array_length, fields.proxy_ext_info_array_size)
    local item_offset = info_offset + length
    for i = 1, array_size do
        local slice_type_id = buffer(item_offset, 1)
        local type_id = slice_type_id:le_int()
        local type = proxy_ext_info_types[type_id]
        if type == "ALPN" then
            local item_length = dissect_and_add_alpn_info(buffer, item_offset, ext_subtree)
            item_offset = item_offset + item_length
        elseif type == "AUTHORITY" then
            local item_length = dissect_and_add_authority_info(buffer, item_offset, ext_subtree)
            item_offset = item_offset + item_length
        elseif type == "IDENTITY" then
            local item_length = dissect_and_add_identity_info(buffer, item_offset, ext_subtree)
            item_offset = item_offset + item_length
        elseif type == "SECURE" then
            local item_length = dissect_and_add_secure_info(buffer, item_offset, ext_subtree)
            item_offset = item_offset + item_length
        elseif type == "NAMESPACE" then
            local item_length = dissect_and_add_namespace_info(buffer, item_offset, ext_subtree)
            item_offset = item_offset + item_length
        end
    end
end

function dissect_and_add_inet_address(buffer, offset, tree, label)
    local slice_address_family = buffer(offset, 1)
    local slice_protocol = buffer(offset + 1, 1)
    local source_length = buffer(offset + 2, 2):le_int()
    local slice_source = buffer(offset + 4, source_length)
    local destination_length = buffer(offset + 4 + source_length, 2):le_int()
    local slice_destination = buffer(offset + 6 + source_length, destination_length)
    local slice_source_port = buffer(offset + 6 + source_length + destination_length, 2)
    local slice_destination_port = buffer(offset + 8 + source_length + destination_length, 2)
    local length = 10 + source_length + destination_length
    local address_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
    address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
    address_subtree:add(fields.proxy_ext_address_inet_source, slice_source)
    address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
    address_subtree:add(fields.proxy_ext_address_inet_destination, slice_destination)
    address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
    return length
end

function dissect_and_add_inet4_address(buffer, offset, tree, label)
    local slice_address_family = buffer(offset, 1)
    local slice_protocol = buffer(offset + 1, 1)
    local slice_source = buffer(offset + 2, 4)
    local slice_destination = buffer(offset + 6, 4)
    local slice_source_port = buffer(offset + 10, 2)
    local slice_destination_port = buffer(offset + 12, 2)
    local length = 14;
    local address_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
    address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
    address_subtree:add(fields.proxy_ext_address_inet4_source, slice_source)
    address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
    address_subtree:add(fields.proxy_ext_address_inet4_destination, slice_destination)
    address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
    return length
end

function dissect_and_add_inet6_address(buffer, offset, tree, label)
    local slice_address_family = buffer(offset, 1)
    local slice_protocol = buffer(offset + 1, 1)
    local slice_source = buffer(offset + 2, 16)
    local slice_destination = buffer(offset + 18, 16)
    local slice_source_port = buffer(offset + 34, 2)
    local slice_destination_port = buffer(offset + 36, 2)
    local length = 38;
    local address_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
    address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
    address_subtree:add(fields.proxy_ext_address_inet6_source, slice_source)
    address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
    address_subtree:add(fields.proxy_ext_address_inet6_destination, slice_destination)
    address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
    return length
end

function dissect_and_add_unix_address(buffer, offset, tree, label)
    local slice_address_family = buffer(offset, 1)
    local slice_protocol = buffer(offset + 1, 1)
    local slice_source = buffer(offset + 2, 108)
    local slice_destination = buffer(offset + 110, 108)
    local length = 218
    local address_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
    address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
    address_subtree:add(fields.proxy_ext_address_unix_source, slice_source)
    address_subtree:add(fields.proxy_ext_address_unix_destination, slice_destination)
    return length
end

function dissect_and_add_none_address(buffer, offset, tree, label)
    local slice_address_family = buffer(offset, 1)
    local address_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
    return 1
end

function dissect_and_add_alpn_info(buffer, offset, tree, label_format)
    local type_id_length = 1
    local slice_type_id = buffer(offset, type_id_length)
    local length, slice_length, slice_text = dissect_length_value(buffer, offset + type_id_length, 1)
    add_proxy_string_as_subtree(buffer(offset, type_id_length + length), tree, "Info: %s: %s", slice_type_id,
        slice_length, slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_alpn)
    return type_id_length + length
end

function dissect_and_add_authority_info(buffer, offset, tree)
    local type_id_length = 1
    local slice_type_id = buffer(offset, type_id_length)
    local length, slice_length, slice_text = dissect_length_value(buffer, offset + type_id_length, 2)
    add_proxy_string_as_subtree(buffer(offset, type_id_length + length), tree, "Info: %s: %s", slice_type_id,
        slice_length, slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_authority)
    return type_id_length + length
end

function dissect_and_add_identity_info(buffer, offset, tree, label_format)
    local type_id_length = 1
    local slice_type_id = buffer(offset, type_id_length)
    local length, slice_length, slice_bytes = dissect_length_value(buffer, offset + type_id_length, 2)
    local label = string.format("Info: IDENTITY: 0x%s", slice_bytes:bytes())
    local subtree = tree:add(zilla_protocol, buffer(offset, type_id_length + length), label)
    subtree:add(fields.proxy_ext_info_type, slice_type_id)
    subtree:add_le(fields.proxy_ext_info_length, slice_length)
    subtree:add(fields.proxy_ext_info_identity, slice_bytes)
    return type_id_length + length
end

function dissect_and_add_secure_info(buffer, offset, tree)
    local slice_type_id = buffer(offset, 1)
    local slice_secure_type_id = buffer(offset + 1, 1)
    local secure_type_id = slice_secure_type_id:le_int();
    local secure_type = proxy_ext_secure_info_types[secure_type_id]
    local length_length
    if secure_type == "VERSION" or secure_type == "CIPHER" or secure_type == "SIGNATURE" or secure_type == "KEY" then
        length_length = 1
    elseif secure_type == "NAME" then
        length_length = 2
    end
    local length, slice_length, slice_text = dissect_length_value(buffer, offset + 2, length_length)
    local label = string.format("Info: SECURE: %s: %s", secure_type, slice_text:string())
    local subtree = tree:add(zilla_protocol, buffer(offset, length + 2), label)
    subtree:add(fields.proxy_ext_info_type, slice_type_id)
    subtree:add(fields.proxy_ext_info_secure_type, slice_secure_type_id)
    subtree:add_le(fields.proxy_ext_info_length, slice_length)
    subtree:add(fields.proxy_ext_info_secure, slice_text)
    return 2 + length
end

function dissect_and_add_namespace_info(buffer, offset, tree)
    local type_id_length = 1
    local slice_type_id = buffer(offset, type_id_length)
    local length, slice_length, slice_text = dissect_length_value(buffer, offset + type_id_length, 2)
    add_proxy_string_as_subtree(buffer(offset, type_id_length + length), tree, "Info: %s: %s", slice_type_id, slice_length,
        slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_namespace)
    return type_id_length + length
end

function add_proxy_string_as_subtree(buffer, tree, label_format, slice_type_id, slice_length, slice_text, field_type, field_length,
        field_text)
    local type_id = slice_type_id:le_int()
    local type = proxy_ext_info_types[type_id]
    local text = slice_text:string()
    local label = string.format(label_format, type, text)
    local subtree = tree:add(zilla_protocol, buffer, label)
    subtree:add(field_type, slice_type_id)
    subtree:add_le(field_length, slice_length)
    subtree:add(field_text, slice_text)
end

register_dissector(PROXY_ID, "proxy", handle_proxy_extension)

