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
-- amqp dissector
AMQP_ID = 0x112dc182

local amqp_ext_capabilities_types = {
    [1] = "SEND_ONLY",
    [2] = "RECEIVE_ONLY",
    [3] = "SEND_AND_RECEIVE",
}

local amqp_ext_sender_settle_modes = {
    [0] = "UNSETTLED",
    [1] = "SETTLED",
    [2] = "MIXED",
}

local amqp_ext_receiver_settle_modes = {
    [0] = "FIRST",
    [1] = "SECOND",
}

local amqp_ext_transfer_flags = {
    [0] = "SETTLED",
    [1] = "RESUME",
    [2] = "ABORTED",
    [3] = "BATCHABLE",
}

local amqp_ext_annotation_key_types = {
    [1] = "ID",
    [2] = "NAME",
}

local amqp_ext_body_kinds = {
    [0] = "DATA",
    [1] = "SEQUENCE",
    [2] = "VALUE_STRING32",
    [3] = "VALUE_STRING8",
    [4] = "VALUE_BINARY32",
    [5] = "VALUE_BINARY8",
    [6] = "VALUE_SYMBOL32",
    [7] = "VALUE_SYMBOL8",
    [8] = "VALUE_NULL",
    [9] = "VALUE",
}

local amqp_ext_message_id_types = {
    [1] = "ULONG",
    [2] = "UUID",
    [3] = "BINARY",
    [4] = "STRINGTYPE",
}

--     begin
add_field("amqp_ext_address_length", ProtoField.int8("zilla.amqp_ext.address_length", "Length", base.DEC))
add_field("amqp_ext_address", ProtoField.string("zilla.amqp_ext.address", "Name", base.NONE))
add_field("amqp_ext_capabilities", ProtoField.uint8("zilla.amqp_ext.capabilities", "Capabilities", base.DEC,
        amqp_ext_capabilities_types))
add_field("amqp_ext_sender_settle_mode", ProtoField.uint8("zilla.amqp_ext.sender_settle_mode", "Sender Settle Mode", base.DEC,
        amqp_ext_sender_settle_modes))
add_field("amqp_ext_receiver_settle_mode", ProtoField.uint8("zilla.amqp_ext.receiver_settle_mode", "Receiver Settle Mode", base.DEC,
        amqp_ext_receiver_settle_modes))
--     data
add_field("amqp_ext_delivery_tag_length", ProtoField.int16("zilla.amqp_ext.delivery_tag_length", "Length", base.DEC))
add_field("amqp_ext_delivery_tag", ProtoField.string("zilla.amqp_ext.delivery_tag", "Delivery Tag", base.NONE))
add_field("amqp_ext_message_format", ProtoField.uint32("zilla.amqp_ext.message_format", "Message Format", base.DEC))
add_field("amqp_ext_body_kind", ProtoField.uint8("zilla.amqp_ext.body_kind", "Body Kind", base.DEC, amqp_ext_body_kinds))
add_field("amqp_ext_deferred", ProtoField.int32("zilla.amqp_ext.deferred", "Deferred", base.DEC))
--     flags
add_field("amqp_ext_transfer_flags", ProtoField.uint8("zilla.amqp_ext.transfer_flags", "Flags", base.HEX))
add_field("amqp_ext_transfer_flags_settled", ProtoField.uint8("zilla.amqp_ext.transfer_flags_settled", "SETTLED",
        base.DEC, flags_types, 0x01))
add_field("amqp_ext_transfer_flags_resume", ProtoField.uint8("zilla.amqp_ext.transfer_flags_resume", "RESUME",
        base.DEC, flags_types, 0x02))
add_field("amqp_ext_transfer_flags_aborted", ProtoField.uint8("zilla.amqp_ext.transfer_flags_aborted", "ABORTED",
        base.DEC, flags_types, 0x04))
add_field("amqp_ext_transfer_flags_batchable", ProtoField.uint8("zilla.amqp_ext.transfer_flags_batchable", "BATCHABLE",
        base.DEC, flags_types, 0x08))
--     annotations
add_field("amqp_ext_annotations_length", ProtoField.int16("zilla.amqp_ext.annotations_length", "Length", base.DEC))
add_field("amqp_ext_annotations_size", ProtoField.int16("zilla.amqp_ext.annotations_size", "Size", base.DEC))
add_field("amqp_ext_annotation_key_type", ProtoField.uint8("zilla.amqp_ext.annotation_key_type", "Key Type", base.DEC,
        amqp_ext_annotation_key_types))
add_field("amqp_ext_annotation_key_id", ProtoField.uint64("zilla.amqp_ext.annotation_key_id", "Key [ID]", base.HEX))
add_field("amqp_ext_annotation_key_name_length", ProtoField.uint8("zilla.amqp_ext.annotation_key_name_length", "Length", base.DEC))
add_field("amqp_ext_annotation_key_name", ProtoField.string("zilla.amqp_ext.annotation_key_name", "Key Name", base.NONE))
add_field("amqp_ext_annotation_value_length", ProtoField.uint8("zilla.amqp_ext.annotation_value_length", "Length", base.DEC))
add_field("amqp_ext_annotation_value", ProtoField.string("zilla.amqp_ext.annotation_value", "Value", base.NONE))
--     properties
add_field("amqp_ext_properties_length", ProtoField.int16("zilla.amqp_ext.properties_length", "Length", base.DEC))
add_field("amqp_ext_properties_size", ProtoField.int16("zilla.amqp_ext.properties_size", "Size", base.DEC))
add_field("amqp_ext_properties_fields", ProtoField.uint64("zilla.amqp_ext.properties_fields", "Fields", base.HEX))
add_field("amqp_ext_property_message_id_type", ProtoField.int16("zilla.amqp_ext.message_id_type", "ID Type", base.DEC,
        amqp_ext_message_id_types))
add_field("amqp_ext_property_message_id_ulong", ProtoField.uint64("zilla.amqp_ext.message_id_ulong", "Message ID", base.HEX))
add_field("amqp_ext_property_message_id_uuid_length", ProtoField.int8("zilla.amqp_ext.property_message_id_uuid_length",
        "Length", base.DEC))
add_field("amqp_ext_property_message_id_uuid", ProtoField.string("zilla.amqp_ext.property_message_id_uuid", "Message ID", base.NONE))
add_field("amqp_ext_property_message_id_binary_length", ProtoField.int8("zilla.amqp_ext.property_message_id_binary_length",
        "Length", base.DEC))
add_field("amqp_ext_property_message_id_binary", ProtoField.string("zilla.amqp_ext.property_message_id_binary", "Message ID", base.NONE))
add_field("amqp_ext_property_message_id_stringtype_length", ProtoField.int8("zilla.amqp_ext.property_message_id_stringtype_length",
        "Length", base.DEC))
add_field("amqp_ext_property_message_id_stringtype", ProtoField.string("zilla.amqp_ext.property_message_id_stringtype",
        "Message ID", base.NONE))
add_field("amqp_ext_property_user_id_length", ProtoField.int16("zilla.amqp_ext.property_user_id_length", "Length", base.DEC))
add_field("amqp_ext_property_user_id", ProtoField.string("zilla.amqp_ext.property_user_id", "User ID", base.NONE))
add_field("amqp_ext_property_to_length", ProtoField.int8("zilla.amqp_ext.property_to_length", "Length", base.DEC))
add_field("amqp_ext_property_to", ProtoField.string("zilla.amqp_ext.property_to", "To", base.NONE))
add_field("amqp_ext_property_subject_length", ProtoField.int8("zilla.amqp_ext.property_subject_length", "Length", base.DEC))
add_field("amqp_ext_property_subject", ProtoField.string("zilla.amqp_ext.property_subject", "Subject", base.NONE))
add_field("amqp_ext_property_reply_to_length", ProtoField.int8("zilla.amqp_ext.property_reply_to_length", "Length", base.DEC))
add_field("amqp_ext_property_reply_to", ProtoField.string("zilla.amqp_ext.property_reply_to", "Reply To", base.NONE))
add_field("amqp_ext_property_correlation_id_type", ProtoField.int16("zilla.amqp_ext.correlation_id_type", "ID Type", base.DEC,
        amqp_ext_message_id_types))
add_field("amqp_ext_property_correlation_id_ulong", ProtoField.uint64("zilla.amqp_ext.correlation_id_ulong", "Correlation ID", base.HEX))
add_field("amqp_ext_property_correlation_id_uuid_length", ProtoField.int8("zilla.amqp_ext.property_correlation_id_uuid_length",
        "Length", base.DEC))
add_field("amqp_ext_property_correlation_id_uuid", ProtoField.string("zilla.amqp_ext.property_correlation_id_uuid", "Correlation ID", base.NONE))
add_field("amqp_ext_property_correlation_id_binary_length", ProtoField.int8("zilla.amqp_ext.property_correlation_id_binary_length",
        "Length", base.DEC))
add_field("amqp_ext_property_correlation_id_binary", ProtoField.string("zilla.amqp_ext.property_correlation_id_binary", "Correlation ID", base.NONE))
add_field("amqp_ext_property_correlation_id_stringtype_length", ProtoField.int8("zilla.amqp_ext.property_correlation_id_stringtype_length",
        "Length", base.DEC))
add_field("amqp_ext_property_correlation_id_stringtype", ProtoField.string("zilla.amqp_ext.property_correlation_id_stringtype",
        "Correlation ID", base.NONE))
add_field("amqp_ext_property_content_type_length", ProtoField.int8("zilla.amqp_ext.property_content_type_length", "Length", base.DEC))
add_field("amqp_ext_property_content_type", ProtoField.string("zilla.amqp_ext.property_content_type", "Content Type", base.NONE))
add_field("amqp_ext_property_content_encoding_length", ProtoField.int8("zilla.amqp_ext.property_content_encoding_length", "Length",
        base.DEC))
add_field("amqp_ext_property_content_encoding", ProtoField.string("zilla.amqp_ext.property_content_encoding", "Content Encoding",
        base.NONE))
add_field("amqp_ext_property_absolute_expiry_time", ProtoField.int64("zilla.amqp_ext.property_absolut_expiry_time",
        "Property: Absolute Expiry Time", base.DEC))
add_field("amqp_ext_property_creation_time", ProtoField.int64("zilla.amqp_ext.property_creation_time", "Property: Creation Time",
        base.DEC))
add_field("amqp_ext_property_group_id_length", ProtoField.int8("zilla.amqp_ext.property_group_id_length", "Length", base.DEC))
add_field("amqp_ext_property_group_id", ProtoField.string("zilla.amqp_ext.property_group_id", "Group ID", base.NONE))
add_field("amqp_ext_property_group_sequence", ProtoField.int32("zilla.amqp_ext.property_group_sequence", "Property: Group Sequence", base.DEC))
add_field("amqp_ext_property_reply_to_group_id_length", ProtoField.int8("zilla.amqp_ext.property_reply_to_group_id_length", "Length",
        base.DEC))
add_field("amqp_ext_property_reply_to_group_id", ProtoField.string("zilla.amqp_ext.property_reply_to_group_id", "Reply To Group ID",
        base.NONE))
--     application_properties
add_field("amqp_ext_application_properties_length", ProtoField.int16("zilla.amqp_ext.application_properties_length", "Length",
        base.DEC))
add_field("amqp_ext_application_properties_size", ProtoField.int16("zilla.amqp_ext.application_properties_size", "Size", base.DEC))
add_field("amqp_ext_application_property_key_length", ProtoField.uint32("zilla.amqp_ext.application_property_key_length", "Length",
        base.DEC))
add_field("amqp_ext_application_property_key", ProtoField.string("zilla.amqp_ext.application_property_key", "Key", base.NONE))
add_field("amqp_ext_application_property_value_length", ProtoField.uint8("zilla.amqp_ext.application_property_value_length", "Length",
        base.DEC))
add_field("amqp_ext_application_property_value", ProtoField.string("zilla.amqp_ext.application_property_value", "Value", base.NONE))
--     abort
add_field("amqp_ext_condition_length", ProtoField.uint8("zilla.amqp_ext.condition_length", "Length", base.DEC))
add_field("amqp_ext_condition", ProtoField.string("zilla.amqp_ext.condition", "Condition", base.NONE))

function handle_amqp_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID then
        handle_amqp_begin_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == DATA_ID then
        handle_amqp_data_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == ABORT_ID then
        handle_amqp_abort_extension(buffer, offset, ext_subtree)
    elseif frame_type_id == FLUSH_ID then
        handle_amqp_flush_extension(buffer, offset, ext_subtree)
    end
end

function handle_amqp_begin_extension(buffer, offset, ext_subtree)
    -- address
    local address_offset = offset
    local address_length, slice_address_length, slice_address_text = dissect_length_value(buffer, address_offset, 1)
    add_string_as_subtree(buffer(address_offset, address_length), ext_subtree, "Address: %s", slice_address_length,
        slice_address_text, fields.amqp_ext_address_length, fields.amqp_ext_address)
    -- capabilities
    local capabilities_offset = address_offset + address_length
    local capabilities_length = 1
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    ext_subtree:add_le(fields.amqp_ext_capabilities, slice_capabilities)
    -- sender_settle_mode
    local sender_settle_mode_offset = capabilities_offset + capabilities_length
    local sender_settle_mode_length = 1
    local slice_sender_settle_mode = buffer(sender_settle_mode_offset, sender_settle_mode_length)
    ext_subtree:add_le(fields.amqp_ext_sender_settle_mode, slice_sender_settle_mode)
    -- receiver_settle_mode
    local receiver_settle_mode_offset = sender_settle_mode_offset + sender_settle_mode_length
    local receiver_settle_mode_length = 1
    local slice_receiver_settle_mode = buffer(receiver_settle_mode_offset, receiver_settle_mode_length)
    ext_subtree:add_le(fields.amqp_ext_receiver_settle_mode, slice_receiver_settle_mode)
end

function handle_amqp_data_extension(buffer, offset, ext_subtree)
    -- delivery_tag
    local delivery_tag_offset = offset
    local delivery_tag_length = add_amqp_binary_as_subtree(buffer, delivery_tag_offset, ext_subtree, "Delivery Tag: %s",
        fields.amqp_ext_delivery_tag_length, fields.amqp_ext_delivery_tag)
    -- message_format
    local message_format_offset = delivery_tag_offset + delivery_tag_length
    local message_format_length = 4
    local slice_message_format = buffer(message_format_offset, message_format_length)
    ext_subtree:add(fields.amqp_ext_message_format, slice_message_format)
    -- flags
    local flags_offset = message_format_offset + message_format_length
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = ext_subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.amqp_ext_transfer_flags_settled, slice_flags)
    flags_subtree:add_le(fields.amqp_ext_transfer_flags_resume, slice_flags)
    flags_subtree:add_le(fields.amqp_ext_transfer_flags_aborted, slice_flags)
    flags_subtree:add_le(fields.amqp_ext_transfer_flags_batchable, slice_flags)
    -- annotations
    local annotations_offset = flags_offset + flags_length
    local annotations_length = resolve_length_of_array(buffer, annotations_offset)
    dissect_and_add_amqp_annotation_array(buffer, annotations_offset, ext_subtree, fields.amqp_ext_annotations_length,
        fields.amqp_ext_annotations_size)
    -- properties
    local properties_offset = annotations_offset + annotations_length
    local properties_length = resolve_length_of_list_amqp(buffer, properties_offset)
    dissect_and_add_amqp_properties_list(buffer, properties_offset, ext_subtree, fields.amqp_ext_properties_length)
    -- application_properties
    local application_properties_offset = properties_offset + properties_length
    local application_properties_length = resolve_length_of_array(buffer, application_properties_offset)
    dissect_and_add_amqp_application_properties_array(buffer, application_properties_offset, ext_subtree,
        fields.amqp_ext_application_properties_length, fields.amqp_ext_application_properties_size)
    -- body_kind
    local body_kind_offset = application_properties_offset + application_properties_length
    local body_kind_length = 1
    local slice_body_kind = buffer(body_kind_offset, body_kind_length)
    ext_subtree:add_le(fields.amqp_ext_body_kind, slice_body_kind)
    -- deferred
    local deferred_offset = body_kind_offset + body_kind_length
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add(fields.amqp_ext_deferred, slice_deferred)
end

function add_amqp_binary_as_subtree(buffer, offset, tree, label_format, field_length, field_bytes)
    local slice_length = buffer(offset, 2)
    local length = math.max(slice_length:int(), 0)
    local slice_bytes = buffer(offset + 2, length)
    local label = string.format(label_format, slice_bytes:string())
    local subtree = tree:add(zilla_protocol, buffer(offset, 2 + length), label)
    subtree:add(field_length, slice_length)
    if (length > 0) then
        subtree:add(field_bytes, slice_bytes)
    end
    return 2 + length
end

function resolve_length_of_amqp_binary(buffer, offset)
    local slice_length = buffer(offset, 2)
    local length = math.max(slice_length:int(), 0)
    return 2 + length
end

function dissect_length_value_amqp(buffer, offset, length_length)
    local slice_length = buffer(offset, length_length)
    local length = math.max(slice_length:int(), 0)
    local slice_value = buffer(offset + length_length, length)
    local item_length = length + length_length
    return item_length, slice_length, slice_value
end

function add_string_as_subtree_amqp(buffer, tree, label_format, slice_length, slice_text, field_length, field_text)
    local text = slice_text:string()
    local label = string.format(label_format, text)
    local subtree = tree:add(zilla_protocol, buffer, label)
    subtree:add(field_length, slice_length)
    subtree:add(field_text, slice_text)
end

function dissect_and_add_amqp_annotation_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Annotations (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length, item_label = resolve_length_and_label_of_amqp_annotation(buffer, item_offset)
        local label = string.format("Annotation: %s", item_label)
        local annotation_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        dissect_and_add_amqp_annotation(buffer, item_offset, annotation_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_amqp_annotation(buffer, offset, tree, field_array_length, field_array_size)
    -- key_type
    local key_type_offset = offset
    local key_type_length = 1
    local slice_key_type = buffer(key_type_offset, key_type_length)
    local key_type = amqp_ext_annotation_key_types[slice_key_type:le_uint()]
    tree:add_le(fields.amqp_ext_annotation_key_type, slice_key_type)
    -- key
    local key_offset = key_type_offset + key_type_length
    local key_length
    if key_type == "ID" then
        key_length = 8
        local slice_key = buffer(key_offset, key_length)
        tree:add_le(fields.amqp_ext_annotation_key_id, slice_key)
    elseif key_type == "NAME" then
        local slice_key_length, slice_key_text
        key_length, slice_key_length, slice_key_text = dissect_length_value_amqp(buffer, key_offset, 1)
        add_string_as_subtree_amqp(buffer(key_offset, key_length), tree, "Key [NAME]: %s", slice_key_length,
            slice_key_text, fields.amqp_ext_annotation_key_name_length, fields.amqp_ext_annotation_key_name)
    end
    -- value
    local value_offset = key_offset + key_length
    local value_length = resolve_length_of_amqp_binary(buffer, value_offset)
    add_amqp_binary_as_subtree(buffer, value_offset, tree, "Value: %s",
        fields.amqp_ext_annotation_value_length, fields.amqp_ext_annotation_value)
end

function resolve_length_and_label_of_amqp_annotation(buffer, offset)
    -- key_type
    local key_type_offset = offset
    local key_type_length = 1
    local slice_key_type = buffer(key_type_offset, key_type_length)
    local key_type = amqp_ext_annotation_key_types[slice_key_type:le_uint()]
    -- key
    local key_offset = key_type_offset + key_type_length
    local key_length
    local key
    if key_type == "ID" then
        key_length = 8
        local slice_key = buffer(key_offset, key_length)
        key = string.format("0x%016x", tostring(slice_key:le_uint64()))
    elseif key_type == "NAME" then
        local slice_key_length, slice_key_length, slice_key_text
        key_length, slice_key_length, slice_key_text = dissect_length_value_amqp(buffer, key_offset, 1)
        key = slice_key_text:string()
    end
    -- value
    local value_offset = key_offset + key_length
    local slice_value_length = buffer(value_offset, 2)
    local value_length = math.max(slice_value_length:int(), 0)
    local slice_value = buffer(value_offset + 2, value_length)
    local value = ""
    if (value_length > 0) then
        value = slice_value:string()
    end
    -- result
    local record_length = key_type_length + key_length + 2 + value_length
    local label = string.format("%s: %s", key, value)
    return record_length, label
end

function dissect_and_add_amqp_application_properties_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Application Properties (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length, item_label = resolve_length_and_label_of_amqp_application_property(buffer, item_offset)
        local label = string.format("Application Property: %s", item_label)
        local application_property_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        dissect_and_add_amqp_application_property(buffer, item_offset, application_property_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_amqp_application_property(buffer, offset, tree, field_array_length, field_array_size)
    -- key
    local key_offset = offset
    local key_length, slice_key_length, slice_key_text = dissect_length_value_amqp(buffer, key_offset, 4)
    add_string_as_subtree_amqp(buffer(key_offset, key_length), tree, "Key: %s", slice_key_length,
        slice_key_text, fields.amqp_ext_application_property_key_length, fields.amqp_ext_application_property_key)
    -- value
    local value_offset = key_offset + key_length
    local value_length = add_amqp_binary_as_subtree(buffer, value_offset, tree, "Value: %s",
        fields.amqp_ext_application_property_value_length, fields.amqp_ext_application_property_value)
end

function resolve_length_and_label_of_amqp_application_property(buffer, offset)
    -- key
    local key_offset = offset
    local key_length, slice_key_length, slice_key_text = dissect_length_value_amqp(buffer, key_offset, 4)
    local key = slice_key_text:string()
    -- value
    local value_offset = key_offset + key_length
    local slice_value_length = buffer(value_offset, 2)
    local value_length = math.max(slice_value_length:int(), 0)
    local slice_bytes = buffer(value_offset + 2, value_length)
    local value
    if (value_length > 0) then
        value = slice_bytes:string()
    end
    -- result
    local record_length = key_length + 2 + value_length
    local label = string.format("%s: %s", key, value)
    return record_length, label
end

function dissect_and_add_amqp_properties_list(buffer, offset, tree, field_list_length)
    -- length
    local slice_list_length = buffer(offset, 4)
    local list_length = slice_list_length:int()
    -- size
    local slice_list_size = buffer(offset + 4, 4)
    local list_size = slice_list_size:int()
    -- fields
    local slice_list_fields = buffer(offset + 8, 8)
    local list_fields = slice_list_fields:uint64()
    local label = string.format("Properties (%d items)", list_size)
    local properties_subtree = tree:add(zilla_protocol, buffer(offset, list_length), label)
    properties_subtree:add(fields.amqp_ext_properties_length, slice_list_length)
    properties_subtree:add(fields.amqp_ext_properties_size, slice_list_size)
    properties_subtree:add(fields.amqp_ext_properties_fields, slice_list_fields)
    -- message_id
    local next_offset = offset + 16
    if field_exists(list_fields, 0) then
        local message_id_length = resolve_length_of_amqp_message_id(buffer, next_offset)
        dissect_and_add_amqp_message_id_as_subtree(buffer, next_offset, tree, "Property: Message ID", "Message ID: %s",
            fields.amqp_ext_property_message_id_type, fields.amqp_ext_property_message_id_ulong,
            fields.amqp_ext_property_message_id_uuid_length, fields.amqp_ext_property_message_id_uuid,
            fields.amqp_ext_property_message_id_binary_length, fields.amqp_ext_property_message_id_binary,
            fields.amqp_ext_property_message_id_stringtype_length, fields.amqp_ext_property_message_id_stringtype)
        next_offset = next_offset + message_id_length
    end
    -- user_id
    if field_exists(list_fields, 1) then
        local user_id_length = add_amqp_binary_as_subtree(buffer, next_offset, tree, "Property: User ID: %s",
            fields.amqp_ext_property_user_id_length, fields.amqp_ext_property_user_id)
        next_offset = next_offset + user_id_length
    end
    -- to
    if field_exists(list_fields, 2) then
        local to_length, slice_to_length, slice_to_text = dissect_length_value_amqp(buffer, next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, to_length), tree, "Property: To: %s", slice_to_length,
            slice_to_text, fields.amqp_ext_property_to_length, fields.amqp_ext_property_to)
        next_offset = next_offset + to_length
    end
    -- subject
    if field_exists(list_fields, 3) then
        local subject_length, slice_subject_length, slice_subject_text = dissect_length_value_amqp(buffer, next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, subject_length), tree, "Property: Subject: %s", slice_subject_length,
            slice_subject_text, fields.amqp_ext_property_subject_length, fields.amqp_ext_property_subject)
        next_offset = next_offset + subject_length
    end
    -- reply_to
    if field_exists(list_fields, 4) then
        local reply_to_length, slice_reply_to_length, slice_reply_to_text = dissect_length_value_amqp(buffer, next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, reply_to_length), tree, "Property: Reply To: %s", slice_reply_to_length,
            slice_reply_to_text, fields.amqp_ext_property_reply_to_length, fields.amqp_ext_property_reply_to)
        next_offset = next_offset + reply_to_length
    end
    -- correlation_id
    if field_exists(list_fields, 5) then
        local correlation_id_length = resolve_length_of_amqp_message_id(buffer, next_offset)
        dissect_and_add_amqp_message_id_as_subtree(buffer, next_offset, tree, "Property: Correlation ID", "Correlation ID: %s",
            fields.amqp_ext_property_correlation_id_type, fields.amqp_ext_property_correlation_id_ulong,
            fields.amqp_ext_property_correlation_id_uuid_length, fields.amqp_ext_property_correlation_id_uuid,
            fields.amqp_ext_property_correlation_id_binary_length, fields.amqp_ext_property_correlation_id_binary,
            fields.amqp_ext_property_correlation_id_stringtype_length, fields.amqp_ext_property_correlation_id_stringtype)
        next_offset = next_offset + correlation_id_length
    end
    -- content_type
    if field_exists(list_fields, 6) then
        local content_type_length, slice_content_type_length, slice_content_type_text = dissect_length_value_amqp(buffer,
            next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, content_type_length), tree, "Property: Content Type: %s",
            slice_content_type_length, slice_content_type_text, fields.amqp_ext_property_content_type_length,
            fields.amqp_ext_property_content_type)
        next_offset = next_offset + content_type_length
    end
    -- content_encoding
    if field_exists(list_fields, 7) then
        local content_encoding_length, slice_content_encoding_length, slice_content_encoding_text =
            dissect_length_value_amqp(buffer, next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, content_encoding_length), tree, "Property: Content Encoding: %s",
            slice_content_encoding_length, slice_content_encoding_text, fields.amqp_ext_property_content_encoding_length,
            fields.amqp_ext_property_content_encoding)
        next_offset = next_offset + content_encoding_length
    end
    -- absolute_expiry_time
    if field_exists(list_fields, 8) then
        local absolute_expiry_time_length = 8
        local slice_absolute_expiry_time = buffer(next_offset, absolute_expiry_time_length)
        tree:add(fields.amqp_ext_property_absolute_expiry_time, slice_absolute_expiry_time)
        next_offset = next_offset + absolute_expiry_time_length
    end
    -- creation_time
    if field_exists(list_fields, 9) then
        local creation_time_length = 8
        local slice_creation_time = buffer(next_offset, creation_time_length)
        tree:add(fields.amqp_ext_property_creation_time, slice_creation_time)
        next_offset = next_offset + creation_time_length
    end
    -- group_id
    if field_exists(list_fields, 10) then
        local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value_amqp(buffer,
            next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, group_id_length), tree, "Property: Group ID: %s",
            slice_group_id_length, slice_group_id_text, fields.amqp_ext_property_group_id_length,
            fields.amqp_ext_property_group_id)
        next_offset = next_offset + group_id_length
    end
    -- group_sequence
    if field_exists(list_fields, 11) then
        local group_sequence_length = 4
        local slice_group_sequence = buffer(next_offset, group_sequence_length)
        tree:add(fields.amqp_ext_property_group_sequence, slice_group_sequence)
        next_offset = next_offset + group_sequence_length
    end
    -- group_id
    if field_exists(list_fields, 12) then
        local reply_to_group_id_length, slice_reply_to_group_id_length, slice_reply_to_group_id_text =
            dissect_length_value_amqp(buffer, next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, reply_to_group_id_length), tree, "Property: Reply To Group ID: %s",
            slice_reply_to_group_id_length, slice_reply_to_group_id_text, fields.amqp_ext_property_reply_to_group_id_length,
            fields.amqp_ext_property_reply_to_group_id)
        next_offset = next_offset + reply_to_group_id_length
    end
end

function resolve_length_of_list_amqp(buffer, offset)
    local slice_list_length = buffer(offset, 4)
    return slice_list_length:int()
end

function dissect_and_add_amqp_message_id_as_subtree(buffer, offset, tree, subtree_label, value_label, field_id_type,
    field_ulong, field_uuid_length, field_uuid, field_binary_length, field_binary, field_stringtype_length, field_stringtype)
    local message_id_type_length = 1
    local slice_message_id_type = buffer(offset, message_id_type_length)
    local message_id_type = amqp_ext_message_id_types[slice_message_id_type:int()]
    next_offset = offset + message_id_type_length
    -- calculate record length
    local message_id_length, slice_message_id_length, slice_message_id_text
    if message_id_type == "ULONG" then
        message_id_length = 8
    elseif message_id_type == "UUID" then
        message_id_length = resolve_length_of_amqp_binary(buffer, next_offset)
    elseif message_id_type == "BINARY" then
        message_id_length = resolve_length_of_amqp_binary(buffer, next_offset)
    elseif message_id_type == "STRINGTYPE" then
        message_id_length, slice_message_id_length, slice_message_id_text = dissect_length_value_amqp(buffer,
            next_offset, 1)
    end
    -- add fields
    local slice_message_id = buffer(next_offset, message_id_length)
    local record_length = message_id_type_length + message_id_length
    local message_id_subtree = tree:add(zilla_protocol, buffer(offset, record_length), subtree_label)
    message_id_subtree:add(field_id_type, slice_message_id_type)
    if message_id_type == "ULONG" then
        message_id_subtree:add_le(field_ulong, slice_message_id)
    elseif message_id_type == "UUID" then
        add_amqp_binary_as_subtree(buffer, next_offset, message_id_subtree, value_label, fields.field_uuid_length,
            fields.field_uuid)
    elseif message_id_type == "BINARY" then
        add_amqp_binary_as_subtree(buffer, next_offset, message_id_subtree, value_label, field_binary_length,
            field_binary)
    elseif message_id_type == "STRINGTYPE" then
        local message_id_length, slice_message_id_length, slice_message_id_text = dissect_length_value_amqp(buffer,
            next_offset, 1)
        add_string_as_subtree_amqp(buffer(next_offset, message_id_length), message_id_subtree, value_label,
            slice_message_id_length, slice_message_id_text, field_stringtype_length, field_stringtype)
    end
end

function resolve_length_of_amqp_message_id(buffer, offset)
    local message_id_type_length = 1
    local slice_message_id_type = buffer(offset, message_id_type_length)
    local message_id_type = amqp_ext_message_id_types[slice_message_id_type:int()]
    next_offset = offset + message_id_type_length
    local message_id_length, slice_message_id_length, slice_message_id_text
    if message_id_type == "ULONG" then
        message_id_length = 8
    elseif message_id_type == "UUID" then
        message_id_length = resolve_length_of_amqp_binary(buffer, next_offset)
    elseif message_id_type == "BINARY" then
        message_id_length = resolve_length_of_amqp_binary(buffer, next_offset)
    elseif message_id_type == "STRINGTYPE" then
        message_id_length, slice_message_id_length, slice_message_id_text = dissect_length_value_amqp(buffer,
            next_offset, 1)
    end
    return message_id_type_length + message_id_length
end

function handle_amqp_abort_extension(buffer, offset, ext_subtree)
    -- condition
    local condition_offset = offset
    local condition_length, slice_condition_length, slice_condition_text = dissect_length_value_amqp(buffer, condition_offset, 1)
    add_string_as_subtree_amqp(buffer(condition_offset, condition_length), ext_subtree, "Condition: %s", slice_condition_length,
        slice_condition_text, fields.amqp_ext_condition_length, fields.amqp_ext_condition)
end

function handle_amqp_flush_extension(buffer, offset, ext_subtree)
    -- capabilities
    local capabilities_offset = offset
    local capabilities_length = 1
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    ext_subtree:add_le(fields.amqp_ext_capabilities, slice_capabilities)
end

register_dissector(AMQP_ID, "amqp", handle_amqp_extension, function (payload) return Dissector.get("amqp") end)

