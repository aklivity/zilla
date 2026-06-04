-- grpc dissector
GRPC_ID = 0xf9c7583a

local grpc_types = {
    [0] = "TEXT",
    [1] = "BASE64"
}

add_field("grpc_ext_scheme_length", ProtoField.int16("zilla.grpc_ext.scheme_length", "Length", base.DEC))
add_field("grpc_ext_scheme", ProtoField.string("zilla.grpc_ext.scheme", "Scheme", base.NONE))
add_field("grpc_ext_authority_length", ProtoField.int16("zilla.grpc_ext.authority_length", "Length", base.DEC))
add_field("grpc_ext_authority", ProtoField.string("zilla.grpc_ext.authority", "Authority", base.NONE))
add_field("grpc_ext_service_length", ProtoField.int16("zilla.grpc_ext.service_length", "Length", base.DEC))
add_field("grpc_ext_service", ProtoField.string("zilla.grpc_ext.service", "Service", base.NONE))
add_field("grpc_ext_method_length", ProtoField.int16("zilla.grpc_ext.method_length", "Length", base.DEC))
add_field("grpc_ext_method", ProtoField.string("zilla.grpc_ext.method", "Method", base.NONE))
add_field("grpc_ext_deferred", ProtoField.int32("zilla.grpc_ext.deferred", "Deferred", base.DEC))
add_field("grpc_ext_status_length", ProtoField.int16("zilla.grpc_ext.status_length", "Length", base.DEC))
add_field("grpc_ext_status", ProtoField.string("zilla.grpc_ext.status", "Status", base.NONE))
--    metadata
add_field("grpc_ext_metadata_array_length", ProtoField.int8("zilla.grpc_ext.metadata_array_length", "Length", base.DEC))
add_field("grpc_ext_metadata_array_size", ProtoField.int8("zilla.grpc_ext.metadata_array_size", "Size", base.DEC))
add_field("grpc_ext_metadata_type", ProtoField.uint8("zilla.grpc_ext.metadata_type", "Type", base.DEC, grpc_types))
add_field("grpc_ext_metadata_name_length_varint", ProtoField.bytes("zilla.grpc_ext.metadata_name_varint", "Length (varint32)",
        base.NONE))
add_field("grpc_ext_metadata_name_length", ProtoField.int32("zilla.grpc_ext.metadata_name_length", "Length", base.DEC))
add_field("grpc_ext_metadata_name", ProtoField.string("zilla.grpc_ext.metadata_name", "Name", base.NONE))
add_field("grpc_ext_metadata_value_length_varint", ProtoField.bytes("zilla.grpc_ext.metadata_value_length_varint", "Length (varint32)",
        base.NONE))
add_field("grpc_ext_metadata_value_length", ProtoField.int32("zilla.grpc_ext.metadata_value_length", "Length", base.DEC))
add_field("grpc_ext_metadata_value", ProtoField.string("zilla.grpc_ext.metadata_value", "Value", base.NONE))

function handle_grpc_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == REDIRECT_ID then
        handle_grpc_begin_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == DATA_ID then
        handle_grpc_data_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == ABORT_ID or frame_type_id == RESET_ID then
        handle_grpc_abort_reset_extension(buffer, offset, ext_subtree)
    end
end

function handle_grpc_begin_extension(buffer, offset, ext_subtree)
    -- scheme
    local scheme_offset = offset
    local scheme_length, slice_scheme_length, slice_scheme_text = dissect_length_value(buffer, scheme_offset, 2)
    add_string_as_subtree(buffer(scheme_offset, scheme_length), ext_subtree, "Scheme: %s", slice_scheme_length,
        slice_scheme_text, fields.grpc_ext_scheme_length, fields.grpc_ext_scheme)
    -- authority
    local authority_offset = scheme_offset + scheme_length
    local authority_length, slice_authority_length, slice_authority_text = dissect_length_value(buffer, authority_offset, 2)
    add_string_as_subtree(buffer(authority_offset, authority_length), ext_subtree, "Authority: %s", slice_authority_length,
        slice_authority_text, fields.grpc_ext_authority_length, fields.grpc_ext_authority)
    -- service
    local service_offset = authority_offset + authority_length
    local service_length, slice_service_length, slice_service_text = dissect_length_value(buffer, service_offset, 2)
    add_string_as_subtree(buffer(service_offset, service_length), ext_subtree, "Service: %s", slice_service_length,
        slice_service_text, fields.grpc_ext_service_length, fields.grpc_ext_service)
    -- method
    local method_offset = service_offset + service_length
    local method_length, slice_method_length, slice_method_text = dissect_length_value(buffer, method_offset, 2)
    add_string_as_subtree(buffer(method_offset, method_length), ext_subtree, "Method: %s", slice_method_length,
        slice_method_text, fields.grpc_ext_method_length, fields.grpc_ext_method)
    -- metadata array
    local metadata_array_offset = method_offset + method_length
    local slice_metadata_array_length = buffer(metadata_array_offset, 4)
    local slice_metadata_array_size = buffer(metadata_array_offset + 4, 4)
    local metadata_array_length = slice_metadata_array_length:le_int()
    local metadata_array_size = slice_metadata_array_size:le_int()
    local length = 8
    local label = string.format("Metadata (%d items)", metadata_array_size)
    local metadata_array_subtree = ext_subtree:add(zilla_protocol, buffer(metadata_array_offset, length), label)
    metadata_array_subtree:add_le(fields.grpc_ext_metadata_array_length, slice_metadata_array_length)
    metadata_array_subtree:add_le(fields.grpc_ext_metadata_array_size, slice_metadata_array_size)
    local item_offset = metadata_array_offset + length
    for i = 1, metadata_array_size do
        local record_length = dissect_and_add_grpc_metadata(buffer, item_offset, ext_subtree)
        item_offset = item_offset + record_length
    end
end

function handle_grpc_data_extension(buffer, offset, ext_subtree)
    local slice_deferred = buffer(offset, 4)
    ext_subtree:add_le(fields.grpc_ext_deferred, slice_deferred)
end

function handle_grpc_abort_reset_extension(buffer, offset, ext_subtree)
    local status_length, slice_status_length, slice_status_text = dissect_length_value(buffer, offset, 2)
    add_string_as_subtree(buffer(offset, status_length), ext_subtree, "Status: %s", slice_status_length,
        slice_status_text, fields.grpc_ext_status_length, fields.grpc_ext_status)
end

function dissect_and_add_grpc_metadata(buffer, offset, tree)
    -- type
    local type_offset = offset
    local type_length = 1
    local slice_type_id = buffer(offset, type_length)
    local type = grpc_types[slice_type_id:le_int()]
    -- name_length
    local name_length_offset = type_offset + type_length
    local name_length, slice_name_length_varint, name_length_length = decode_varint32(buffer, name_length_offset)
    -- name
    local name_offset = name_length_offset + name_length_length
    local slice_name = buffer(name_offset, name_length)
    local name = slice_name:string()
    -- value_length
    local value_length_offset = name_offset + name_length
    local value_length, slice_value_length_varint, value_length_length = decode_varint32(buffer, value_length_offset)
    -- value
    local value_offset = value_length_offset + value_length_length
    local slice_value = buffer(value_offset, value_length)
    local value = slice_value:string()
    -- add subtree
    local record_length = type_length + name_length_length + name_length + value_length_length + value_length
    local label = string.format("Metadata: [%s] %s: %s", type, name, value)
    local subtree = tree:add(zilla_protocol, buffer(metadata_offset, record_length), label)
    subtree:add(fields.grpc_ext_metadata_type, slice_type_id)
    subtree:add(fields.grpc_ext_metadata_name_length_varint, slice_name_length_varint)
    subtree:add(fields.grpc_ext_metadata_name_length, name_length)
    subtree:add(fields.grpc_ext_metadata_name, slice_name)
    subtree:add(fields.grpc_ext_metadata_value_length_varint, slice_value_length_varint)
    subtree:add(fields.grpc_ext_metadata_value_length, value_length)
    subtree:add(fields.grpc_ext_metadata_value, slice_value)
    return record_length
end

register_dissector(GRPC_ID, "grpc", handle_grpc_extension)

