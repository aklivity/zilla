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
-- mqtt dissector
MQTT_ID = 0xd0d41a76

local mqtt_ext_kinds = {
    [0] = "PUBLISH",
    [1] = "SUBSCRIBE",
    [2] = "SESSION",
}

local mqtt_ext_qos_types = {
    [0] = "AT_MOST_ONCE",
    [1] = "AT_LEAST_ONCE",
    [2] = "EXACTLY_ONCE",
}

local mqtt_ext_subscribe_flags = {
    [0] = "SEND_RETAINED",
    [1] = "RETAIN_AS_PUBLISHED",
    [2] = "NO_LOCAL",
    [3] = "RETAIN",
}

local mqtt_ext_publish_flags = {
    [0] = "RETAIN",
}

local mqtt_ext_session_flags = {
    [1] = "CLEAN_START",
    [2] = "WILL",
}

local mqtt_ext_payload_format_types = {
    [0] = "BINARY",
    [1] = "TEXT",
    [2] = "NONE",
}

local mqtt_ext_data_kinds = {
    [0] = "STATE",
    [1] = "WILL",
}

local mqtt_ext_offset_state_flags = {
    [0] = "COMPLETE",
    [1] = "INCOMPLETE",
}

add_field("mqtt_ext_kind", ProtoField.uint8("zilla.mqtt_ext.kind", "Kind", base.DEC, mqtt_ext_kinds))
--     begin
add_field("mqtt_ext_qos", ProtoField.uint8("zilla.mqtt_ext.qos", "QoS", base.DEC, mqtt_ext_qos_types))
add_field("mqtt_ext_client_id_length", ProtoField.int16("zilla.mqtt_ext.client_id_length", "Length", base.DEC))
add_field("mqtt_ext_client_id", ProtoField.string("zilla.mqtt_ext.client_id", "Client ID", base.NONE))
add_field("mqtt_ext_topic_length", ProtoField.int16("zilla.mqtt_ext.topic_length", "Length", base.DEC))
add_field("mqtt_ext_topic", ProtoField.string("zilla.mqtt_ext.topic", "Topic", base.NONE))
add_field("mqtt_ext_expiry", ProtoField.int32("zilla.mqtt_ext.expiry", "Expiry", base.DEC))
add_field("mqtt_ext_subscribe_qos_max", ProtoField.uint16("zilla.mqtt_ext.subscribe_qos_max", "Subscribe QoS Maximum", base.DEC))
add_field("mqtt_ext_publish_qos_max", ProtoField.uint16("zilla.mqtt_ext.publish_qos_max", "Publish QoS Maximum", base.DEC))
add_field("mqtt_ext_packet_size_max", ProtoField.uint32("zilla.mqtt_ext.packet_size_max", "Packet Size Maximum", base.DEC))
add_field("mqtt_ext_packet_ids_array_size", ProtoField.int8("zilla.mqtt_ext.packet_ids_array_size", "Size", base.DEC))
--     capabilities
add_field("mqtt_ext_capabilities", ProtoField.uint8("zilla.mqtt_ext.capabilities", "Capabilities", base.HEX))
add_field("mqtt_ext_capabilities_retain", ProtoField.uint8("zilla.mqtt_ext.capabilities_retain", "RETAIN",
        base.DEC, flags_types, 0x01))
add_field("mqtt_ext_capabilities_wildcard", ProtoField.uint8("zilla.mqtt_ext.capabilities_wildcard", "WILDCARD",
        base.DEC, flags_types, 0x02))
add_field("mqtt_ext_capabilities_subscription_ids", ProtoField.uint8("zilla.mqtt_ext.capabilities_subscription_ids",
        "SUBSCRIPTION_IDS", base.DEC, flags_types, 0x04))
add_field("mqtt_ext_capabilities_shared_subscriptions", ProtoField.uint8("zilla.mqtt_ext.capabilities_shared_subscriptions",
        "SHARED_SUBSCRIPTIONS", base.DEC, flags_types, 0x08))
--     subscribe flags
add_field("mqtt_ext_subscribe_flags", ProtoField.uint8("zilla.mqtt_ext.subscribe_flags", "Flags", base.HEX))
add_field("mqtt_ext_subscribe_flags_send_retained", ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_send_retained",
        "SEND_RETAINED", base.DEC, flags_types, 0x01))
add_field("mqtt_ext_subscribe_flags_retain_as_published", ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_retain_as_published",
        "RETAIN_AS_PUBLISHED", base.DEC, flags_types, 0x02))
add_field("mqtt_ext_subscribe_flags_no_local", ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_no_local",
        "NO_LOCAL", base.DEC, flags_types, 0x04))
add_field("mqtt_ext_subscribe_flags_retain", ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_retain",
        "RETAIN", base.DEC, flags_types, 0x08))
--     publish flags
add_field("mqtt_ext_publish_flags", ProtoField.uint8("zilla.mqtt_ext.publish_flags", "Flags", base.HEX))
add_field("mqtt_ext_publish_flags_retain", ProtoField.uint8("zilla.mqtt_ext.publish_flags_retain", "RETAIN", base.DEC,
        flags_types, 0x01))
--     session flags
add_field("mqtt_ext_session_flags", ProtoField.uint8("zilla.mqtt_ext.session_flags", "Flags", base.HEX))
add_field("mqtt_ext_session_flags_clean_start", ProtoField.uint8("zilla.mqtt_ext.session_flags_clean_start", "CLEAN_START",
        base.DEC, flags_types, 0x02))
add_field("mqtt_ext_session_flags_will", ProtoField.uint8("zilla.mqtt_ext.session_flags_will", "WILL", base.DEC, flags_types, 0x04))
--     filters
add_field("mqtt_ext_filters_array_length", ProtoField.int8("zilla.mqtt_ext.filters_array_length", "Length", base.DEC))
add_field("mqtt_ext_filters_array_size", ProtoField.int8("zilla.mqtt_ext.filters_array_size", "Size", base.DEC))
add_field("mqtt_ext_filter_subscription_id", ProtoField.uint32("zilla.mqtt_ext.filter_subscription_id", "Subscription ID", base.HEX))
add_field("mqtt_ext_filter_qos", ProtoField.uint8("zilla.mqtt_ext.filter_qos", "QoS", base.DEC, mqtt_ext_qos_types))
add_field("mqtt_ext_filter_reason_code", ProtoField.uint8("zilla.mqtt_ext.filter_reason_code", "Reason Code", base.DEC))
add_field("mqtt_ext_filter_pattern_length", ProtoField.int16("zilla.mqtt_ext.filter_pattern_length", "Length", base.DEC))
add_field("mqtt_ext_filter_pattern", ProtoField.string("zilla.mqtt_ext.filter_pattern", "Pattern", base.NONE))
--     data
add_field("mqtt_ext_deferred", ProtoField.uint32("zilla.mqtt_ext.deferred", "Deferred", base.DEC))
add_field("mqtt_ext_expiry_interval", ProtoField.int16("zilla.mqtt_ext.expiry_interval", "Expiry Interval", base.DEC))
add_field("mqtt_ext_content_type_length", ProtoField.int16("zilla.mqtt_ext.content_type_length", "Length", base.DEC))
add_field("mqtt_ext_content_type", ProtoField.string("zilla.mqtt_ext.content_type", "Content Type", base.NONE))
add_field("mqtt_ext_payload_format", ProtoField.uint8("zilla.mqtt_ext.payload_format", "Payload Format", base.DEC,
        mqtt_ext_payload_format_types))
add_field("mqtt_ext_response_topic_length", ProtoField.int16("zilla.mqtt_ext.response_topic_length", "Length", base.DEC))
add_field("mqtt_ext_response_topic", ProtoField.string("zilla.mqtt_ext.response_topic", "Response Topic", base.NONE))
add_field("mqtt_ext_correlation_length", ProtoField.int16("zilla.mqtt_ext.correlation_length", "Length", base.DEC))
add_field("mqtt_ext_correlation", ProtoField.bytes("zilla.mqtt_ext.correlation", "Correlation", base.NONE))
add_field("mqtt_ext_properties_array_length", ProtoField.int8("zilla.mqtt_ext.properties_array_length", "Length", base.DEC))
add_field("mqtt_ext_properties_array_size", ProtoField.int8("zilla.mqtt_ext.properties_array_size", "Size", base.DEC))
add_field("mqtt_ext_property_key_length", ProtoField.int16("zilla.mqtt_ext.property_key_length", "Length", base.DEC))
add_field("mqtt_ext_property_key", ProtoField.string("zilla.mqtt_ext.property_key", "Key", base.NONE))
add_field("mqtt_ext_property_value_length", ProtoField.int16("zilla.mqtt_ext.property_value_length", "Length", base.DEC))
add_field("mqtt_ext_property_value", ProtoField.string("zilla.mqtt_ext.property_value", "Value", base.NONE))
add_field("mqtt_ext_data_kind", ProtoField.uint8("zilla.mqtt_ext.data_kind", "Data Kind", base.HEX, mqtt_ext_data_kinds))
add_field("mqtt_ext_packet_id", ProtoField.uint16("zilla.mqtt_ext.packet_id", "Packet ID", base.HEX))
add_field("mqtt_ext_subscription_ids_array_length", ProtoField.int8("zilla.mqtt_ext.subscription_ids_array_length", "Length",
        base.DEC))
add_field("mqtt_ext_subscription_ids_array_size", ProtoField.int8("zilla.mqtt_ext.subscription_ids_array_size", "Size",
        base.DEC))
add_field("mqtt_ext_subscription_id_varuint", ProtoField.bytes("zilla.mqtt_ext.subsciption_id_varuint", "Subscription ID (varuint32)",
        base.NONE))
add_field("mqtt_ext_subscription_id", ProtoField.int32("zilla.mqtt_ext.subsciption_id", "Subscription ID", base.DEC))
--     reset
add_field("mqtt_ext_server_ref_length", ProtoField.int16("zilla.mqtt_ext.server_ref_length", "Length", base.DEC))
add_field("mqtt_ext_server_ref", ProtoField.string("zilla.mqtt_ext.server_ref", "Value", base.NONE))
add_field("mqtt_ext_reason_code", ProtoField.uint8("zilla.mqtt_ext.reason_code", "Reason Code", base.DEC))
add_field("mqtt_ext_reason_length", ProtoField.int16("zilla.mqtt_ext.reason_length", "Length", base.DEC))
add_field("mqtt_ext_reason", ProtoField.string("zilla.mqtt_ext.reason", "Value", base.NONE))
--     reset
add_field("mqtt_ext_state", ProtoField.uint8("zilla.mqtt_ext.state", "State", base.DEC, mqtt_ext_offset_state_flags))

function handle_mqtt_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == DATA_ID or frame_type_id == FLUSH_ID or
            frame_type_id == REDIRECT_ID then
        local kind_length = 1
        local slice_kind = buffer(offset, kind_length)
        local kind = mqtt_ext_kinds[slice_kind:le_int()]
        ext_subtree:add_le(fields.mqtt_ext_kind, slice_kind)
        if frame_type_id == BEGIN_ID or frame_type_id == REDIRECT_ID then
            if kind == "PUBLISH" then
                handle_mqtt_begin_publish_extension(buffer, offset + kind_length, ext_subtree)
            elseif kind == "SUBSCRIBE" then
                handle_mqtt_begin_subscribe_extension(buffer, offset + kind_length, ext_subtree)
            elseif kind == "SESSION" then
                handle_mqtt_begin_session_extension(buffer, offset + kind_length, ext_subtree)
            end
        elseif frame_type_id == DATA_ID then
            if kind == "PUBLISH" then
                handle_mqtt_data_publish_extension(buffer, offset + kind_length, ext_subtree)
            elseif kind == "SUBSCRIBE" then
                handle_mqtt_data_subscribe_extension(buffer, offset + kind_length, ext_subtree)
            elseif kind == "SESSION" then
                handle_mqtt_data_session_extension(buffer, offset + kind_length, ext_subtree)
            end
        elseif frame_type_id == FLUSH_ID then
            if kind == "SUBSCRIBE" then
                handle_mqtt_flush_subscribe_extension(buffer, offset + kind_length, ext_subtree)
            elseif kind == "SESSION" then
                handle_mqtt_flush_session_extension(buffer, offset + kind_length, ext_subtree)
            end
        end
    elseif frame_type_id == RESET_ID then
        handle_mqtt_reset_extension(buffer, offset, ext_subtree)
    end
end

function handle_mqtt_begin_subscribe_extension(buffer, offset, ext_subtree)
    -- client_id
    local client_id_offset = offset
    local client_id_length, slice_client_id_length, slice_client_id_text = dissect_length_value(buffer, client_id_offset, 2)
    add_string_as_subtree(buffer(client_id_offset, client_id_length), ext_subtree, "Client ID: %s",
        slice_client_id_length, slice_client_id_text, fields.mqtt_ext_client_id_length, fields.mqtt_ext_client_id)
    -- qos
    local qos_offset = client_id_offset + client_id_length
    local qos_length = 1
    local slice_qos = buffer(qos_offset, qos_length)
    ext_subtree:add_le(fields.mqtt_ext_qos, slice_qos)
    -- topic_filters
    local topic_filters_offset = qos_offset + qos_length
    dissect_and_add_mqtt_topic_filters(buffer, topic_filters_offset, ext_subtree)
end

function dissect_and_add_mqtt_topic_filters(buffer, offset, tree)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Topic Filters (%d items)",
        fields.mqtt_ext_filters_array_length, fields.mqtt_ext_filters_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- subscription_id
        local subscription_id_offset = item_offset
        local subscription_id_length = 4
        local slice_subscription_id = buffer(subscription_id_offset, subscription_id_length)
        -- qos
        local qos_offset = subscription_id_offset + subscription_id_length
        local qos_length = 1
        local slice_qos = buffer(qos_offset, qos_length)
        -- flags
        local flags_offset = qos_offset + qos_length
        local flags_length = 1
        local slice_flags = buffer(flags_offset, flags_length)
        local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
        -- reason_code
        local reason_code_offset = flags_offset + flags_length
        local reason_code_length = 1
        local slice_reason_code = buffer(reason_code_offset, reason_code_length)
        -- pattern
        local pattern_offset = reason_code_offset + reason_code_length
        local pattern_length, slice_pattern_length, slice_pattern_text = dissect_length_value(buffer, pattern_offset, 2)
        -- add fields
        local record_length = subscription_id_length + qos_length + flags_length + reason_code_length + pattern_length
        local label = string.format("Topic Filter: %s", slice_pattern_text:string())
        local item_subtree = tree:add(zilla_protocol, buffer(item_offset, record_length), label)
        item_subtree:add_le(fields.mqtt_ext_filter_subscription_id, slice_subscription_id)
        item_subtree:add_le(fields.mqtt_ext_filter_qos, slice_qos)
        local flags_subtree = item_subtree:add(zilla_protocol, slice_flags, flags_label)
        flags_subtree:add_le(fields.mqtt_ext_subscribe_flags_send_retained, slice_flags)
        flags_subtree:add_le(fields.mqtt_ext_subscribe_flags_retain_as_published, slice_flags)
        flags_subtree:add_le(fields.mqtt_ext_subscribe_flags_no_local, slice_flags)
        flags_subtree:add_le(fields.mqtt_ext_subscribe_flags_retain, slice_flags)
        item_subtree:add_le(fields.mqtt_ext_filter_reason_code, slice_reason_code)
        add_string_as_subtree(buffer(pattern_offset, pattern_length), item_subtree, "Pattern: %s",
            slice_pattern_length, slice_pattern_text, fields.mqtt_ext_filter_pattern_length, fields.mqtt_ext_filter_pattern)
        -- next
        item_offset = item_offset + record_length
    end
end

function handle_mqtt_begin_publish_extension(buffer, offset, ext_subtree)
    -- client_id
    local client_id_offset = offset
    local client_id_length, slice_client_id_length, slice_client_id_text = dissect_length_value(buffer, client_id_offset, 2)
    add_string_as_subtree(buffer(client_id_offset, client_id_length), ext_subtree, "Client ID: %s",
        slice_client_id_length, slice_client_id_text, fields.mqtt_ext_client_id_length, fields.mqtt_ext_client_id)
    -- topic
    local topic_offset = client_id_offset + client_id_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.mqtt_ext_topic_length, fields.mqtt_ext_topic)
    -- flags
    local flags_offset = topic_offset + topic_length
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = ext_subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.mqtt_ext_publish_flags_retain, slice_flags)
    -- qos
    local qos_offset = flags_offset + flags_length
    local qos_length = 1
    local slice_qos = buffer(qos_offset, qos_length)
    ext_subtree:add_le(fields.mqtt_ext_qos, slice_qos)
end

function handle_mqtt_begin_session_extension(buffer, offset, ext_subtree)
    -- flags
    local flags_offset = offset
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = ext_subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.mqtt_ext_session_flags_clean_start, slice_flags)
    flags_subtree:add_le(fields.mqtt_ext_session_flags_will, slice_flags)
    -- expiry
    local expiry_offset = flags_offset + flags_length
    local expiry_length = 4
    local slice_expiry = buffer(expiry_offset, expiry_length)
    ext_subtree:add_le(fields.mqtt_ext_expiry, slice_expiry)
    -- subscribe_qos_max
    local subscribe_qos_max_offset = expiry_offset + expiry_length
    local subscribe_qos_max_length = 2
    local slice_subscribe_qos_max = buffer(subscribe_qos_max_offset, subscribe_qos_max_length)
    ext_subtree:add_le(fields.mqtt_ext_subscribe_qos_max, slice_subscribe_qos_max)
    -- publish_qos_max
    local publish_qos_max_offset = subscribe_qos_max_offset + subscribe_qos_max_length
    local publish_qos_max_length = 2
    local slice_publish_qos_max = buffer(publish_qos_max_offset, publish_qos_max_length)
    ext_subtree:add_le(fields.mqtt_ext_publish_qos_max, slice_publish_qos_max)
    -- packet_size_max
    local packet_size_max_offset = publish_qos_max_offset + publish_qos_max_length
    local packet_size_max_length = 4
    local slice_packet_size_max = buffer(packet_size_max_offset, packet_size_max_length)
    ext_subtree:add_le(fields.mqtt_ext_packet_size_max, slice_packet_size_max)
    -- capabilities
    local capabilities_offset = packet_size_max_offset + packet_size_max_length
    local capabilities_length = 1
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    local capabilities_label = string.format("Capabilities: 0x%02x", slice_capabilities:le_uint())
    local capabilities_subtree = ext_subtree:add(zilla_protocol, slice_capabilities, capabilities_label)
    capabilities_subtree:add_le(fields.mqtt_ext_capabilities_retain, slice_capabilities)
    capabilities_subtree:add_le(fields.mqtt_ext_capabilities_wildcard, slice_capabilities)
    capabilities_subtree:add_le(fields.mqtt_ext_capabilities_subscription_ids, slice_capabilities)
    capabilities_subtree:add_le(fields.mqtt_ext_capabilities_shared_subscriptions, slice_capabilities)
    -- client_id
    local client_id_offset = capabilities_offset + capabilities_length
    local client_id_length, slice_client_id_length, slice_client_id_text = dissect_length_value(buffer, client_id_offset, 2)
    add_string_as_subtree(buffer(client_id_offset, client_id_length), ext_subtree, "Client ID: %s",
        slice_client_id_length, slice_client_id_text, fields.mqtt_ext_client_id_length, fields.mqtt_ext_client_id)
    -- packet_ids
    local packet_ids_offset = client_id_offset + client_id_length
    local next_offset = dissect_and_add_mqtt_packet_ids(buffer, packet_ids_offset, ext_subtree)
end

function dissect_and_add_mqtt_packet_ids(buffer, offset, tree)
    local size_length = 1
    local slice_array_size = buffer(offset, size_length)
    local array_size = slice_array_size:le_int();
    local label = string.format("Packet IDs (%d items)", array_size)
    local array_subtree = tree:add(zilla_protocol, slice_array_size, label)
    array_subtree:add_le(fields.mqtt_ext_packet_ids_array_size, slice_array_size)
    local item_offset = offset + size_length
    for i = 1, array_size do
        -- packet_id
        local item_length = 2
        local slice_packet_id = buffer(item_offset, item_length)
        local label = string.format("Packet ID: 0x%04x", slice_packet_id:le_int())
        local item_subtree = tree:add(zilla_protocol, slice_packet_id, label)
        item_subtree:add_le(fields.mqtt_ext_packet_id, slice_packet_id)
        item_offset = item_offset + item_length
    end
    return item_offset
end

function handle_mqtt_data_publish_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.mqtt_ext_deferred, slice_deferred)
    -- qos
    local qos_offset = deferred_offset + deferred_length
    local qos_length = 1
    local slice_qos = buffer(qos_offset, qos_length)
    ext_subtree:add_le(fields.mqtt_ext_qos, slice_qos)
    -- flags
    local flags_offset = qos_offset + qos_length
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = ext_subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.mqtt_ext_publish_flags_retain, slice_flags)
    -- packet_id
    local packet_id_offset = flags_offset + flags_length
    local packet_id_length = 2
    local slice_packet_id = buffer(packet_id_offset, packet_id_length)
    ext_subtree:add_le(fields.mqtt_ext_packet_id, slice_packet_id)
    -- expiry_interval
    local expiry_interval_offset = packet_id_offset + packet_id_length
    local expiry_interval_length = 4
    local slice_expiry_interval = buffer(expiry_interval_offset, expiry_interval_length)
    ext_subtree:add_le(fields.mqtt_ext_expiry_interval, slice_expiry_interval)
    -- content_type
    local content_type_offset = expiry_interval_offset + expiry_interval_length
    local content_type_length, slice_content_type_length, slice_content_type_text = dissect_length_value(buffer, content_type_offset, 2)
    add_string_as_subtree(buffer(content_type_offset, content_type_length), ext_subtree, "Content Type: %s",
        slice_content_type_length, slice_content_type_text, fields.mqtt_ext_content_type_length, fields.mqtt_ext_content_type)
    -- payload_format
    local payload_format_offset = content_type_offset + content_type_length
    local payload_format_length = 1
    slice_payload_format = buffer(payload_format_offset, payload_format_length)
    ext_subtree:add_le(fields.mqtt_ext_payload_format, slice_payload_format)
    -- response_topic
    local response_topic_offset = payload_format_offset + payload_format_length
    local response_topic_length, slice_response_topic_length, slice_response_topic_text = dissect_length_value(buffer, response_topic_offset, 2)
    add_string_as_subtree(buffer(response_topic_offset, response_topic_length), ext_subtree, "Response Topic: %s",
        slice_response_topic_length, slice_response_topic_text, fields.mqtt_ext_response_topic_length, fields.mqtt_ext_response_topic)
    -- correlation
    local correlation_offset = response_topic_offset + response_topic_length
    local correlation_length = add_mqtt_binary_as_subtree(buffer, correlation_offset, ext_subtree, "Correlation",
        fields.mqtt_ext_correlation_length, fields.mqtt_ext_correlation)
    -- properties
    local properties_offset = correlation_offset + correlation_length
    dissect_and_add_mqtt_properties(buffer, properties_offset, ext_subtree)
end

function add_mqtt_binary_as_subtree(buffer, offset, tree, label, field_length, field_bytes)
    local slice_length = buffer(offset, 4)
    local length = math.max(slice_length:le_int(), 0)
    local slice_bytes = buffer(offset + 4, length)
    local subtree = tree:add(zilla_protocol, buffer(offset, 4 + length), label)
    subtree:add_le(field_length, slice_length)
    if (length > 0) then
        subtree:add(field_bytes, slice_bytes)
    end
    return 4 + length
end

function dissect_and_add_mqtt_properties(buffer, offset, tree)
    local slice_properties_array_length = buffer(offset, 4)
    local slice_properties_array_size = buffer(offset + 4, 4)
    local properties_array_size = slice_properties_array_size:le_int()
    local length = 8
    local label = string.format("Properties (%d items)", properties_array_size)
    local properties_array_subtree = tree:add(zilla_protocol, buffer(offset, length), label)
    properties_array_subtree:add_le(fields.mqtt_ext_properties_array_length, slice_properties_array_length)
    properties_array_subtree:add_le(fields.mqtt_ext_properties_array_size, slice_properties_array_size)
    local item_offset = offset + length
    for i = 1, properties_array_size do
        -- key
        local key_offset = item_offset
        local key_length, slice_key_length, slice_key_text = dissect_length_value(buffer, key_offset, 2)
        -- value
        local value_offset = key_offset + key_length
        local value_length, slice_value_length, slice_value_text = dissect_length_value(buffer, value_offset, 2)
        -- add fields
        local record_length = key_length + value_length
        local label = string.format("Property: %s: %s", slice_key_text:string(), slice_value_text:string())
        local subtree = tree:add(zilla_protocol, buffer(item_offset, record_length), label)
        add_string_as_subtree(buffer(key_offset, key_length), subtree, "Key: %s",
            slice_key_length, slice_key_text, fields.mqtt_ext_property_key_length, fields.mqtt_ext_property_key)
        add_string_as_subtree(buffer(value_offset, value_length), subtree, "Value: %s",
            slice_value_length, slice_value_text, fields.mqtt_ext_property_value_length, fields.mqtt_ext_property_value)
        -- next
        item_offset = item_offset + record_length
    end
end

function handle_mqtt_data_subscribe_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.mqtt_ext_deferred, slice_deferred)
    -- topic
    local topic_offset = deferred_offset + deferred_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.mqtt_ext_topic_length, fields.mqtt_ext_topic)
    -- packet_id
    local packet_id_offset = topic_offset + topic_length
    local packet_id_length = 2
    local slice_packet_id = buffer(packet_id_offset, packet_id_length)
    ext_subtree:add_le(fields.mqtt_ext_packet_id, slice_packet_id)
    -- qos
    local qos_offset = packet_id_offset + packet_id_length
    local qos_length = 1
    local slice_qos = buffer(qos_offset, qos_length)
    ext_subtree:add_le(fields.mqtt_ext_qos, slice_qos)
    -- flags
    local flags_offset = qos_offset + qos_length
    local flags_length = 1
    local slice_flags = buffer(flags_offset, flags_length)
    local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
    local flags_subtree = ext_subtree:add(zilla_protocol, slice_flags, flags_label)
    flags_subtree:add_le(fields.mqtt_ext_publish_flags_retain, slice_flags)
    -- subscription_ids
    local subscription_ids_offset = flags_offset + flags_length
    local next_offset = dissect_and_add_mqtt_subscription_ids(buffer, subscription_ids_offset, ext_subtree)
    -- expiry_interval
    local expiry_interval_offset = next_offset
    local expiry_interval_length = 4
    local slice_expiry_interval = buffer(expiry_interval_offset, expiry_interval_length)
    ext_subtree:add_le(fields.mqtt_ext_expiry_interval, slice_expiry_interval)
    -- content_type
    local content_type_offset = expiry_interval_offset + expiry_interval_length
    local content_type_length, slice_content_type_length, slice_content_type_text = dissect_length_value(buffer, content_type_offset, 2)
    add_string_as_subtree(buffer(content_type_offset, content_type_length), ext_subtree, "Content Type: %s",
        slice_content_type_length, slice_content_type_text, fields.mqtt_ext_content_type_length, fields.mqtt_ext_content_type)
    -- payload_format
    local payload_format_offset = content_type_offset + content_type_length
    local payload_format_length = 1
    slice_payload_format = buffer(payload_format_offset, payload_format_length)
    ext_subtree:add_le(fields.mqtt_ext_payload_format, slice_payload_format)
    -- response_topic
    local response_topic_offset = payload_format_offset + payload_format_length
    local response_topic_length, slice_response_topic_length, slice_response_topic_text = dissect_length_value(buffer, response_topic_offset, 2)
    add_string_as_subtree(buffer(response_topic_offset, response_topic_length), ext_subtree, "Response Topic: %s",
        slice_response_topic_length, slice_response_topic_text, fields.mqtt_ext_response_topic_length, fields.mqtt_ext_response_topic)
    -- correlation
    local correlation_offset = response_topic_offset + response_topic_length
    local correlation_length = add_mqtt_binary_as_subtree(buffer, correlation_offset, ext_subtree, "Correlation",
        fields.mqtt_ext_correlation_length, fields.mqtt_ext_correlation)
    -- properties
    local properties_offset = correlation_offset + correlation_length
    dissect_and_add_mqtt_properties(buffer, properties_offset, ext_subtree)
end

function dissect_and_add_mqtt_subscription_ids(buffer, offset, tree)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Subscription IDs (%d items)",
        fields.mqtt_ext_subscription_ids_array_length, fields.mqtt_ext_subscription_ids_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- subscription_id
        local subscription_id, slice_subscription_id_varuint, subscription_id_length = decode_varuint32(buffer, item_offset)
        add_varint_as_subtree(buffer(item_offset, subscription_id_length), tree, "Subscription ID: %d",
            slice_subscription_id_varuint, subscription_id, fields.mqtt_ext_subscription_id_varuint, fields.mqtt_ext_subscription_id)
        -- next
        item_offset = item_offset + subscription_id_length
    end
    return item_offset
end

function handle_mqtt_data_session_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.mqtt_ext_deferred, slice_deferred)
    -- data_kind
    local data_kind_offset = deferred_offset + deferred_length
    local data_kind_length = 1
    slice_data_kind = buffer(data_kind_offset, data_kind_length)
    ext_subtree:add_le(fields.mqtt_ext_data_kind, slice_data_kind)
end

function handle_mqtt_flush_subscribe_extension(buffer, offset, ext_subtree)
    -- qos
    local qos_offset = offset
    local qos_length = 1
    local slice_qos = buffer(qos_offset, qos_length)
    ext_subtree:add_le(fields.mqtt_ext_qos, slice_qos)
    -- packet_id
    local packet_id_offset = qos_offset + qos_length
    local packet_id_length = 2
    local slice_packet_id = buffer(packet_id_offset, packet_id_length)
    ext_subtree:add_le(fields.mqtt_ext_packet_id, slice_packet_id)
    -- state
    local state_offset = packet_id_offset + packet_id_length
    local state_length = 1
    local slice_state = buffer(state_offset, state_length)
    ext_subtree:add_le(fields.mqtt_ext_state, slice_state)
    -- topic_filters
    local topic_filters_offset = state_offset + state_length
    dissect_and_add_mqtt_topic_filters(buffer, topic_filters_offset, ext_subtree)
end

function handle_mqtt_flush_session_extension(buffer, offset, ext_subtree)
    -- packet_id
    local packet_id_offset = offset
    local packet_id_length = 2
    local slice_packet_id = buffer(packet_id_offset, packet_id_length)
    ext_subtree:add_le(fields.mqtt_ext_packet_id, slice_packet_id)
end

function handle_mqtt_reset_extension(buffer, offset, ext_subtree)
    -- server_ref
    local server_ref_offset = offset
    local server_ref_length, slice_server_ref_length, slice_server_ref_text = dissect_length_value(buffer, server_ref_offset, 2)
    add_string_as_subtree(buffer(server_ref_offset, server_ref_length), ext_subtree, "Server Reference: %s",
        slice_server_ref_length, slice_server_ref_text, fields.mqtt_ext_server_ref_length, fields.mqtt_ext_server_ref)
    -- reason_code
    local reason_code_offset = server_ref_offset + server_ref_length
    local reason_code_length = 1
    local slice_reason_code = buffer(reason_code_offset, reason_code_length)
    ext_subtree:add_le(fields.mqtt_ext_reason_code, slice_reason_code)
    -- reason
    local reason_offset = reason_code_offset + reason_code_length
    local reason_length, slice_reason_length, slice_reason_text = dissect_length_value(buffer, reason_offset, 2)
    add_string_as_subtree(buffer(reason_offset, reason_length), ext_subtree, "Reason: %s",
        slice_reason_length, slice_reason_text, fields.mqtt_ext_reason_length, fields.mqtt_ext_reason)
end

register_dissector(MQTT_ID, "mqtt", handle_mqtt_extension, function (payload) return Dissector.get("mqtt") end)

