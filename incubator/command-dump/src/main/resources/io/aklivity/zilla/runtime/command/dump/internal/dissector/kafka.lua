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
-- kafka dissector
KAFKA_ID = 0x084b20e1

local kafka_ext_apis = {
    [252] = "CONSUMER",
    [253] = "GROUP",
    [254] = "BOOTSTRAP",
    [255] = "MERGED",
    [22]  = "INIT_PRODUCER_ID",
    [3]   = "META",
    [8]   = "OFFSET_COMMIT",
    [9]   = "OFFSET_FETCH",
    [32]  = "DESCRIBE",
    [1]   = "FETCH",
    [0]   = "PRODUCE",
}

local kafka_ext_capabilities_types = {
    [1] = "PRODUCE_ONLY",
    [2] = "FETCH_ONLY",
    [3] = "PRODUCE_AND_FETCH",
}

local kafka_ext_evaluation_types = {
    [0] = "LAZY",
    [1] = "EAGER",
}

local kafka_ext_isolation_types = {
    [0] = "READ_UNCOMMITTED",
    [1] = "READ_COMMITTED",
}

local kafka_ext_delta_types = {
    [0] = "NONE",
    [1] = "JSON_PATCH",
}

local kafka_ext_ack_modes = {
    [0] = "NONE",
    [1] = "LEADER_ONLY",
    [-1] = "IN_SYNC_REPLICAS",
}

local kafka_ext_condition_types = {
    [0] = "KEY",
    [1] = "HEADER",
    [2] = "NOT",
    [3] = "HEADERS",
}

local kafka_ext_value_match_types = {
    [0] = "VALUE",
    [1] = "SKIP",
}

local kafka_ext_skip_types = {
    [0] = "SKIP",
    [1] = "SKIP_MANY",
}

local kafka_ext_transaction_result_types = {
    [0] = "ABORT",
    [1] = "COMMIT",
}

add_field("kafka_ext_api", ProtoField.uint8("zilla.kafka_ext.api", "API", base.DEC, kafka_ext_apis))
--     reset
add_field("kafka_ext_error", ProtoField.int32("zilla.kafka_ext.error", "Error", base.DEC))
--     consumer
add_field("kafka_ext_group_id_length", ProtoField.int16("zilla.kafka_ext.group_id_length", "Length", base.DEC))
add_field("kafka_ext_group_id", ProtoField.string("zilla.kafka_ext.group_id", "Group ID", base.NONE))
add_field("kafka_ext_consumer_id_length", ProtoField.int16("zilla.kafka_ext.consumer_id_length", "Length", base.DEC))
add_field("kafka_ext_consumer_id", ProtoField.string("zilla.kafka_ext.consumer_id", "Consumer ID", base.NONE))
add_field("kafka_ext_host_length", ProtoField.int16("zilla.kafka_ext.host_length", "Length", base.DEC))
add_field("kafka_ext_host", ProtoField.string("zilla.kafka_ext.host", "Host", base.NONE))
add_field("kafka_ext_port", ProtoField.int32("zilla.kafka_ext.port", "Port", base.DEC))
add_field("kafka_ext_timeout", ProtoField.int32("zilla.kafka_ext.timeout", "Timeout", base.DEC))
add_field("kafka_ext_topic_length", ProtoField.int16("zilla.kafka_ext.topic_length", "Length", base.DEC))
add_field("kafka_ext_topic", ProtoField.string("zilla.kafka_ext.topic", "Topic", base.NONE))
add_field("kafka_ext_partition_ids_array_length", ProtoField.int8("zilla.kafka_ext.partition_ids_array_length", "Length", base.DEC))
add_field("kafka_ext_partition_ids_array_size", ProtoField.int8("zilla.kafka_ext.partition_ids_array_size", "Size", base.DEC))
add_field("kafka_ext_partition_id", ProtoField.int32("zilla.kafka_ext.partition_id", "Partition ID", base.DEC))
add_field("kafka_ext_consumer_assignments_array_length", ProtoField.int8("zilla.kafka_ext.consumer_assignments_array_length",
        "Length", base.DEC))
add_field("kafka_ext_consumer_assignments_array_size", ProtoField.int8("zilla.kafka_ext.consumer_assignments_array_size",
        "Size", base.DEC))
add_field("kafka_ext_partition_offset", ProtoField.int64("zilla.kafka_ext.partition_offset", "Partition Offset", base.DEC))
add_field("kafka_ext_stable_offset", ProtoField.int64("zilla.kafka_ext.stable_offset", "Stable Offset", base.DEC))
add_field("kafka_ext_latest_offset", ProtoField.int64("zilla.kafka_ext.latest_offset", "Latest Offset", base.DEC))
add_field("kafka_ext_metadata_length", ProtoField.int32("zilla.kafka_ext.metadata_length", "Length", base.DEC))
add_field("kafka_ext_metadata", ProtoField.string("zilla.kafka_ext.metadata", "Metadata", base.NONE))
add_field("kafka_ext_leader_epoch", ProtoField.int32("zilla.kafka_ext.leader_epoch", "Leader Epoch", base.DEC))
add_field("kafka_ext_correlation_id", ProtoField.int64("zilla.kafka_ext.correlation_id", "Correlation ID", base.DEC))
--     group
add_field("kafka_ext_protocol_length", ProtoField.int16("zilla.kafka_ext.protocol_length", "Length", base.DEC))
add_field("kafka_ext_protocol", ProtoField.string("zilla.kafka_ext.protocol", "Protocol", base.NONE))
add_field("kafka_ext_instance_id_length", ProtoField.int16("zilla.kafka_ext.instance_id_length", "Length", base.DEC))
add_field("kafka_ext_instance_id", ProtoField.string("zilla.kafka_ext.instance_id", "Instance ID", base.NONE))
add_field("kafka_ext_metadata_length_varint", ProtoField.bytes("zilla.kafka_ext.metadata_length_varint", "Length (varint32)", base.NONE))
add_field("kafka_ext_metadata_bytes", ProtoField.bytes("zilla.kafka_ext.metadata_bytes", "Metadata", base.NONE))
add_field("kafka_ext_generation_id", ProtoField.int32("zilla.kafka_ext.generation_id", "Generation ID", base.DEC))
add_field("kafka_ext_leader_id_length", ProtoField.int16("zilla.kafka_ext.leader_id_length", "Length", base.DEC))
add_field("kafka_ext_leader_id", ProtoField.string("zilla.kafka_ext.leader_id", "Leader ID", base.NONE))
add_field("kafka_ext_member_id_length", ProtoField.int16("zilla.kafka_ext.member_id_length", "Length", base.DEC))
add_field("kafka_ext_member_id", ProtoField.string("zilla.kafka_ext.member_id", "Member ID", base.NONE))
-- merged
add_field("kafka_ext_capabilities", ProtoField.uint8("zilla.kafka_ext.capabilities", "Capabilities", base.DEC,
        kafka_ext_capabilities_types))
add_field("kafka_ext_partitions_array_length", ProtoField.int8("zilla.kafka_ext.partitions_array_length", "Length", base.DEC))
add_field("kafka_ext_partitions_array_size", ProtoField.int8("zilla.kafka_ext.partitions_array_size", "Size", base.DEC))
add_field("kafka_ext_filters_array_length", ProtoField.int8("zilla.kafka_ext.filters_array_length", "Length", base.DEC))
add_field("kafka_ext_filters_array_size", ProtoField.int8("zilla.kafka_ext.filters_array_size", "Size", base.DEC))
add_field("kafka_ext_conditions_array_length", ProtoField.int8("zilla.kafka_ext.conditions_array_length", "Length", base.DEC))
add_field("kafka_ext_conditions_array_size", ProtoField.int8("zilla.kafka_ext.conditions_array_size", "Size", base.DEC))
add_field("kafka_ext_condition_type", ProtoField.int8("zilla.kafka_ext.condition_type", "Type", base.DEC, kafka_ext_condition_types))
add_field("kafka_ext_key_length_varint", ProtoField.bytes("zilla.kafka_ext.key_length_varint", "Length (varint32)", base.NONE))
add_field("kafka_ext_key_length", ProtoField.int32("zilla.kafka_ext.key_length", "Length", base.DEC))
add_field("kafka_ext_key", ProtoField.string("zilla.kafka_ext.key", "Key", base.NONE))
add_field("kafka_ext_name_length_varint", ProtoField.bytes("zilla.kafka_ext.name_length_varint", "Length (varint32)", base.NONE))
add_field("kafka_ext_name_length", ProtoField.int32("zilla.kafka_ext.name_length", "Length", base.DEC))
add_field("kafka_ext_name", ProtoField.string("zilla.kafka_ext.name", "Name", base.NONE))
add_field("kafka_ext_value_length_varint", ProtoField.bytes("zilla.kafka_ext.value_length_varint", "Length (varint32)", base.NONE))
add_field("kafka_ext_value_length", ProtoField.int32("zilla.kafka_ext.value_length", "Length", base.DEC))
add_field("kafka_ext_value", ProtoField.string("zilla.kafka_ext.value", "Value", base.NONE))
add_field("kafka_ext_value_match_array_length", ProtoField.int8("zilla.kafka_ext.value_match_array_length", "Length", base.DEC))
add_field("kafka_ext_value_match_array_size", ProtoField.int8("zilla.kafka_ext.value_match_array_size", "Size", base.DEC))
add_field("kafka_ext_value_match_type", ProtoField.uint8("zilla.kafka_ext.value_match_type", "Type", base.DEC,
        kafka_ext_value_match_types))
add_field("kafka_ext_skip_type", ProtoField.uint8("zilla.kafka_ext.skip_type", "Skip Type", base.DEC, kafka_ext_skip_types))
add_field("kafka_ext_evaluation", ProtoField.uint8("zilla.kafka_ext.evaluation", "Evaluation", base.DEC, kafka_ext_evaluation_types))
add_field("kafka_ext_isolation", ProtoField.uint8("zilla.kafka_ext.isolation", "Isolation", base.DEC, kafka_ext_isolation_types))
add_field("kafka_ext_delta_type", ProtoField.uint8("zilla.kafka_ext.delta_type", "Delta Type", base.DEC, kafka_ext_delta_types))
add_field("kafka_ext_ack_mode_id", ProtoField.int16("zilla.kafka_ext.ack_mode_id", "Ack Mode ID", base.DEC))
add_field("kafka_ext_ack_mode", ProtoField.string("zilla.kafka_ext.ack_mode", "Ack Mode", base.NONE))
add_field("kafka_ext_merged_api", ProtoField.uint8("zilla.kafka_ext.data_api", "Merged API", base.DEC, kafka_ext_apis))
add_field("kafka_ext_deferred", ProtoField.int32("zilla.kafka_ext.deferred", "Deferred", base.DEC))
add_field("kafka_ext_filters", ProtoField.int64("zilla.kafka_ext.filters", "Filters", base.DEC))
add_field("kafka_ext_progress_array_length", ProtoField.int8("zilla.kafka_ext.progress_array_length", "Length", base.DEC))
add_field("kafka_ext_progress_array_size", ProtoField.int8("zilla.kafka_ext.progress_array_size", "Size", base.DEC))
add_field("kafka_ext_ancestor_offset", ProtoField.int64("zilla.kafka_ext.ancestor_offset", "Ancestor Offset", base.DEC))
add_field("kafka_ext_headers_array_length", ProtoField.int8("zilla.kafka_ext.headers_array_length", "Length", base.DEC))
add_field("kafka_ext_headers_array_size", ProtoField.int8("zilla.kafka_ext.headers_array_size", "Size", base.DEC))
add_field("kafka_ext_producer_id", ProtoField.uint64("zilla.kafka_ext.producer_id", "Producer ID", base.HEX))
add_field("kafka_ext_producer_epoch", ProtoField.uint16("zilla.kafka_ext.producer_epoch", "Producer Epoch", base.HEX))
-- meta
add_field("kafka_ext_partition_leader_id", ProtoField.int32("zilla.kafka_ext.partition_leader_id", "Leader ID", base.DEC))
-- offset_fetch
add_field("kafka_ext_topic_partition_array_length", ProtoField.int8("zilla.kafka_ext.topic_partition_array_length", "Length", base.DEC))
add_field("kafka_ext_topic_partition_array_size", ProtoField.int8("zilla.kafka_ext.topic_partition_array_size", "Size", base.DEC))
add_field("kafka_ext_topic_partition_offset_array_length", ProtoField.int8("zilla.kafka_ext.topic_partition_offset_array_length",
        "Length", base.DEC))
add_field("kafka_ext_topic_partition_offset_array_size", ProtoField.int8("zilla.kafka_ext.topic_partition_offset_array_size",
        "Size", base.DEC))
-- describe
add_field("kafka_ext_config_array_length", ProtoField.int8("zilla.kafka_ext.config_array_length", "Length", base.DEC))
add_field("kafka_ext_config_array_size", ProtoField.int8("zilla.kafka_ext.config_array_size", "Size", base.DEC))
add_field("kafka_ext_config_length", ProtoField.int16("zilla.kafka_ext.config_length", "Length", base.DEC))
add_field("kafka_ext_config", ProtoField.string("zilla.kafka_ext.config", "Config", base.NONE))
-- fetch
add_field("kafka_ext_header_size_max", ProtoField.int32("zilla.kafka_ext.header_size_max", "Header Size Maximum", base.DEC))
add_field("kafka_ext_transactions_array_length", ProtoField.int8("zilla.kafka_ext.transactions_array_length", "Length", base.DEC))
add_field("kafka_ext_transactions_array_size", ProtoField.int8("zilla.kafka_ext.transactions_array_size", "Size", base.DEC))
add_field("kafka_ext_transaction_result", ProtoField.int8("zilla.kafka_ext.transaction_result", "Result", base.DEC,
        kafka_ext_transaction_result_types))
-- produce
add_field("kafka_ext_transaction_length", ProtoField.int16("zilla.kafka_ext.transaction_length", "Length", base.DEC))
add_field("kafka_ext_transaction", ProtoField.string("zilla.kafka_ext.transaction", "Transaction", base.NONE))
add_field("kafka_ext_sequence", ProtoField.int32("zilla.kafka_ext.sequence", "Sequence", base.DEC))
add_field("kafka_ext_crc32c", ProtoField.uint32("zilla.kafka_ext.crc32c", "CRC32C", base.HEX))

function handle_kafka_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == DATA_ID or frame_type_id == FLUSH_ID or
            frame_type_id == REDIRECT_ID then
        local api_length = 1
        local slice_api = buffer(offset, api_length)
        local api = kafka_ext_apis[slice_api:le_uint()]
        ext_subtree:add_le(fields.kafka_ext_api, slice_api)
        if frame_type_id == BEGIN_ID or frame_type_id == REDIRECT_ID then
            if api == "CONSUMER" then
                handle_kafka_begin_consumer_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "GROUP" then
                handle_kafka_group_begin_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "BOOTSTRAP" then
                handle_kafka_begin_bootstrap_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "MERGED" then
                handle_kafka_begin_merged_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "INIT_PRODUCER_ID" then
                handle_kafka_begin_init_producer_id_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "META" then
                handle_kafka_begin_meta_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "OFFSET_COMMIT" then
                handle_kafka_begin_offset_commit_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "OFFSET_FETCH" then
                handle_kafka_begin_offset_fetch_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "DESCRIBE" then
                handle_kafka_begin_describe_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "FETCH" then
                handle_kafka_begin_fetch_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "PRODUCE" then
                handle_kafka_begin_produce_extension(buffer, offset + api_length, ext_subtree)
            end
        elseif frame_type_id == DATA_ID then
            if api == "CONSUMER" then
                handle_kafka_data_consumer_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "MERGED" then
                handle_kafka_data_merged_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "META" then
                handle_kafka_data_meta_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "OFFSET_COMMIT" then
                handle_kafka_data_offset_commit_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "OFFSET_FETCH" then
                handle_kafka_data_offset_fetch_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "DESCRIBE" then
                handle_kafka_data_describe_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "FETCH" then
                handle_kafka_data_fetch_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "PRODUCE" then
                handle_kafka_data_produce_extension(buffer, offset + api_length, ext_subtree)
            end
        elseif frame_type_id == FLUSH_ID then
            if api == "CONSUMER" then
                handle_kafka_flush_consumer_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "GROUP" then
                handle_kafka_group_flush_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "MERGED" then
                handle_kafka_flush_merged_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "FETCH" then
                handle_kafka_flush_fetch_extension(buffer, offset + api_length, ext_subtree)
            elseif api == "PRODUCE" then
                handle_kafka_flush_produce_extension(buffer, offset + api_length, ext_subtree)
            end
        end
    elseif frame_type_id == RESET_ID then
        handle_kafka_reset_extension(buffer, offset, ext_subtree)
    end
end

function handle_kafka_begin_consumer_extension(buffer, offset, ext_subtree)
    -- group_id
    local group_id_offset = offset
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- consumer_id
    local consumer_id_offset = group_id_offset + group_id_length
    local consumer_id_length, slice_consumer_id_length, slice_consumer_id_text = dissect_length_value(buffer, consumer_id_offset, 2)
    add_string_as_subtree(buffer(consumer_id_offset, consumer_id_length), ext_subtree, "Consumer ID: %s",
        slice_consumer_id_length, slice_consumer_id_text, fields.kafka_ext_consumer_id_length, fields.kafka_ext_consumer_id)
    -- host
    local host_offset = consumer_id_offset + consumer_id_length
    local host_length, slice_host_length, slice_host_text = dissect_length_value(buffer, host_offset, 2)
    add_string_as_subtree(buffer(host_offset, host_length), ext_subtree, "Host: %s",
        slice_host_length, slice_host_text, fields.kafka_ext_host_length, fields.kafka_ext_host)
    -- port
    local port_offset = host_offset + host_length
    local port_length = 4
    local slice_port = buffer(port_offset, port_length)
    ext_subtree:add_le(fields.kafka_ext_port, slice_port)
    -- timeout
    local timeout_offset = port_offset + port_length
    local timeout_length = 4
    local slice_timeout = buffer(timeout_offset, timeout_length)
    ext_subtree:add_le(fields.kafka_ext_timeout, slice_timeout)
    -- topic
    local topic_offset = timeout_offset + timeout_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- partition_ids
    local partition_ids_offset = topic_offset + topic_length
    dissect_and_add_kafka_topic_partition_ids(buffer, partition_ids_offset, ext_subtree)
end

function dissect_and_add_kafka_topic_partition_ids(buffer, offset, tree)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Partition IDs (%d items)",
        fields.kafka_ext_partition_ids_array_length, fields.kafka_ext_partition_ids_array_size)
    local item_offset = offset + length
    local partition_id_length = 4
    for i = 1, array_size do
        local slice_partition_id = buffer(item_offset, partition_id_length)
        tree:add_le(fields.kafka_ext_partition_id, slice_partition_id)
        item_offset = item_offset + partition_id_length
    end
end

function resolve_length_of_kafka_topic_partition_ids(buffer, offset)
    local slice_array_length = buffer(offset, 4)
    local slice_array_size = buffer(offset + 4, 4)
    local array_size = slice_array_size:le_int()
    local length = 8
    local partition_id_length = 4
    return length + array_size * partition_id_length
end

function handle_kafka_data_consumer_extension(buffer, offset, ext_subtree)
    -- partition_ids
    local partition_ids_offset = offset
    local partition_ids_length = resolve_length_of_kafka_topic_partition_ids(buffer, partition_ids_offset)
    dissect_and_add_kafka_topic_partition_ids(buffer, partition_ids_offset, ext_subtree)
    -- assignments
    local assignments_offset = partition_ids_offset + partition_ids_length
    dissect_and_add_kafka_consumer_assignments(buffer, assignments_offset, ext_subtree)
end

function dissect_and_add_kafka_consumer_assignments(buffer, offset, tree)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Consumer Assignments (%d items)",
        fields.kafka_ext_consumer_assignments_array_length, fields.kafka_ext_consumer_assignments_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- consumer_id
        local consumer_id_offset = item_offset
        local consumer_id_length, slice_consumer_id_length, slice_consumer_id_text = dissect_length_value(buffer, consumer_id_offset, 2)
        -- partition_ids
        local partition_ids_offset = consumer_id_offset + consumer_id_length
        local partition_ids_length = resolve_length_of_kafka_topic_partition_ids(buffer, partition_ids_offset)
        -- add fields
        local record_length = consumer_id_length + partition_ids_length
        local label = string.format("Consumer Assignment: %s", slice_consumer_id_text:string())
        local consumer_assignment_subtree = tree:add(zilla_protocol, buffer(item_offset, record_length), label)
        add_string_as_subtree(buffer(consumer_id_offset, consumer_id_length), consumer_assignment_subtree, "Consumer ID: %s",
            slice_consumer_id_length, slice_consumer_id_text, fields.kafka_ext_consumer_id_length, fields.kafka_ext_consumer_id)
        dissect_and_add_kafka_topic_partition_ids(buffer, partition_ids_offset, consumer_assignment_subtree)
        -- next
        item_offset = item_offset + record_length
    end
    return item_offset
end

function handle_kafka_flush_consumer_extension(buffer, offset, ext_subtree)
    -- progress
    local progress_offset = offset
    local progress_length = resolve_length_of_kafka_offset(buffer, progress_offset)
    dissect_and_add_kafka_offset(buffer, progress_offset, ext_subtree, "Progress: %d [%d]")
    -- leader_epoch
    local leader_epoch_offset = progress_offset + progress_length
    local leader_epoch_length = 4
    local slice_leader_epoch = buffer(leader_epoch_offset, leader_epoch_length)
    ext_subtree:add_le(fields.kafka_ext_leader_epoch, slice_leader_epoch)
    -- correlation_id
    local correlation_id_offset = leader_epoch_offset + leader_epoch_length
    local correlation_id_length = 8
    local slice_correlation_id = buffer(correlation_id_offset, correlation_id_length)
    ext_subtree:add_le(fields.kafka_ext_correlation_id, slice_correlation_id)
end

function dissect_and_add_kafka_offset(buffer, offset, tree, label_format)
    local partition_id_length = 4
    local partition_offset_length = 8
    local stable_offset_length = 8
    local latest_offset_length = 8
    -- metadata
    local metadata_offset = offset + partition_id_length + partition_offset_length + stable_offset_length + latest_offset_length
    local metadata_length, slice_metadata_length, slice_metadata_text = dissect_length_value(buffer, metadata_offset, 2)
    local record_length = partition_id_length + partition_offset_length + stable_offset_length + latest_offset_length + metadata_length
    -- partition_id
    local partition_id_offset = offset
    local slice_partition_id = buffer(partition_id_offset, partition_id_length)
    -- partition_offset
    local partition_offset_offset = partition_id_offset + partition_id_length
    local slice_partition_offset = buffer(partition_offset_offset, partition_offset_length)
    -- stable_offset
    local stable_offset_offset = partition_offset_offset + partition_offset_length
    local slice_stable_offset = buffer(stable_offset_offset, stable_offset_length)
    -- latest_offset
    local latest_offset_offset = stable_offset_offset + stable_offset_length
    local slice_latest_offset = buffer(latest_offset_offset, latest_offset_length)
    -- add fields
    local label = string.format(label_format, slice_partition_id:le_int(), tostring(slice_partition_offset:le_int64()))
    local offset_subtree = tree:add(zilla_protocol, buffer(offset, record_length), label)
    offset_subtree:add_le(fields.kafka_ext_partition_id, slice_partition_id)
    offset_subtree:add_le(fields.kafka_ext_partition_offset, slice_partition_offset)
    offset_subtree:add_le(fields.kafka_ext_stable_offset, slice_stable_offset)
    offset_subtree:add_le(fields.kafka_ext_latest_offset, slice_latest_offset)
    add_string_as_subtree(buffer(metadata_offset, metadata_length), offset_subtree, "Metadata: %s",
        slice_metadata_length, slice_metadata_text, fields.kafka_ext_metadata_length, fields.kafka_ext_metadata)
end

function resolve_length_of_kafka_offset(buffer, offset)
    local partition_id_length = 4
    local partition_offset_length = 8
    local stable_offset_length = 8
    local latest_offset_length = 8
    local metadata_offset = offset + partition_id_length + partition_offset_length + stable_offset_length + latest_offset_length
    local metadata_length, slice_metadata_length, slice_metadata_text = dissect_length_value(buffer, metadata_offset, 2)
    return partition_id_length + partition_offset_length + stable_offset_length + latest_offset_length + metadata_length
end

function dissect_and_add_kafka_offset_array(buffer, offset, tree, field_array_length, field_array_size, plural_name, singular_name)
    local label = string.format("%s (%%d items)", plural_name)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, label, field_array_length,
        field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length = resolve_length_of_kafka_offset(buffer, item_offset)
        dissect_and_add_kafka_offset(buffer, item_offset, tree, string.format("%s: %%s [%%d]", singular_name))
        item_offset = item_offset + item_length
    end
end

function handle_kafka_group_begin_extension(buffer, offset, ext_subtree)
    -- group_id
    local group_id_offset = offset
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- protocol
    local protocol_offset = group_id_offset + group_id_length
    local protocol_length, slice_protocol_length, slice_protocol_text = dissect_length_value(buffer, protocol_offset, 2)
    add_string_as_subtree(buffer(protocol_offset, protocol_length), ext_subtree, "Protocol: %s",
        slice_protocol_length, slice_protocol_text, fields.kafka_ext_protocol_length, fields.kafka_ext_protocol)
    -- instance_id
    local instance_id_offset = protocol_offset + protocol_length
    local instance_id_length, slice_instance_id_length, slice_instance_id_text = dissect_length_value(buffer, instance_id_offset, 2)
    add_string_as_subtree(buffer(instance_id_offset, instance_id_length), ext_subtree, "Instance ID: %s",
        slice_instance_id_length, slice_instance_id_text, fields.kafka_ext_instance_id_length, fields.kafka_ext_instance_id)
    -- host
    local host_offset = instance_id_offset + instance_id_length
    local host_length, slice_host_length, slice_host_text = dissect_length_value(buffer, host_offset, 2)
    add_string_as_subtree(buffer(host_offset, host_length), ext_subtree, "Host: %s",
        slice_host_length, slice_host_text, fields.kafka_ext_host_length, fields.kafka_ext_host)
    -- port
    local port_offset = host_offset + host_length
    local port_length = 4
    local slice_port = buffer(port_offset, port_length)
    ext_subtree:add_le(fields.kafka_ext_port, slice_port)
    -- timeout
    local timeout_offset = port_offset + port_length
    local timeout_length = 4
    local slice_timeout = buffer(timeout_offset, timeout_length)
    ext_subtree:add_le(fields.kafka_ext_timeout, slice_timeout)
    -- metadata_length_varint
    local metadata_length_offset = timeout_offset + timeout_length
    local metadata_length, slice_metadata_length_varint, metadata_length_length = decode_varint32(buffer, metadata_length_offset)
    add_varint_as_subtree(buffer(metadata_length_offset, metadata_length_length), ext_subtree, "Metadata Length: %d",
        slice_metadata_length_varint, metadata_length, fields.kafka_ext_metadata_length_varint, fields.kafka_ext_metadata_length)
    -- metadata_bytes
    if (metadata_length > 0) then
        local metadata_bytes_offset = metadata_length_offset + metadata_length_length
        local slice_metadata_bytes = buffer(metadata_bytes_offset, metadata_length)
        ext_subtree:add(fields.kafka_ext_metadata_bytes, slice_metadata_bytes)
    end
end

function handle_kafka_group_flush_extension(buffer, offset, ext_subtree)
    -- generation_id
    local generation_id_offset = offset
    local generation_id_length = 4
    local slice_generation_id = buffer(generation_id_offset, generation_id_length)
    ext_subtree:add_le(fields.kafka_ext_generation_id, slice_generation_id)
    -- leader_id
    local leader_id_offset = generation_id_offset + generation_id_length
    local leader_id_length, slice_leader_id_length, slice_leader_id_text = dissect_length_value(buffer, leader_id_offset, 2)
    add_string_as_subtree(buffer(leader_id_offset, leader_id_length), ext_subtree, "Leader ID: %s",
        slice_leader_id_length, slice_leader_id_text, fields.kafka_ext_leader_id_length, fields.kafka_ext_leader_id)
    -- member_id
    local member_id_offset = leader_id_offset + leader_id_length
    local member_id_length, slice_member_id_length, slice_member_id_text = dissect_length_value(buffer, member_id_offset, 2)
    add_string_as_subtree(buffer(member_id_offset, member_id_length), ext_subtree, "Member ID: %s",
        slice_member_id_length, slice_member_id_text, fields.kafka_ext_member_id_length, fields.kafka_ext_member_id)
    -- members
    local members_offset = member_id_offset + member_id_length
    dissect_and_add_kafka_group_members(buffer, members_offset, ext_subtree)
end

function dissect_and_add_kafka_group_members(buffer, offset, tree)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Members (%d items)",
        fields.kafka_ext_consumer_assignments_array_length, fields.kafka_ext_consumer_assignments_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- member_id
        local member_id_offset = item_offset
        local member_id_length, slice_member_id_length, slice_member_id_text = dissect_length_value(buffer, member_id_offset, 2)
        -- metadata_length_varint
        local metadata_length_offset = member_id_offset + member_id_length
        local metadata_length, slice_metadata_length_varint, metadata_length_length = decode_varint32(buffer, metadata_length_offset)
        -- add fields
        local record_length = member_id_length + metadata_length_length + metadata_length
        local member_label = string.format("Member: %s", slice_member_id_text:string())
        local member_subtree = tree:add(zilla_protocol, buffer(item_offset, record_length), member_label)
        add_string_as_subtree(buffer(member_id_offset, member_id_length), member_subtree, "Member ID: %s",
            slice_member_id_length, slice_member_id_text, fields.kafka_ext_member_id_length, fields.kafka_ext_member_id)
        add_varint_as_subtree(buffer(metadata_length_offset, metadata_length_length), member_subtree, "Metadata Length: %d",
            slice_metadata_length_varint, metadata_length, fields.kafka_ext_metadata_length_varint, fields.kafka_ext_metadata_length)
        -- metadata_bytes
        if (metadata_length > 0) then
            local metadata_bytes_offset = metadata_length_offset + metadata_length_length
            local slice_metadata_bytes = buffer(metadata_bytes_offset, metadata_length)
            member_subtree:add(fields.kafka_ext_metadata_bytes, slice_metadata_bytes)
        end
        -- next
        item_offset = item_offset + record_length
    end
    return item_offset
end

function handle_kafka_begin_bootstrap_extension(buffer, offset, ext_subtree)
    -- topic
    local topic_offset = offset
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- group_id
    local group_id_offset = topic_offset + topic_length
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- consumer_id
    local consumer_id_offset = group_id_offset + group_id_length
    local consumer_id_length, slice_consumer_id_length, slice_consumer_id_text = dissect_length_value(buffer, consumer_id_offset, 2)
    add_string_as_subtree(buffer(consumer_id_offset, consumer_id_length), ext_subtree, "Consumer ID: %s",
        slice_consumer_id_length, slice_consumer_id_text, fields.kafka_ext_consumer_id_length, fields.kafka_ext_consumer_id)
    -- timeout
    local timeout_offset = consumer_id_offset + consumer_id_length
    local timeout_length = 4
    local slice_timeout = buffer(timeout_offset, timeout_length)
    ext_subtree:add_le(fields.kafka_ext_timeout, slice_timeout)
end

function handle_kafka_begin_merged_extension(buffer, offset, ext_subtree)
    -- capabilities
    local capabilities_offset = offset
    local capabilities_length = 1
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    ext_subtree:add_le(fields.kafka_ext_capabilities, slice_capabilities)
    -- topic
    local topic_offset = capabilities_offset + capabilities_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- group_id
    local group_id_offset = topic_offset + topic_length
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- consumer_id
    local consumer_id_offset = group_id_offset + group_id_length
    local consumer_id_length, slice_consumer_id_length, slice_consumer_id_text = dissect_length_value(buffer, consumer_id_offset, 2)
    add_string_as_subtree(buffer(consumer_id_offset, consumer_id_length), ext_subtree, "Consumer ID: %s",
        slice_consumer_id_length, slice_consumer_id_text, fields.kafka_ext_consumer_id_length, fields.kafka_ext_consumer_id)
    -- timeout
    local timeout_offset = consumer_id_offset + consumer_id_length
    local timeout_length = 4
    local slice_timeout = buffer(timeout_offset, timeout_length)
    ext_subtree:add_le(fields.kafka_ext_timeout, slice_timeout)
    -- partitions
    local partitions_offset = timeout_offset + timeout_length
    local partitions_length = resolve_length_of_array(buffer, partitions_offset)
    dissect_and_add_kafka_offset_array(buffer, partitions_offset, ext_subtree,
        fields.kafka_ext_partitions_array_length, fields.kafka_ext_partitions_array_size, "Partitions", "Partition")
    -- filters
    local filters_offset = partitions_offset + partitions_length
    local filters_length = resolve_length_of_array(buffer, filters_offset)
    dissect_and_add_kafka_filters_array(buffer, filters_offset, ext_subtree,
        fields.kafka_ext_filters_array_length, fields.kafka_ext_filters_array_size)
    -- evaluation
    local evaluation_offset = filters_offset + filters_length
    local evaluation_length = 1
    local slice_evaluation = buffer(evaluation_offset, evaluation_length)
    ext_subtree:add_le(fields.kafka_ext_evaluation, slice_evaluation)
    -- isolation
    local isolation_offset = evaluation_offset + evaluation_length
    local isolation_length = 1
    local slice_isolation = buffer(isolation_offset, isolation_length)
    ext_subtree:add_le(fields.kafka_ext_isolation, slice_isolation)
    -- delta_type
    local delta_type_offset = isolation_offset + isolation_length
    local delta_type_length = 1
    local slice_delta_type = buffer(delta_type_offset, delta_type_length)
    ext_subtree:add_le(fields.kafka_ext_delta_type, slice_delta_type)
    -- ack_mode
    local ack_mode_offset = delta_type_offset + delta_type_length
    local ack_mode_length = 2
    local slice_ack_mode_id = buffer(ack_mode_offset, ack_mode_length)
    local ack_mode = kafka_ext_ack_modes[slice_ack_mode_id:le_int()]
    ext_subtree:add_le(fields.kafka_ext_ack_mode_id, slice_ack_mode_id)
    ext_subtree:add(fields.kafka_ext_ack_mode, ack_mode)
    -- configs
    local configs_offset = ack_mode_offset + ack_mode_length
    local configs_length = resolve_length_of_array(buffer, configs_offset)
    dissect_and_add_kafka_config_struct_array(buffer, configs_offset, ext_subtree, fields.kafka_ext_config_array_length,
        fields.kafka_ext_config_array_size)
end

function handle_kafka_begin_init_producer_id_extension(buffer, offset, ext_subtree)
    -- producer_id
    local producer_id_offset = offset
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    ext_subtree:add_le(fields.kafka_ext_producer_id, slice_producer_id)
    -- producer_epoch
    local producer_epoch_offset = producer_id_offset + producer_id_length
    local producer_epoch_length = 2
    local slice_producer_epoch = buffer(producer_epoch_offset, producer_epoch_length)
    ext_subtree:add_le(fields.kafka_ext_producer_epoch, slice_producer_epoch)
end

function dissect_and_add_kafka_filters_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Filters (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local filter_label = string.format("Filter #%d", i)
        local item_length = resolve_length_of_array(buffer, item_offset)
        local item_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), filter_label)
        dissect_and_add_kafka_conditions_array(buffer, item_offset, item_subtree,
            fields.kafka_ext_conditions_array_length, fields.kafka_ext_conditions_array_size)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_kafka_conditions_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Conditions (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length, item_label = resolve_length_and_label_of_kafka_condition(buffer, item_offset)
        local condition_label = string.format("Condition: %s", item_label)
        local item_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), condition_label)
        dissect_and_add_kafka_condition(buffer, item_offset, item_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_kafka_condition(buffer, offset, tree)
    -- condition_type
    local condition_type_offset = offset
    local condition_type_length = 1
    local slice_condition_type = buffer(condition_type_offset, condition_type_length)
    local condition_type = kafka_ext_condition_types[slice_condition_type:le_int()]
    tree:add_le(fields.kafka_ext_condition_type, slice_condition_type)
    if condition_type == "KEY" then
        dissect_and_add_kafka_key(buffer, offset + condition_type_length, tree)
    elseif condition_type == "HEADER" then
        dissect_and_add_kafka_header(buffer, offset + condition_type_length, tree)
    elseif condition_type == "NOT" then
        dissect_and_add_kafka_not(buffer, offset + condition_type_length, tree)
    elseif condition_type == "HEADERS" then
        dissect_and_add_kafka_headers(buffer, offset + condition_type_length, tree)
    end
end

function resolve_length_and_label_of_kafka_condition(buffer, offset)
    -- condition_type
    local condition_type_offset = offset
    local condition_type_length = 1
    local slice_condition_type = buffer(condition_type_offset, condition_type_length)
    local condition_type = kafka_ext_condition_types[slice_condition_type:le_int()]
    if condition_type == "KEY" then
        return resolve_length_and_label_of_kafka_key(buffer, offset + condition_type_length, condition_type_length)
    elseif condition_type == "HEADER" then
        return resolve_length_and_label_of_kafka_header(buffer, offset + condition_type_length, condition_type_length)
    elseif condition_type == "NOT" then
        return resolve_length_and_label_of_kafka_not(buffer, offset + condition_type_length, condition_type_length)
    elseif condition_type == "HEADERS" then
        return resolve_length_and_label_of_kafka_headers(buffer, offset + condition_type_length, condition_type_length)
    end
end

function dissect_and_add_kafka_key(buffer, offset, tree)
    -- length
    local length_offset = offset
    local length, slice_length_varint, length_length = decode_varint32(buffer, length_offset)
    add_varint_as_subtree(buffer(length_offset, length_length), tree, "Length: %d", slice_length_varint, length,
        fields.kafka_ext_key_length_varint, fields.kafka_ext_key_length)
    if (length > 0) then
        local value_offset = length_offset + length_length
        local slice_value = buffer(value_offset, length)
        tree:add(fields.kafka_ext_key, slice_value)
    end
end

function resolve_length_and_label_of_kafka_key(buffer, offset, extra_length)
    local length_offset = offset
    local length, slice_length_varint, length_length = decode_varint32(buffer, length_offset)
    local value = ""
    if (length > 0) then
        local value_offset = length_offset + length_length
        local slice_value = buffer(value_offset, length)
        value = slice_value:string()
    end
    -- result
    local record_length = extra_length + length_length + length
    local label = string.format("[KEY] %s", value)
    return record_length, label
end

function dissect_and_add_kafka_header(buffer, offset, tree)
    -- name_length
    local name_length_offset = offset
    local name_length, slice_name_length_varint, name_length_length = decode_varint32(buffer, name_length_offset)
    add_varint_as_subtree(buffer(name_length_offset, name_length_length), tree, "Length: %d", slice_name_length_varint,
        name_length, fields.kafka_ext_name_length_varint, fields.kafka_ext_name_length)
    -- name
    local name_offset = name_length_offset + name_length_length
    if (name_length > 0) then
        local slice_name = buffer(name_offset, name_length)
        tree:add(fields.kafka_ext_name, slice_name)
    end
    -- value_length
    local value_length_offset = name_offset + name_length
    local value_length, slice_value_length_varint, value_length_length = decode_varint32(buffer, value_length_offset)
    add_varint_as_subtree(buffer(value_length_offset, value_length_length), tree, "Length: %d", slice_value_length_varint,
        value_length, fields.kafka_ext_value_length_varint, fields.kafka_ext_value_length)
    -- value
    local value_offset = value_length_offset + value_length_length
    if (value_length > 0) then
        local slice_value = buffer(value_offset, value_length)
        tree:add(fields.kafka_ext_value, slice_value)
    end
end

function resolve_length_and_label_of_kafka_header(buffer, offset, extra_length)
    -- name_length
    local name_length_offset = offset
    local name_length, slice_name_length_varint, name_length_length = decode_varint32(buffer, name_length_offset)
    -- name
    local name_offset = name_length_offset + name_length_length
    local name = ""
    if (name_length > 0) then
        local slice_name = buffer(name_offset, name_length)
        name = slice_name:string()
    end
    -- value_length
    local value_length_offset = name_offset + name_length
    local value_length, slice_value_length_varint, value_length_length = decode_varint32(buffer, value_length_offset)
    -- value
    local value_offset = value_length_offset + value_length_length
    local value = ""
    if (value_length > 0) then
        local slice_value = buffer(value_offset, value_length)
        value = slice_value:string()
    end
    -- result
    local record_length = extra_length + name_length_length + name_length + value_length_length + value_length
    local label = string.format("[HEADER] %s: %s", name, value)
    return record_length, label
end

function dissect_and_add_kafka_not(buffer, offset, tree)
    -- condition_type
    local condition_type_offset = offset
    local condition_type_length = 1
    local slice_condition_type = buffer(condition_type_offset, condition_type_length)
    local condition_type = kafka_ext_condition_types[slice_condition_type:le_int()]
    tree:add_le(fields.kafka_ext_condition_type, slice_condition_type)
    if condition_type == "KEY" then
        dissect_and_add_kafka_key(buffer, offset + condition_type_length, tree)
    elseif condition_type == "HEADER" then
        dissect_and_add_kafka_header(buffer, offset + condition_type_length, tree)
    end
end

function resolve_length_and_label_of_kafka_not(buffer, offset, extra_length)
    -- condition_type
    local condition_type_offset = offset
    local condition_type_length = 1
    local slice_condition_type = buffer(condition_type_offset, condition_type_length)
    local condition_type = kafka_ext_condition_types[slice_condition_type:le_int()]
    local length, label
    if condition_type == "KEY" then
        length, label = resolve_length_and_label_of_kafka_key(buffer, offset + condition_type_length,
            extra_length + condition_type_length)
    elseif condition_type == "HEADER" then
        length, label = resolve_length_and_label_of_kafka_header(buffer, offset + condition_type_length,
            extra_length + condition_type_length)
    end
    return length, string.format("[NOT] %s", label)
end

function dissect_and_add_kafka_headers(buffer, offset, tree)
    -- name_length
    local name_length_offset = offset
    local name_length, slice_name_length_varint, name_length_length = decode_varint32(buffer, name_length_offset)
    add_varint_as_subtree(buffer(name_length_offset, name_length_length), tree, "Length: %d", slice_name_length_varint,
        name_length, fields.kafka_ext_name_length_varint, fields.kafka_ext_name_length)
    -- name
    local name_offset = name_length_offset + name_length_length
    if (name_length > 0) then
        local slice_name = buffer(name_offset, name_length)
        tree:add(fields.kafka_ext_name, slice_name)
    end
    -- value_match_array
    local value_match_array_offset = name_offset + name_length
    dissect_and_add_kafka_value_match_array(buffer, value_match_array_offset, tree,
        fields.kafka_ext_value_match_array_length, fields.kafka_ext_value_match_array_size)
end

function resolve_length_and_label_of_kafka_headers(buffer, offset, extra_length)
    -- name_length
    local name_length_offset = offset
    local name_length, slice_name_length_varint, name_length_length = decode_varint32(buffer, name_length_offset)
    -- name
    local name_offset = name_length_offset + name_length_length
    local name = ""
    if (name_length > 0) then
        local slice_name = buffer(name_offset, name_length)
        name = slice_name:string()
    end
    -- value_match_array
    local value_match_array_offset = name_offset + name_length
    local array_length = resolve_length_of_array(buffer, value_match_array_offset)
    local array_label = resolve_label_of_kafka_value_match_array(buffer, value_match_array_offset)
    -- result
    local record_length = extra_length + name_length_length + name_length + array_length
    local label = string.format("[HEADERS] %s: %s", name, array_label)
    return record_length, label
end

function dissect_and_add_kafka_value_match_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Value Matches (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local filter_label = string.format("Value Match #%d", i)
        local item_length, item_label = resolve_length_and_label_of_kafka_value_match(buffer, item_offset)
        local value_match_label = string.format("Value Match: %s", item_label)
        local item_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), value_match_label)
        dissect_and_add_kafka_value_match(buffer, item_offset, item_subtree)
        item_offset = item_offset + item_length
    end
end

function resolve_label_of_kafka_value_match_array(buffer, offset)
    local slice_array_size = buffer(offset + 4, 4)
    local array_size = slice_array_size:le_int()
    local length = 8
    local item_offset = offset + length
    local result = ""
    for i = 1, array_size do
        local item_length, item_label = resolve_length_and_label_of_kafka_value_match(buffer, item_offset)
        result = result .. item_label
        if i < array_size then
            result = result .. ", "
        end
        item_offset = item_offset + item_length
    end
    return result
end

function dissect_and_add_kafka_value_match(buffer, offset, tree)
    -- value_match_type
    local value_match_type_offset = offset
    local value_match_type_length = 1
    local slice_value_match_type = buffer(value_match_type_offset, value_match_type_length)
    local value_match_type = kafka_ext_value_match_types[slice_value_match_type:le_int()]
    tree:add_le(fields.kafka_ext_value_match_type, slice_value_match_type)
    if value_match_type == "VALUE" then
        -- value_length
        local value_length_offset = value_match_type_offset + value_match_type_length
        local value_length, slice_value_length_varint, value_length_length = decode_varint32(buffer, value_length_offset)
        add_varint_as_subtree(buffer(value_length_offset, value_length_length), tree, "Length: %d", slice_value_length_varint,
            value_length, fields.kafka_ext_value_length_varint, fields.kafka_ext_value_length)
        -- value
        local value_offset = value_length_offset + value_length_length
        if (value_length > 0) then
            local slice_value = buffer(value_offset, value_length)
            tree:add(fields.kafka_ext_value, slice_value)
        end
    elseif value_match_type == "SKIP" then
        local skip_type_offset = value_match_type_offset + value_match_type_length
        local skip_type_length = 1
        local slice_skip_type = buffer(skip_type_offset, skip_type_length)
        local skip_type = kafka_ext_skip_types[slice_skip_type:le_int()]
        tree:add_le(fields.kafka_ext_skip_type, slice_skip_type)
    end
end

function resolve_length_and_label_of_kafka_value_match(buffer, offset)
    -- value_match_type
    local value_match_type_offset = offset
    local value_match_type_length = 1
    local slice_value_match_type = buffer(value_match_type_offset, value_match_type_length)
    local value_match_type = kafka_ext_value_match_types[slice_value_match_type:le_int()]
    if value_match_type == "VALUE" then
        -- value_length
        local value_length_offset = value_match_type_offset + value_match_type_length
        local value_length, slice_value_length_varint, value_length_length = decode_varint32(buffer, value_length_offset)
        -- value
        local value_offset = value_length_offset + value_length_length
        local value = ""
        if (value_length > 0) then
            local slice_value = buffer(value_offset, value_length)
            value = slice_value:string()
        end
        local record_length = value_match_type_length + value_length_length + value_length
        return record_length, value
    elseif value_match_type == "SKIP" then
        local skip_type_offset = value_match_type_offset + value_match_type_length
        local skip_type_length = 1
        local slice_skip_type = buffer(skip_type_offset, skip_type_length)
        local skip_type = kafka_ext_skip_types[slice_skip_type:le_int()]
        return value_match_type_length + skip_type_length, string.format("[%s]", skip_type)
    end
end

function handle_kafka_data_merged_extension(buffer, offset, ext_subtree)
    -- merged_api
    local merged_api_offset = offset
    local merged_api_length = 1
    local slice_merged_api = buffer(merged_api_offset, merged_api_length)
    local merged_api = kafka_ext_apis[slice_merged_api:le_int()]
    ext_subtree:add_le(fields.kafka_ext_merged_api, slice_merged_api)
    if merged_api == "FETCH" then
        handle_kafka_data_merged_fetch_extension(buffer, offset + merged_api_length, ext_subtree)
    elseif merged_api == "PRODUCE" then
        handle_kafka_data_merged_produce_extension(buffer, offset + merged_api_length, ext_subtree)
    end
end

function handle_kafka_data_merged_fetch_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.kafka_ext_deferred, slice_deferred)
    -- timestamp
    local timestamp_offset = deferred_offset + deferred_length
    local timestamp_length = 8
    local slice_timestamp = buffer(timestamp_offset, timestamp_length)
    ext_subtree:add_le(fields.sse_ext_timestamp, slice_timestamp)
    -- filters
    local filters_offset = timestamp_offset + timestamp_length
    local filters_length = 8
    local slice_filters = buffer(filters_offset, filters_length)
    ext_subtree:add_le(fields.kafka_ext_filters, slice_filters)
    -- partition
    local partition_offset = filters_offset + filters_length
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- progress
    local progress_offset = partition_offset + partition_length
    local progress_length = resolve_length_of_array(buffer, progress_offset)
    dissect_and_add_kafka_offset_array(buffer, progress_offset, ext_subtree,
        fields.kafka_ext_progress_array_length, fields.kafka_ext_progress_array_size, "Progress", "Progress")
    -- key
    local key_offset = progress_offset + progress_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), string.format("Key: %s", key_label))
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
    -- delta
    local delta_offset = key_offset + key_length
    local delta_length, delta_label = resolve_length_and_label_of_kafka_delta(buffer, delta_offset)
    local delta_subtree = ext_subtree:add(zilla_protocol, buffer(delta_offset, delta_length), string.format("Delta: %s", delta_label))
    dissect_and_add_kafka_delta(buffer, delta_offset, delta_subtree)
    -- header_array
    local header_array_offset = delta_offset + delta_length
    dissect_and_add_kafka_header_array(buffer, header_array_offset, ext_subtree, fields.kafka_ext_headers_array_length,
        fields.kafka_ext_headers_array_size)
end

function dissect_and_add_kafka_delta(buffer, offset, tree)
    -- delta_type
    local delta_type_offset = offset
    local delta_type_length = 1
    local slice_delta_type = buffer(delta_type_offset, delta_type_length)
    tree:add_le(fields.kafka_ext_delta_type, slice_delta_type)
    -- ancestor_offset
    local ancestor_offset_offset = delta_type_offset + delta_type_length
    local ancestor_offset_length = 8
    local slice_ancestor_offset = buffer(ancestor_offset_offset, ancestor_offset_length)
    tree:add_le(fields.kafka_ext_ancestor_offset, slice_ancestor_offset)
end

function resolve_length_and_label_of_kafka_delta(buffer, offset)
    -- delta_type
    local delta_type_offset = offset
    local delta_type_length = 1
    local slice_delta_type = buffer(delta_type_offset, delta_type_length)
    local delta_type = kafka_ext_delta_types[slice_delta_type:le_int()]
    -- ancestor_offset
    local ancestor_offset_offset = delta_type_offset + delta_type_length
    local ancestor_offset_length = 8
    local slice_ancestor_offset = buffer(ancestor_offset_offset, ancestor_offset_length)
    local ancestor_offset = tostring(slice_ancestor_offset:le_int64())
    -- result
    local record_length = delta_type_length + ancestor_offset_length
    local label = string.format("[%s] [%s]", delta_type, ancestor_offset)
    return record_length, label
end

function dissect_and_add_kafka_header_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Headers (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length, item_label = resolve_length_and_label_of_kafka_header(buffer, item_offset, 0)
        local label = string.format("Header: %s", item_label)
        local item_subtree = tree:add(zilla_protocol, buffer(item_offset, record_length), label)
        dissect_and_add_kafka_header(buffer, item_offset, item_subtree)
        item_offset = item_offset + item_length
    end
end

function handle_kafka_data_merged_produce_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.kafka_ext_deferred, slice_deferred)
    -- timestamp
    local timestamp_offset = deferred_offset + deferred_length
    local timestamp_length = 8
    local slice_timestamp = buffer(timestamp_offset, timestamp_length)
    ext_subtree:add_le(fields.sse_ext_timestamp, slice_timestamp)
    -- producer_id
    local producer_id_offset = timestamp_offset + timestamp_length
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    ext_subtree:add_le(fields.kafka_ext_producer_id, slice_producer_id)
    -- producer_epoch
    local producer_epoch_offset = producer_id_offset + producer_id_length
    local producer_epoch_length = 2
    local slice_producer_epoch = buffer(producer_epoch_offset, producer_epoch_length)
    ext_subtree:add_le(fields.kafka_ext_producer_epoch, slice_producer_epoch)
    -- partition
    local partition_offset = producer_epoch_offset + producer_epoch_length
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- key
    local key_offset = partition_offset + partition_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local label = string.format("Key: %s", key_label)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), label)
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
    -- hash_key
    local hash_key_offset = key_offset + key_length
    local hash_key_length, hash_key_label = resolve_length_and_label_of_kafka_key(buffer, hash_key_offset, 0)
    local label = string.format("Hash Key: %s", hash_key_label)
    local hash_key_subtree = ext_subtree:add(zilla_protocol, buffer(hash_key_offset, hash_key_length), label)
    dissect_and_add_kafka_key(buffer, hash_key_offset, hash_key_subtree)
    -- header_array
    local header_array_offset = hash_key_offset + hash_key_length
    dissect_and_add_kafka_header_array(buffer, header_array_offset, ext_subtree, fields.kafka_ext_headers_array_length,
        fields.kafka_ext_headers_array_size)
end

function handle_kafka_flush_merged_extension(buffer, offset, ext_subtree)
    -- merged_api
    local merged_api_offset = offset
    local merged_api_length = 1
    local slice_merged_api = buffer(merged_api_offset, merged_api_length)
    local merged_api = kafka_ext_apis[slice_merged_api:le_uint()]
    ext_subtree:add(fields.kafka_ext_merged_api, slice_merged_api)
    if merged_api == "CONSUMER" then
        handle_kafka_flush_merged_consumer_extension(buffer, offset + merged_api_length, ext_subtree)
    elseif merged_api == "FETCH" then
        handle_kafka_flush_merged_fetch_extension(buffer, offset + merged_api_length, ext_subtree)
    end
end

function handle_kafka_flush_merged_consumer_extension(buffer, offset, ext_subtree)
    -- progress
    local progress_offset = offset
    local progress_length = resolve_length_of_kafka_offset(buffer, progress_offset)
    dissect_and_add_kafka_offset(buffer, progress_offset, ext_subtree, "Progress: %d [%d]")
    -- correlation_id
    local correlation_id_offset = progress_offset + progress_length
    local correlation_id_length = 8
    local slice_correlation_id = buffer(correlation_id_offset, correlation_id_length)
    ext_subtree:add_le(fields.kafka_ext_correlation_id, slice_correlation_id)
end

function handle_kafka_flush_merged_fetch_extension(buffer, offset, ext_subtree)
    -- partition
    local partition_offset = offset
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- progress
    local progress_offset = partition_offset + partition_length
    local progress_length = resolve_length_of_array(buffer, progress_offset)
    dissect_and_add_kafka_offset_array(buffer, progress_offset, ext_subtree,
        fields.kafka_ext_progress_array_length, fields.kafka_ext_progress_array_size, "Progress", "Progress")
    -- capabilities
    local capabilities_offset = progress_offset + progress_length
    local capabilities_length = 1
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    ext_subtree:add_le(fields.kafka_ext_capabilities, slice_capabilities)
    -- filters
    local filters_offset = capabilities_offset + capabilities_length
    local filters_length = resolve_length_of_array(buffer, filters_offset)
    dissect_and_add_kafka_filters_array(buffer, filters_offset, ext_subtree,
        fields.kafka_ext_filters_array_length, fields.kafka_ext_filters_array_size)
    -- key
    local key_offset = filters_offset + filters_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local label = string.format("Key: %s", key_label)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), label)
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
end

function handle_kafka_begin_meta_extension(buffer, offset, ext_subtree)
    -- topic
    local topic_offset = offset
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
end

function handle_kafka_data_meta_extension(buffer, offset, ext_subtree)
    -- partitions
    local partitions_offset = offset
    local partitions_length = resolve_length_of_array(buffer, partitions_offset)
    dissect_and_add_kafka_partition_array(buffer, partitions_offset, ext_subtree,
        fields.kafka_ext_partitions_array_length, fields.kafka_ext_partitions_array_size)
end

function dissect_and_add_kafka_partition_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Partitions (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        local item_length = 8
        -- partition_id
        local partition_id_offset = item_offset
        local partition_id_length = 4
        local slice_partition_id = buffer(partition_id_offset, partition_id_length)
        local partition_id = slice_partition_id:le_int()
        -- leader_id
        local leader_id_offset = partition_id_offset + partition_id_length
        local leader_id_length = 4
        local slice_leader_id = buffer(leader_id_offset, leader_id_length)
        local leader_id = slice_leader_id:le_int()
        -- subtree
        local label = string.format("Partition: %d [%d]", partition_id, leader_id)
        local partition_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        partition_subtree:add_le(fields.kafka_ext_partition_id, slice_partition_id)
        partition_subtree:add_le(fields.kafka_ext_partition_leader_id, slice_leader_id)
        item_offset = item_offset + item_length
    end
end

function handle_kafka_begin_offset_commit_extension(buffer, offset, ext_subtree)
    -- group_id
    local group_id_offset = offset
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- member_id
    local member_id_offset = group_id_offset + group_id_length
    local member_id_length, slice_member_id_length, slice_member_id_text = dissect_length_value(buffer, member_id_offset, 2)
    add_string_as_subtree(buffer(member_id_offset, member_id_length), ext_subtree, "Member ID: %s",
        slice_member_id_length, slice_member_id_text, fields.kafka_ext_member_id_length, fields.kafka_ext_member_id)
    -- instance_id
    local instance_id_offset = member_id_offset + member_id_length
    local instance_id_length, slice_instance_id_length, slice_instance_id_text = dissect_length_value(buffer, instance_id_offset, 2)
    add_string_as_subtree(buffer(instance_id_offset, instance_id_length), ext_subtree, "Instance ID: %s",
        slice_instance_id_length, slice_instance_id_text, fields.kafka_ext_instance_id_length, fields.kafka_ext_instance_id)
    -- host
    local host_offset = instance_id_offset + instance_id_length
    local host_length, slice_host_length, slice_host_text = dissect_length_value(buffer, host_offset, 2)
    add_string_as_subtree(buffer(host_offset, host_length), ext_subtree, "Host: %s",
        slice_host_length, slice_host_text, fields.kafka_ext_host_length, fields.kafka_ext_host)
    -- port
    local port_offset = host_offset + host_length
    local port_length = 4
    local slice_port = buffer(port_offset, port_length)
    ext_subtree:add_le(fields.kafka_ext_port, slice_port)
end

function handle_kafka_data_offset_commit_extension(buffer, offset, ext_subtree)
    -- topic
    local topic_offset = offset
    local topic_lentgh, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.mqtt_ext_topic_length, fields.mqtt_ext_topic)
    -- progress
    local progress_offset = topic_offset + topic_lentgh
    local progress_length = resolve_length_of_kafka_offset(buffer, progress_offset)
    dissect_and_add_kafka_offset(buffer, progress_offset, ext_subtree, "Progress: %d [%d]")
    -- generation_id
    local generation_id_offset = progress_offset + progress_length
    local generation_id_length = 4
    local slice_generation_id = buffer(generation_id_offset, generation_id_length)
    ext_subtree:add_le(fields.kafka_ext_generation_id, slice_generation_id)
    -- leader_epoch
    local leader_epoch_offset = generation_id_offset + generation_id_length
    local leader_epoch_length = 4
    local slice_leader_epoch = buffer(leader_epoch_offset, leader_epoch_length)
    ext_subtree:add_le(fields.kafka_ext_leader_epoch, slice_leader_epoch)
end

function handle_kafka_begin_offset_fetch_extension(buffer, offset, ext_subtree)
    -- group_id
    local group_id_offset = offset
    local group_id_length, slice_group_id_length, slice_group_id_text = dissect_length_value(buffer, group_id_offset, 2)
    add_string_as_subtree(buffer(group_id_offset, group_id_length), ext_subtree, "Group ID: %s",
        slice_group_id_length, slice_group_id_text, fields.kafka_ext_group_id_length, fields.kafka_ext_group_id)
    -- host
    local host_offset = group_id_offset + group_id_length
    local host_length, slice_host_length, slice_host_text = dissect_length_value(buffer, host_offset, 2)
    add_string_as_subtree(buffer(host_offset, host_length), ext_subtree, "Host: %s",
        slice_host_length, slice_host_text, fields.kafka_ext_host_length, fields.kafka_ext_host)
    -- port
    local port_offset = host_offset + host_length
    local port_length = 4
    local slice_port = buffer(port_offset, port_length)
    ext_subtree:add_le(fields.kafka_ext_port, slice_port)
    -- topic
    local topic_offset = port_offset + port_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- topic_partition
    local topic_partition_offset = topic_offset + topic_length
    local topic_partition_length = resolve_length_of_array(buffer, topic_partition_offset)
    dissect_and_add_kafka_topic_partition_array(buffer, topic_partition_offset, ext_subtree,
        fields.kafka_ext_topic_partition_array_length, fields.kafka_ext_topic_partition_array_size)
end

function dissect_and_add_kafka_topic_partition_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Partitions (%d items)",
        field_array_length, field_array_size)
    local item_length = 4
    local item_offset = offset + length
    for i = 1, array_size do
        -- partition_id
        local partition_id_offset = item_offset
        local partition_id_length = 4
        local slice_partition_id = buffer(partition_id_offset, partition_id_length)
        local partition_id = slice_partition_id:le_int()
        local label = string.format("Topic Partition: %d", partition_id)
        tree:add_le(fields.kafka_ext_partition_id, slice_partition_id)
        item_offset = item_offset + item_length
    end
end

function handle_kafka_data_offset_fetch_extension(buffer, offset, ext_subtree)
    dissect_and_add_kafka_topic_partition_offset_array(buffer, offset, ext_subtree,
        fields.kafka_ext_topic_partition_offset_array_length, fields.kafka_ext_topic_partition_offset_array_size)
end

function dissect_and_add_kafka_topic_partition_offset_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Partition Offsets (%d items)",
        field_array_length, field_array_size)
    local item_length = 4
    local item_offset = offset + length
    for i = 1, array_size do
        -- partition_offset
        local item_length, item_label = resolve_length_and_label_of_topic_partition_offset(buffer, item_offset)
        local label = string.format("Partition Offset: %s", item_label)
        local partition_offset_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        dissect_and_add_kafka_topic_partition_offset(buffer, item_offset, partition_offset_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_kafka_topic_partition_offset(buffer, offset, tree)
    -- partition_id
    local partition_id_offset = offset
    local partition_id_length = 4
    local slice_partition_id = buffer(partition_id_offset, partition_id_length)
    tree:add_le(fields.kafka_ext_partition_id, slice_partition_id)
    -- partition_offset
    local partition_offset_offset = partition_id_offset + partition_id_length
    local partition_offset_length = 8
    local slice_partition_offset = buffer(partition_offset_offset, partition_offset_length)
    tree:add_le(fields.kafka_ext_partition_offset, slice_partition_offset)
    -- leader_epoch
    local leader_epoch_offset = partition_offset_offset + partition_offset_length
    local leader_epoch_length = 4
    local slice_leader_epoch = buffer(leader_epoch_offset, leader_epoch_length)
    tree:add_le(fields.kafka_ext_leader_epoch, slice_leader_epoch)
    -- metadata
    local metadata_offset = leader_epoch_offset + leader_epoch_length
    local metadata_length, slice_metadata_length, slice_metadata_text = dissect_length_value(buffer, metadata_offset, 2)
    add_string_as_subtree(buffer(offset, metadata_length), tree, "Metadata: %s", slice_metadata_length,
        slice_metadata_text, fields.kafka_ext_metadata_length, fields.kafka_ext_metadata)
end

function resolve_length_and_label_of_topic_partition_offset(buffer, offset)
    -- partition_id
    local partition_id_offset = offset
    local partition_id_length = 4
    local slice_partition_id = buffer(partition_id_offset, partition_id_length)
    local partition_id = slice_partition_id:le_int()
    -- partition_offset
    local partition_offset_offset = partition_id_offset + partition_id_length
    local partition_offset_length = 8
    local slice_partition_offset = buffer(partition_offset_offset, partition_offset_length)
    local partition_offset = tostring(slice_partition_offset:le_int64())
    -- leader_epoch
    local leader_epoch_offset = partition_offset_offset + partition_offset_length
    local leader_epoch_length = 4
    -- metadata
    local metadata_offset = leader_epoch_offset + leader_epoch_length
    local metadata_length, slice_metadata_length, slice_metadata_text = dissect_length_value(buffer, metadata_offset, 2)
    -- result
    local record_length = partition_id_length + partition_offset_length + leader_epoch_length + metadata_length
    local label = string.format("%d [%d]", partition_id, partition_offset)
    return record_length, label
end

function handle_kafka_begin_describe_extension(buffer, offset, ext_subtree)
    -- topic
    local topic_offset = offset
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- configs
    local configs_offset = topic_offset + topic_length
    local configs_length = resolve_length_of_array(buffer, configs_offset)
    dissect_and_add_kafka_config_array(buffer, configs_offset, ext_subtree, fields.kafka_ext_config_array_length,
        fields.kafka_ext_config_array_size)
end

function dissect_and_add_kafka_config_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Configs (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- config
        local item_length, slice_length, slice_text = dissect_length_value(buffer, item_offset, 2)
        add_string_as_subtree(buffer(item_offset, item_length), tree, "Config: %s", slice_length, slice_text,
            fields.kafka_ext_config_length, fields.kafka_ext_config)
        item_offset = item_offset + item_length
    end
end

function handle_kafka_data_describe_extension(buffer, offset, ext_subtree)
    -- configs
    local configs_offset = offset
    local configs_length = resolve_length_of_array(buffer, configs_offset)
    dissect_and_add_kafka_config_struct_array(buffer, configs_offset, ext_subtree, fields.kafka_ext_config_array_length,
        fields.kafka_ext_config_array_size)
end

function dissect_and_add_kafka_config_struct_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Configs (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- config
        local item_length, item_label = resolve_length_and_label_of_kafka_config_struct(buffer, item_offset)
        local label = string.format("Config: %s", item_label)
        local config_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        dissect_and_add_kafka_config_struct(buffer, item_offset, config_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_kafka_config_struct(buffer, offset, tree, label_format)
    -- name
    local name_offset = offset
    local name_length, slice_name_length, slice_name_text = dissect_length_value(buffer, name_offset, 2)
    add_string_as_subtree(buffer(name_offset, name_length), tree, "Name: %s", slice_name_length,
        slice_name_text, fields.kafka_ext_name_length, fields.kafka_ext_name)
    -- authority
    local value_offset = name_offset + name_length
    local value_length, slice_value_length, slice_value_text = dissect_length_value(buffer, value_offset, 2)
    add_string_as_subtree(buffer(value_offset, value_length), tree, "Value: %s", slice_value_length,
        slice_value_text, fields.kafka_ext_value_length, fields.kafka_ext_value)
end

function resolve_length_and_label_of_kafka_config_struct(buffer, offset)
    -- name
    local name_offset = offset
    local name_length, slice_name_length, slice_name_text = dissect_length_value(buffer, name_offset, 2)
    local name = slice_name_text:string()
    -- authority
    local value_offset = name_offset + name_length
    local value_length, slice_value_length, slice_value_text = dissect_length_value(buffer, value_offset, 2)
    local value = slice_value_text:string()
    -- result
    local record_length = name_length + value_length
    local label = string.format("%s: %s", name, value)
    return record_length, label
end

function handle_kafka_begin_fetch_extension(buffer, offset, ext_subtree)
    -- topic
    local topic_offset = offset
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.kafka_ext_topic_length, fields.kafka_ext_topic)
    -- partition
    local partition_offset = topic_offset + topic_length
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- filters
    local filters_offset = partition_offset + partition_length
    local filters_length = resolve_length_of_array(buffer, filters_offset)
    dissect_and_add_kafka_filters_array(buffer, filters_offset, ext_subtree,
        fields.kafka_ext_filters_array_length, fields.kafka_ext_filters_array_size)
    -- evaluation
    local evaluation_offset = filters_offset + filters_length
    local evaluation_length = 1
    local slice_evaluation = buffer(evaluation_offset, evaluation_length)
    ext_subtree:add_le(fields.kafka_ext_evaluation, slice_evaluation)
    -- isolation
    local isolation_offset = evaluation_offset + evaluation_length
    local isolation_length = 1
    local slice_isolation = buffer(isolation_offset, isolation_length)
    ext_subtree:add_le(fields.kafka_ext_isolation, slice_isolation)
    -- delta_type
    local delta_type_offset = isolation_offset + isolation_length
    local delta_type_length = 1
    local slice_delta_type = buffer(delta_type_offset, delta_type_length)
    ext_subtree:add_le(fields.kafka_ext_delta_type, slice_delta_type)
end

function handle_kafka_data_fetch_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.kafka_ext_deferred, slice_deferred)
    -- timestamp
    local timestamp_offset = deferred_offset + deferred_length
    local timestamp_length = 8
    local slice_timestamp = buffer(timestamp_offset, timestamp_length)
    ext_subtree:add_le(fields.sse_ext_timestamp, slice_timestamp)
    -- header_size_max
    local header_size_max_offset = timestamp_offset + timestamp_length
    local header_size_max_length = 4
    local slice_header_size_max = buffer(header_size_max_offset, header_size_max_length)
    ext_subtree:add_le(fields.kafka_ext_header_size_max, slice_header_size_max)
    -- producer_id
    local producer_id_offset = header_size_max_offset + header_size_max_length
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    ext_subtree:add_le(fields.kafka_ext_producer_id, slice_producer_id)
    -- filters
    local filters_offset = producer_id_offset + producer_id_length
    local filters_length = 8
    local slice_filters = buffer(filters_offset, filters_length)
    ext_subtree:add_le(fields.kafka_ext_filters, slice_filters)
    -- partition
    local partition_offset = filters_offset + filters_length
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- key
    local key_offset = partition_offset + partition_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), string.format("Key: %s", key_label))
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
    -- delta
    local delta_offset = key_offset + key_length
    local delta_length, delta_label = resolve_length_and_label_of_kafka_delta(buffer, delta_offset)
    local delta_subtree = ext_subtree:add(zilla_protocol, buffer(delta_offset, delta_length), string.format("Delta: %s", delta_label))
    dissect_and_add_kafka_delta(buffer, delta_offset, delta_subtree)
    -- header_array
    local header_array_offset = delta_offset + delta_length
    dissect_and_add_kafka_header_array(buffer, header_array_offset, ext_subtree, fields.kafka_ext_headers_array_length,
        fields.kafka_ext_headers_array_size)
end

function handle_kafka_flush_fetch_extension(buffer, offset, ext_subtree)
    -- partition
    local partition_offset = offset
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- transactions
    local transactions_offset = partition_offset + partition_length
    local transactions_length = resolve_length_of_array(buffer, transactions_offset)
    dissect_and_add_kafka_transactions_array(buffer, transactions_offset, ext_subtree, fields.kafka_ext_transactions_array_length,
        fields.kafka_ext_transactions_array_size)
    -- filters
    local filters_offset = transactions_offset + transactions_length
    local filters_length = resolve_length_of_array(buffer, filters_offset)
    dissect_and_add_kafka_filters_array(buffer, filters_offset, ext_subtree,
        fields.kafka_ext_filters_array_length, fields.kafka_ext_filters_array_size)
    -- evaluation
    local evaluation_offset = filters_offset + filters_length
    local evaluation_length = 1
    local slice_evaluation = buffer(evaluation_offset, evaluation_length)
    ext_subtree:add_le(fields.kafka_ext_evaluation, slice_evaluation)
end

function dissect_and_add_kafka_transactions_array(buffer, offset, tree, field_array_length, field_array_size)
    local length, array_size = dissect_and_add_array_header_as_subtree(buffer, offset, tree, "Transactions (%d items)",
        field_array_length, field_array_size)
    local item_offset = offset + length
    for i = 1, array_size do
        -- transaction
        local item_length, item_label = resolve_length_and_label_of_kafka_transaction(buffer, item_offset)
        local label = string.format("Transaction: %s", item_label)
        local transaction_subtree = tree:add(zilla_protocol, buffer(item_offset, item_length), label)
        dissect_and_add_kafka_transaction(buffer, item_offset, transaction_subtree)
        item_offset = item_offset + item_length
    end
end

function dissect_and_add_kafka_transaction(buffer, offset, tree, label_format)
    -- transaction_result
    local transaction_result_offset = offset
    local transaction_result_length = 1
    local slice_transaction_result = buffer(transaction_result_offset, transaction_result_length)
    tree:add_le(fields.kafka_ext_transaction_result, slice_transaction_result)
    -- producer_id
    local producer_id_offset = transaction_result_offset + transaction_result_length
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    tree:add_le(fields.kafka_ext_producer_id, slice_producer_id)
end

function resolve_length_and_label_of_kafka_transaction(buffer, offset)
    -- transaction_result
    local transaction_result_offset = offset
    local transaction_result_length = 1
    local slice_transaction_result = buffer(transaction_result_offset, transaction_result_length)
    local transaction_result = kafka_ext_transaction_result_types[slice_transaction_result:le_int()]
    -- producer_id
    local producer_id_offset = transaction_result_offset + transaction_result_length
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    local producer_id = tostring(slice_producer_id:le_uint64())
    -- result
    local record_length = transaction_result_length + producer_id_length
    local label = string.format("[%s] 0x%016x", transaction_result, producer_id)
    return record_length, label
end

function handle_kafka_begin_produce_extension(buffer, offset, ext_subtree)
    -- transaction
    local transaction_offset = offset
    local transaction_length, slice_transaction_length, slice_transaction_text = dissect_length_value(buffer, transaction_offset, 1)
    add_string_as_subtree(buffer(transaction_offset, transaction_length), ext_subtree, "Transaction: %s", slice_transaction_length,
        slice_transaction_text, fields.kafka_ext_transaction_length, fields.kafka_ext_transaction)
    -- topic
    local topic_offset = transaction_offset + transaction_length
    local topic_length, slice_topic_length, slice_topic_text = dissect_length_value(buffer, topic_offset, 2)
    add_string_as_subtree(buffer(topic_offset, topic_length), ext_subtree, "Topic: %s",
        slice_topic_length, slice_topic_text, fields.mqtt_ext_topic_length, fields.mqtt_ext_topic)
    -- partition
    local partition_offset = topic_offset + topic_length
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
end

function handle_kafka_data_produce_extension(buffer, offset, ext_subtree)
    -- deferred
    local deferred_offset = offset
    local deferred_length = 4
    local slice_deferred = buffer(deferred_offset, deferred_length)
    ext_subtree:add_le(fields.kafka_ext_deferred, slice_deferred)
    -- timestamp
    local timestamp_offset = deferred_offset + deferred_length
    local timestamp_length = 8
    local slice_timestamp = buffer(timestamp_offset, timestamp_length)
    ext_subtree:add_le(fields.sse_ext_timestamp, slice_timestamp)
    -- producer_id
    local producer_id_offset = timestamp_offset + timestamp_length
    local producer_id_length = 8
    local slice_producer_id = buffer(producer_id_offset, producer_id_length)
    ext_subtree:add_le(fields.kafka_ext_producer_id, slice_producer_id)
    -- producer_epoch
    local producer_epoch_offset = producer_id_offset + producer_id_length
    local producer_epoch_length = 2
    local slice_producer_epoch = buffer(producer_epoch_offset, producer_epoch_length)
    ext_subtree:add_le(fields.kafka_ext_producer_epoch, slice_producer_epoch)
    -- sequence
    local sequence_offset = producer_epoch_offset + producer_epoch_length
    local sequence_length = 4
    local slice_sequence = buffer(sequence_offset, sequence_length)
    ext_subtree:add_le(fields.kafka_ext_sequence, slice_sequence)
    -- crc32c
    local crc32c_offset = sequence_offset + sequence_length
    local crc32c_length = 4
    local slice_crc32c = buffer(crc32c_offset, crc32c_length)
    ext_subtree:add_le(fields.kafka_ext_crc32c, slice_crc32c)
    -- ack_mode
    local ack_mode_offset = crc32c_offset + crc32c_length
    local ack_mode_length = 2
    local slice_ack_mode_id = buffer(ack_mode_offset, ack_mode_length)
    local ack_mode = kafka_ext_ack_modes[slice_ack_mode_id:le_int()]
    ext_subtree:add_le(fields.kafka_ext_ack_mode_id, slice_ack_mode_id)
    ext_subtree:add(fields.kafka_ext_ack_mode, ack_mode)
    -- key
    local key_offset = ack_mode_offset + ack_mode_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), string.format("Key: %s", key_label))
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
    -- header_array
    local header_array_offset = key_offset + key_length
    dissect_and_add_kafka_header_array(buffer, header_array_offset, ext_subtree, fields.kafka_ext_headers_array_length,
        fields.kafka_ext_headers_array_size)
end

function handle_kafka_flush_produce_extension(buffer, offset, ext_subtree)
    -- partition
    local partition_offset = offset
    local partition_length = resolve_length_of_kafka_offset(buffer, partition_offset)
    dissect_and_add_kafka_offset(buffer, partition_offset, ext_subtree, "Partition: %d [%d]")
    -- key
    local key_offset = partition_offset + partition_length
    local key_length, key_label = resolve_length_and_label_of_kafka_key(buffer, key_offset, 0)
    local key_subtree = ext_subtree:add(zilla_protocol, buffer(key_offset, key_length), string.format("Key: %s", key_label))
    dissect_and_add_kafka_key(buffer, key_offset, key_subtree)
    -- error
    local error_offset = key_offset + key_length
    local error_length = 4
    local slice_error = buffer(error_offset, error_length)
    ext_subtree:add_le(fields.kafka_ext_error, slice_error)
end

function handle_kafka_reset_extension(buffer, offset, ext_subtree)
    -- error
    local error_offset = offset
    local error_length = 4
    local slice_error = buffer(error_offset, error_length)
    ext_subtree:add_le(fields.kafka_ext_error, slice_error)
    -- consumer_id
    local consumer_id_offset = error_offset + error_length
    local consumer_id_length, slice_consumer_id_length, slice_consumer_id_text = dissect_length_value(buffer, consumer_id_offset, 2)
    add_string_as_subtree(buffer(consumer_id_offset, consumer_id_length), ext_subtree, "Consumer ID: %s",
        slice_consumer_id_length, slice_consumer_id_text, fields.kafka_ext_consumer_id_length, fields.kafka_ext_consumer_id)
end

register_dissector(KAFKA_ID, "kafka", handle_kafka_extension, function (payload) return Dissector.get("kafka") end)

