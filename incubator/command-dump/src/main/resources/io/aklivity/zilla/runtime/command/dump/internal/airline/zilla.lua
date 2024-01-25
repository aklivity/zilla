--[[

    Copyright 2021-2023 Aklivity Inc

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

AMQP_ID = 0x112dc182
FILESYSTEM_ID = 0xe4e6aa9e
GRPC_ID = 0xf9c7583a
HTTP_ID = 0x8ab62046
KAFKA_ID = 0x084b20e1
MQTT_ID = 0xd0d41a76
PROXY_ID = 0x8dcea850
SSE_ID = 0x03409e2e
TLS_ID = 0x99f321bc
WS_ID = 0x569dcde9

local flags_types = {
    [0] = "Not set",
    [1] = "Set"
}

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

local grpc_types = {
    [0] = "TEXT",
    [1] = "BASE64"
}

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

local fields = {
    -- header
    frame_type_id = ProtoField.uint32("zilla.frame_type_id", "Frame Type ID", base.HEX),
    frame_type = ProtoField.string("zilla.frame_type", "Frame Type", base.NONE),
    protocol_type_id = ProtoField.uint32("zilla.protocol_type_id", "Protocol Type ID", base.HEX),
    protocol_type = ProtoField.string("zilla.protocol_type", "Protocol Type", base.NONE),
    stream_type_id = ProtoField.uint32("zilla.stream_type_id", "Stream Type ID", base.HEX),
    stream_type = ProtoField.string("zilla.stream_type", "Stream Type", base.NONE),
    worker = ProtoField.int32("zilla.worker", "Worker", base.DEC),
    offset = ProtoField.uint32("zilla.offset", "Offset", base.HEX),

    -- labels
    origin_namespace = ProtoField.string("zilla.origin_namespace", "Origin Namespace", base.STRING),
    origin_binding = ProtoField.string("zilla.origin_binding", "Origin Binding", base.STRING),
    routed_namespace = ProtoField.string("zilla.routed_namespace", "Routed Namespace", base.STRING),
    routed_binding = ProtoField.string("zilla.routed_binding", "Routed Binding", base.STRING),

    -- all frames
    origin_id = ProtoField.uint64("zilla.origin_id", "Origin ID", base.HEX),
    routed_id = ProtoField.uint64("zilla.routed_id", "Routed ID", base.HEX),
    stream_id = ProtoField.uint64("zilla.stream_id", "Stream ID", base.HEX),
    direction = ProtoField.string("zilla.direction", "Direction", base.NONE),
    initial_id = ProtoField.uint64("zilla.initial_id", "Initial ID", base.HEX),
    reply_id = ProtoField.uint64("zilla.reply_id", "Reply ID", base.HEX),
    sequence = ProtoField.int64("zilla.sequence", "Sequence", base.DEC),
    acknowledge = ProtoField.int64("zilla.acknowledge", "Acknowledge", base.DEC),
    maximum = ProtoField.int32("zilla.maximum", "Maximum", base.DEC),
    timestamp = ProtoField.uint64("zilla.timestamp", "Timestamp", base.HEX),
    trace_id = ProtoField.uint64("zilla.trace_id", "Trace ID", base.HEX),
    authorization = ProtoField.uint64("zilla.authorization", "Authorization", base.HEX),

    -- begin frame
    affinity = ProtoField.uint64("zilla.affinity", "Affinity", base.HEX),

    -- data frame
    flags = ProtoField.uint8("zilla.flags", "Flags", base.HEX),
    flags_fin = ProtoField.uint8("zilla.flags_fin", "FIN", base.DEC, flags_types, 0x01),
    flags_init = ProtoField.uint8("zilla.flags_init", "INIT", base.DEC, flags_types, 0x02),
    flags_incomplete = ProtoField.uint8("zilla.flags_incomplete", "INCOMPLETE", base.DEC, flags_types, 0x04),
    flags_skip = ProtoField.uint8("zilla.flags_skip", "SKIP", base.DEC, flags_types, 0x08),
    budget_id = ProtoField.uint64("zilla.budget_id", "Budget ID", base.HEX),
    reserved = ProtoField.int32("zilla.reserved", "Reserved", base.DEC),
    payload_length = ProtoField.int32("zilla.payload_length", "Length", base.DEC),
    progress = ProtoField.int64("zilla.progress", "Progress", base.DEC),
    progress_maximum = ProtoField.string("zilla.progress_maximum", "Progress/Maximum", base.NONE),
    payload = ProtoField.protocol("zilla.payload", "Payload", base.HEX),

    -- window frame
    padding = ProtoField.int32("zilla.padding", "Padding", base.DEC),
    minimum = ProtoField.int32("zilla.minimum", "Minimum", base.DEC),
    capabilities = ProtoField.uint8("zilla.capabilities", "Capabilities", base.HEX),

    -- signal frame
    cancel_id = ProtoField.uint64("zilla.cancel_id", "Cancel ID", base.HEX),
    signal_id = ProtoField.uint32("zilla.signal_id", "Signal ID", base.HEX),
    context_id = ProtoField.uint32("zilla.context_id", "Context ID", base.HEX),

    -- proxy extension
    --     address
    proxy_ext_address_family = ProtoField.uint8("zilla.proxy_ext.address_family", "Family", base.DEC,
        proxy_ext_address_family_types),
    proxy_ext_address_protocol = ProtoField.uint8("zilla.proxy_ext.address_protocol", "Protocol", base.DEC,
        proxy_ext_address_protocol_types),
    proxy_ext_address_inet_source_port = ProtoField.uint16("zilla.proxy_ext.address_inet_source_port", "Source Port",
        base.DEC),
    proxy_ext_address_inet_destination_port = ProtoField.uint16("zilla.proxy_ext.address_inet_destination_port",
        "Destination Port", base.DEC),
    proxy_ext_address_inet_source = ProtoField.string("zilla.proxy_ext.address_inet_source", "Source", base.NONE),
    proxy_ext_address_inet_destination = ProtoField.string("zilla.proxy_ext.address_inet_destination", "Destination",
        base.NONE),
    proxy_ext_address_inet4_source = ProtoField.new("Source", "zilla.proxy_ext.address_inet4_source", ftypes.IPv4),
    proxy_ext_address_inet4_destination = ProtoField.new("Destination", "zilla.proxy_ext.address_inet4_destination",
        ftypes.IPv4),
    proxy_ext_address_inet6_source = ProtoField.new("Source", "zilla.proxy_ext.address_inet6_source", ftypes.IPv6),
    proxy_ext_address_inet6_destination = ProtoField.new("Destination", "zilla.proxy_ext.address_inet6_destination",
        ftypes.IPv6),
    proxy_ext_address_unix_source = ProtoField.string("zilla.proxy_ext.address_unix_source", "Source", base.NONE),
    proxy_ext_address_unix_destination = ProtoField.string("zilla.proxy_ext.address_unix_destination", "Destination",
        base.NONE),
    --     info
    proxy_ext_info_array_length = ProtoField.int8("zilla.proxy_ext.info_array_length", "Length", base.DEC),
    proxy_ext_info_array_size = ProtoField.int8("zilla.proxy_ext.info_array_size", "Size", base.DEC),
    proxy_ext_info_type = ProtoField.uint8("zilla.proxy_ext.info_type", "Type", base.HEX, proxy_ext_info_types),
    proxy_ext_info_length = ProtoField.int16("zilla.proxy_ext.info_length", "Length", base.DEC),
    proxy_ext_info_alpn = ProtoField.string("zilla.proxy_ext.info_alpn", "Value", base.NONE),
    proxy_ext_info_authority = ProtoField.string("zilla.proxy_ext.info_authority", "Value", base.NONE),
    proxy_ext_info_identity = ProtoField.bytes("zilla.proxy_ext.info_identity", "Value", base.NONE),
    proxy_ext_info_namespace = ProtoField.string("zilla.proxy_ext.info_namespace", "Value", base.NONE),
    proxy_ext_info_secure = ProtoField.string("zilla.proxy_ext.info_secure", "Value", base.NONE),
    proxy_ext_info_secure_type = ProtoField.uint8("zilla.proxy_ext.info_secure_type", "Secure Type", base.HEX,
        proxy_ext_secure_info_types),

    -- http extension
    --     headers
    http_ext_headers_array_length = ProtoField.int8("zilla.http_ext.headers_array_length", "Length", base.DEC),
    http_ext_headers_array_size = ProtoField.int8("zilla.http_ext.headers_array_size", "Size", base.DEC),
    http_ext_header_name_length = ProtoField.int8("zilla.http_ext.header_name_length", "Length", base.DEC),
    http_ext_header_name = ProtoField.string("zilla.http_ext.header_name", "Name", base.NONE),
    http_ext_header_value_length = ProtoField.int16("zilla.http_ext.header_value_length", "Length", base.DEC),
    http_ext_header_value = ProtoField.string("zilla.http_ext.header_value", "Value", base.NONE),
    --    promise id
    http_ext_promise_id = ProtoField.uint64("zilla.promise_id", "Promise ID", base.HEX),

    -- grpc extension
    grpc_ext_scheme_length = ProtoField.int16("zilla.grpc_ext.scheme_length", "Length", base.DEC),
    grpc_ext_scheme = ProtoField.string("zilla.grpc_ext.scheme", "Scheme", base.NONE),
    grpc_ext_authority_length = ProtoField.int16("zilla.grpc_ext.authority_length", "Length", base.DEC),
    grpc_ext_authority = ProtoField.string("zilla.grpc_ext.authority", "Authority", base.NONE),
    grpc_ext_service_length = ProtoField.int16("zilla.grpc_ext.service_length", "Length", base.DEC),
    grpc_ext_service = ProtoField.string("zilla.grpc_ext.service", "Service", base.NONE),
    grpc_ext_method_length = ProtoField.int16("zilla.grpc_ext.method_length", "Length", base.DEC),
    grpc_ext_method = ProtoField.string("zilla.grpc_ext.method", "Method", base.NONE),
    grpc_ext_deferred = ProtoField.int32("zilla.grpc_ext.deferred", "Deferred", base.DEC),
    grpc_ext_status_length = ProtoField.int16("zilla.grpc_ext.status_length", "Length", base.DEC),
    grpc_ext_status = ProtoField.string("zilla.grpc_ext.status", "Status", base.NONE),
    --    metadata
    grpc_ext_metadata_array_length = ProtoField.int8("zilla.grpc_ext.metadata_array_length", "Length", base.DEC),
    grpc_ext_metadata_array_size = ProtoField.int8("zilla.grpc_ext.metadata_array_size", "Size", base.DEC),
    grpc_ext_metadata_type = ProtoField.uint8("zilla.grpc_ext.metadata_type", "Type", base.DEC, grpc_types),
    grpc_ext_metadata_name_length_varint = ProtoField.bytes("zilla.grpc_ext.metadata_name_varint", "Length (varint32)",
        base.NONE),
    grpc_ext_metadata_name_length = ProtoField.int32("zilla.grpc_ext.metadata_name_length", "Length", base.DEC),
    grpc_ext_metadata_name = ProtoField.string("zilla.grpc_ext.metadata_name", "Name", base.NONE),
    grpc_ext_metadata_value_length_varint = ProtoField.bytes("zilla.grpc_ext.metadata_value_length_varint", "Length (varint32)",
        base.NONE),
    grpc_ext_metadata_value_length = ProtoField.int32("zilla.grpc_ext.metadata_value_length", "Length", base.DEC),
    grpc_ext_metadata_value = ProtoField.string("zilla.grpc_ext.metadata_value", "Value", base.NONE),

    -- sse extension
    sse_ext_scheme_length = ProtoField.int16("zilla.sse_ext.scheme_length", "Length", base.DEC),
    sse_ext_scheme = ProtoField.string("zilla.sse_ext.scheme", "Scheme", base.NONE),
    sse_ext_authority_length = ProtoField.int16("zilla.sse_ext.authority_length", "Length", base.DEC),
    sse_ext_authority = ProtoField.string("zilla.sse_ext.authority", "Authority", base.NONE),
    sse_ext_path_length = ProtoField.int16("zilla.sse_ext.path_length", "Length", base.DEC),
    sse_ext_path = ProtoField.string("zilla.sse_ext.path", "Path", base.NONE),
    sse_ext_last_id_length = ProtoField.int8("zilla.sse_ext.last_id_length", "Length", base.DEC),
    sse_ext_last_id = ProtoField.string("zilla.sse_ext.last_id", "Last ID", base.NONE),
    sse_ext_timestamp = ProtoField.uint64("zilla.sse_ext.timestamp", "Timestamp", base.HEX),
    sse_ext_id_length = ProtoField.int8("zilla.sse_ext.id_length", "Length", base.DEC),
    sse_ext_id = ProtoField.string("zilla.sse_ext.id", "ID", base.NONE),
    sse_ext_type_length = ProtoField.int8("zilla.sse_ext.type_length", "Length", base.DEC),
    sse_ext_type = ProtoField.string("zilla.sse_ext.type", "Type", base.NONE),

    -- ws extension
    ws_ext_protocol_length = ProtoField.int8("zilla.ws_ext.protocol_length", "Length", base.DEC),
    ws_ext_protocol = ProtoField.string("zilla.ws_ext.protocol", "Protocol", base.NONE),
    ws_ext_scheme_length = ProtoField.int8("zilla.ws_ext.scheme_length", "Length", base.DEC),
    ws_ext_scheme = ProtoField.string("zilla.ws_ext.scheme", "Scheme", base.NONE),
    ws_ext_authority_length = ProtoField.int8("zilla.ws_ext.authority_length", "Length", base.DEC),
    ws_ext_authority = ProtoField.string("zilla.ws_ext.authority", "Authority", base.NONE),
    ws_ext_path_length = ProtoField.int8("zilla.ws_ext.path_length", "Length", base.DEC),
    ws_ext_path = ProtoField.string("zilla.ws_ext.path", "Path", base.NONE),
    ws_ext_flags = ProtoField.uint8("zilla.ws_ext.flags", "Flags", base.HEX),
    ws_ext_info = ProtoField.bytes("zilla.ws_ext.info", "Info", base.NONE),
    ws_ext_code = ProtoField.int16("zilla.ws_ext.code", "Code", base.DEC),
    ws_ext_reason_length = ProtoField.int8("zilla.ws_ext.reason_length", "Length", base.DEC),
    ws_ext_reason = ProtoField.string("zilla.ws_ext.reason", "Reason", base.NONE),

    -- filesystem extension
    filesystem_ext_capabilities = ProtoField.uint32("zilla.filesystem_ext.capabilities", "Capabilities", base.HEX),
    filesystem_ext_capabilities_read_payload = ProtoField.uint32("zilla.filesystem_ext.capabilities_read_payload",
        "READ_PAYLOAD", base.DEC, flags_types, 0x01),
    filesystem_ext_capabilities_read_extension = ProtoField.uint32("zilla.filesystem_ext.capabilities_read_extension",
        "READ_EXTENSION", base.DEC, flags_types, 0x02),
    filesystem_ext_capabilities_read_changes = ProtoField.uint32("zilla.filesystem_ext.capabilities_read_changes",
        "READ_CHANGES", base.DEC, flags_types, 0x04),
    filesystem_ext_path_length = ProtoField.int16("zilla.filesystem_ext.path_length", "Length", base.DEC),
    filesystem_ext_path = ProtoField.string("zilla.filesystem_ext.path", "Path", base.NONE),
    filesystem_ext_type_length = ProtoField.int16("zilla.filesystem_ext.type_length", "Length", base.DEC),
    filesystem_ext_type = ProtoField.string("zilla.filesystem_ext.type", "Type", base.NONE),
    filesystem_ext_payload_size = ProtoField.int64("zilla.filesystem_ext.payload_size", "Payload Size", base.DEC),
    filesystem_ext_tag_length = ProtoField.int16("zilla.filesystem_ext.tag_length", "Length", base.DEC),
    filesystem_ext_tag = ProtoField.string("zilla.filesystem_ext.tag", "Tag", base.NONE),
    filesystem_ext_timeout = ProtoField.int64("zilla.filesystem_ext.timeout", "Timeout", base.DEC),

    -- mqtt extension
    mqtt_ext_kind = ProtoField.uint8("zilla.mqtt_ext.kind", "Kind", base.DEC, mqtt_ext_kinds),
    --     begin
    mqtt_ext_qos = ProtoField.uint8("zilla.mqtt_ext.qos", "QoS", base.DEC, mqtt_ext_qos_types),
    mqtt_ext_client_id_length = ProtoField.int16("zilla.mqtt_ext.client_id_length", "Length", base.DEC),
    mqtt_ext_client_id = ProtoField.string("zilla.mqtt_ext.client_id", "Client ID", base.NONE),
    mqtt_ext_topic_length = ProtoField.int16("zilla.mqtt_ext.topic_length", "Length", base.DEC),
    mqtt_ext_topic = ProtoField.string("zilla.mqtt_ext.topic", "Topic", base.NONE),
    mqtt_ext_expiry = ProtoField.int32("zilla.mqtt_ext.expiry", "Expiry", base.DEC),
    mqtt_ext_qos_max = ProtoField.uint16("zilla.mqtt_ext.qos_max", "QoS Maximum", base.DEC),
    mqtt_ext_packet_size_max = ProtoField.uint32("zilla.mqtt_ext.packet_size_max", "Packet Size Maximum", base.DEC),
    --     capabilities
    mqtt_ext_capabilities = ProtoField.uint8("zilla.mqtt_ext.capabilities", "Capabilities", base.HEX),
    mqtt_ext_capabilities_retain = ProtoField.uint8("zilla.mqtt_ext.capabilities_retain", "RETAIN",
        base.DEC, flags_types, 0x01),
    mqtt_ext_capabilities_wildcard = ProtoField.uint8("zilla.mqtt_ext.capabilities_wildcard", "WILDCARD",
        base.DEC, flags_types, 0x02),
    mqtt_ext_capabilities_subscription_ids = ProtoField.uint8("zilla.mqtt_ext.capabilities_subscription_ids",
        "SUBSCRIPTION_IDS", base.DEC, flags_types, 0x04),
    mqtt_ext_capabilities_shared_subscriptions = ProtoField.uint8("zilla.mqtt_ext.capabilities_shared_subscriptions",
        "SHARED_SUBSCRIPTIONS", base.DEC, flags_types, 0x08),
    --     subscribe flags
    mqtt_ext_subscribe_flags = ProtoField.uint8("zilla.mqtt_ext.subscribe_flags", "Flags", base.HEX),
    mqtt_ext_subscribe_flags_send_retained = ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_send_retained",
        "SEND_RETAINED", base.DEC, flags_types, 0x01),
    mqtt_ext_subscribe_flags_retain_as_published = ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_retain_as_published",
        "RETAIN_AS_PUBLISHED", base.DEC, flags_types, 0x02),
    mqtt_ext_subscribe_flags_no_local = ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_no_local",
        "NO_LOCAL", base.DEC, flags_types, 0x04),
    mqtt_ext_subscribe_flags_retain = ProtoField.uint8("zilla.mqtt_ext.subscribe_flags_retain",
        "RETAIN", base.DEC, flags_types, 0x08),
    --     publish flags
    mqtt_ext_publish_flags = ProtoField.uint8("zilla.mqtt_ext.publish_flags", "Flags", base.HEX),
    mqtt_ext_publish_flags_retain = ProtoField.uint8("zilla.mqtt_ext.publish_flags_retain", "RETAIN", base.DEC,
        flags_types, 0x01),
    --     session flags
    mqtt_ext_session_flags = ProtoField.uint8("zilla.mqtt_ext.session_flags", "Flags", base.HEX),
    mqtt_ext_session_flags_clean_start = ProtoField.uint8("zilla.mqtt_ext.session_flags_clean_start", "CLEAN_START",
        base.DEC, flags_types, 0x02),
    mqtt_ext_session_flags_will = ProtoField.uint8("zilla.mqtt_ext.session_flags_will", "WILL", base.DEC, flags_types, 0x04),
    --     filters
    mqtt_ext_filters_array_length = ProtoField.int8("zilla.mqtt_ext.filters_array_length", "Length", base.DEC),
    mqtt_ext_filters_array_size = ProtoField.int8("zilla.mqtt_ext.filters_array_size", "Size", base.DEC),
    mqtt_ext_filter_subscription_id = ProtoField.uint32("zilla.mqtt_ext.filter_subscription_id", "Subscription ID", base.HEX),
    mqtt_ext_filter_qos = ProtoField.uint8("zilla.mqtt_ext.filter_qos", "QoS", base.DEC, mqtt_ext_qos_types),
    mqtt_ext_filter_reason_code = ProtoField.uint8("zilla.mqtt_ext.filter_reason_code", "Reason Code", base.DEC),
    mqtt_ext_filter_pattern_length = ProtoField.int16("zilla.mqtt_ext.filter_pattern_length", "Length", base.DEC),
    mqtt_ext_filter_pattern = ProtoField.string("zilla.mqtt_ext.filter_pattern", "Pattern", base.NONE),
    --     data
    mqtt_ext_deferred = ProtoField.uint32("zilla.mqtt_ext.deferred", "Deferred", base.DEC),
    mqtt_ext_expiry_interval = ProtoField.int16("zilla.mqtt_ext.expiry_interval", "Expiry Interval", base.DEC),
    mqtt_ext_content_type_length = ProtoField.int16("zilla.mqtt_ext.content_type_length", "Length", base.DEC),
    mqtt_ext_content_type = ProtoField.string("zilla.mqtt_ext.content_type", "Content Type", base.NONE),
    mqtt_ext_payload_format = ProtoField.uint8("zilla.mqtt_ext.payload_format", "Payload Format", base.DEC,
        mqtt_ext_payload_format_types),
    mqtt_ext_response_topic_length = ProtoField.int16("zilla.mqtt_ext.response_topic_length", "Length", base.DEC),
    mqtt_ext_response_topic = ProtoField.string("zilla.mqtt_ext.response_topic", "Response Topic", base.NONE),
    mqtt_ext_correlation_length = ProtoField.int16("zilla.mqtt_ext.correlation_length", "Length", base.DEC),
    mqtt_ext_correlation = ProtoField.bytes("zilla.mqtt_ext.correlation", "Correlation", base.NONE),
    mqtt_ext_properties_array_length = ProtoField.int8("zilla.mqtt_ext.properties_array_length", "Length", base.DEC),
    mqtt_ext_properties_array_size = ProtoField.int8("zilla.mqtt_ext.properties_array_size", "Size", base.DEC),
    mqtt_ext_property_key_length = ProtoField.int16("zilla.mqtt_ext.property_key_length", "Length", base.DEC),
    mqtt_ext_property_key = ProtoField.string("zilla.mqtt_ext.property_key", "Key", base.NONE),
    mqtt_ext_property_value_length = ProtoField.int16("zilla.mqtt_ext.property_value_length", "Length", base.DEC),
    mqtt_ext_property_value = ProtoField.string("zilla.mqtt_ext.property_value", "Value", base.NONE),
    mqtt_ext_data_kind = ProtoField.uint8("zilla.mqtt_ext.data_kind", "Data Kind", base.HEX, mqtt_ext_data_kinds),
    mqtt_ext_packet_id = ProtoField.uint16("zilla.mqtt_ext.packet_id", "Packet ID", base.HEX),
    mqtt_ext_subscription_ids_array_length = ProtoField.int8("zilla.mqtt_ext.subscription_ids_array_length", "Length",
        base.DEC),
    mqtt_ext_subscription_ids_array_size = ProtoField.int8("zilla.mqtt_ext.subscription_ids_array_size", "Size",
        base.DEC),
    mqtt_ext_subscription_id_varuint = ProtoField.bytes("zilla.mqtt_ext.subsciption_id_varuint", "Subscription ID (varuint32)",
        base.NONE),
    mqtt_ext_subscription_id = ProtoField.int32("zilla.mqtt_ext.subsciption_id", "Subscription ID", base.DEC),
    --     reset
    mqtt_ext_server_ref_length = ProtoField.int16("zilla.mqtt_ext.server_ref_length", "Length", base.DEC),
    mqtt_ext_server_ref = ProtoField.string("zilla.mqtt_ext.server_ref", "Value", base.NONE),
    mqtt_ext_reason_code = ProtoField.uint8("zilla.mqtt_ext.reason_code", "Reason Code", base.DEC),
    mqtt_ext_reason_length = ProtoField.int16("zilla.mqtt_ext.reason_length", "Length", base.DEC),
    mqtt_ext_reason = ProtoField.string("zilla.mqtt_ext.reason", "Value", base.NONE),
    --     reset
    mqtt_ext_state = ProtoField.uint8("zilla.mqtt_ext.state", "State", base.DEC, mqtt_ext_offset_state_flags),

    -- kafka extension
    kafka_ext_api = ProtoField.uint8("zilla.kafka_ext.api", "API", base.DEC, kafka_ext_apis),
    --     reset
    kafka_ext_error = ProtoField.int32("zilla.kafka_ext.error", "Error", base.DEC),
    --     consumer
    kafka_ext_group_id_length = ProtoField.int16("zilla.kafka_ext.group_id_length", "Length", base.DEC),
    kafka_ext_group_id = ProtoField.string("zilla.kafka_ext.group_id", "Group ID", base.NONE),
    kafka_ext_consumer_id_length = ProtoField.int16("zilla.kafka_ext.consumer_id_length", "Length", base.DEC),
    kafka_ext_consumer_id = ProtoField.string("zilla.kafka_ext.consumer_id", "Consumer ID", base.NONE),
    kafka_ext_host_length = ProtoField.int16("zilla.kafka_ext.host_length", "Length", base.DEC),
    kafka_ext_host = ProtoField.string("zilla.kafka_ext.host", "Host", base.NONE),
    kafka_ext_port = ProtoField.int32("zilla.kafka_ext.port", "Port", base.DEC),
    kafka_ext_timeout = ProtoField.int32("zilla.kafka_ext.timeout", "Timeout", base.DEC),
    kafka_ext_topic_length = ProtoField.int16("zilla.kafka_ext.topic_length", "Length", base.DEC),
    kafka_ext_topic = ProtoField.string("zilla.kafka_ext.topic", "Topic", base.NONE),
    kafka_ext_partition_ids_array_length = ProtoField.int8("zilla.kafka_ext.partition_ids_array_length", "Length", base.DEC),
    kafka_ext_partition_ids_array_size = ProtoField.int8("zilla.kafka_ext.partition_ids_array_size", "Size", base.DEC),
    kafka_ext_partition_id = ProtoField.int32("zilla.kafka_ext.partition_id", "Partition ID", base.DEC),
    kafka_ext_consumer_assignments_array_length = ProtoField.int8("zilla.kafka_ext.consumer_assignments_array_length",
        "Length", base.DEC),
    kafka_ext_consumer_assignments_array_size = ProtoField.int8("zilla.kafka_ext.consumer_assignments_array_size",
        "Size", base.DEC),
    kafka_ext_partition_offset = ProtoField.int64("zilla.kafka_ext.partition_offset", "Partition Offset", base.DEC),
    kafka_ext_stable_offset = ProtoField.int64("zilla.kafka_ext.stable_offset", "Stable Offset", base.DEC),
    kafka_ext_latest_offset = ProtoField.int64("zilla.kafka_ext.latest_offset", "Latest Offset", base.DEC),
    kafka_ext_metadata_length = ProtoField.int32("zilla.kafka_ext.metadata_length", "Length", base.DEC),
    kafka_ext_metadata = ProtoField.string("zilla.kafka_ext.metadata", "Metadata", base.NONE),
    kafka_ext_leader_epoch = ProtoField.int32("zilla.kafka_ext.leader_epoch", "Leader Epoch", base.DEC),
    kafka_ext_correlation_id = ProtoField.int64("zilla.kafka_ext.correlation_id", "Correlation ID", base.DEC),
    --     group
    kafka_ext_protocol_length = ProtoField.int16("zilla.kafka_ext.protocol_length", "Length", base.DEC),
    kafka_ext_protocol = ProtoField.string("zilla.kafka_ext.protocol", "Protocol", base.NONE),
    kafka_ext_instance_id_length = ProtoField.int16("zilla.kafka_ext.instance_id_length", "Length", base.DEC),
    kafka_ext_instance_id = ProtoField.string("zilla.kafka_ext.instance_id", "Instance ID", base.NONE),
    kafka_ext_metadata_length_varint = ProtoField.bytes("zilla.kafka_ext.metadata_length_varint", "Length (varint32)", base.NONE),
    kafka_ext_metadata_bytes = ProtoField.bytes("zilla.kafka_ext.metadata_bytes", "Metadata", base.NONE),
    kafka_ext_generation_id = ProtoField.int32("zilla.kafka_ext.generation_id", "Generation ID", base.DEC),
    kafka_ext_leader_id_length = ProtoField.int16("zilla.kafka_ext.leader_id_length", "Length", base.DEC),
    kafka_ext_leader_id = ProtoField.string("zilla.kafka_ext.leader_id", "Leader ID", base.NONE),
    kafka_ext_member_id_length = ProtoField.int16("zilla.kafka_ext.member_id_length", "Length", base.DEC),
    kafka_ext_member_id = ProtoField.string("zilla.kafka_ext.member_id", "Member ID", base.NONE),
    -- merged
    kafka_ext_capabilities = ProtoField.uint8("zilla.kafka_ext.capabilities", "Capabilities", base.DEC,
        kafka_ext_capabilities_types),
    kafka_ext_partitions_array_length = ProtoField.int8("zilla.kafka_ext.partitions_array_length", "Length", base.DEC),
    kafka_ext_partitions_array_size = ProtoField.int8("zilla.kafka_ext.partitions_array_size", "Size", base.DEC),
    kafka_ext_filters_array_length = ProtoField.int8("zilla.kafka_ext.filters_array_length", "Length", base.DEC),
    kafka_ext_filters_array_size = ProtoField.int8("zilla.kafka_ext.filters_array_size", "Size", base.DEC),
    kafka_ext_conditions_array_length = ProtoField.int8("zilla.kafka_ext.conditions_array_length", "Length", base.DEC),
    kafka_ext_conditions_array_size = ProtoField.int8("zilla.kafka_ext.conditions_array_size", "Size", base.DEC),
    kafka_ext_condition_type = ProtoField.int8("zilla.kafka_ext.condition_type", "Type", base.DEC, kafka_ext_condition_types),
    kafka_ext_key_length_varint = ProtoField.bytes("zilla.kafka_ext.key_length_varint", "Length (varint32)", base.NONE),
    kafka_ext_key_length = ProtoField.int32("zilla.kafka_ext.key_length", "Length", base.DEC),
    kafka_ext_key = ProtoField.string("zilla.kafka_ext.key", "Key", base.NONE),
    kafka_ext_name_length_varint = ProtoField.bytes("zilla.kafka_ext.name_length_varint", "Length (varint32)", base.NONE),
    kafka_ext_name_length = ProtoField.int32("zilla.kafka_ext.name_length", "Length", base.DEC),
    kafka_ext_name = ProtoField.string("zilla.kafka_ext.name", "Name", base.NONE),
    kafka_ext_value_length_varint = ProtoField.bytes("zilla.kafka_ext.value_length_varint", "Length (varint32)", base.NONE),
    kafka_ext_value_length = ProtoField.int32("zilla.kafka_ext.value_length", "Length", base.DEC),
    kafka_ext_value = ProtoField.string("zilla.kafka_ext.value", "Value", base.NONE),
    kafka_ext_value_match_array_length = ProtoField.int8("zilla.kafka_ext.value_match_array_length", "Length", base.DEC),
    kafka_ext_value_match_array_size = ProtoField.int8("zilla.kafka_ext.value_match_array_size", "Size", base.DEC),
    kafka_ext_value_match_type = ProtoField.uint8("zilla.kafka_ext.value_match_type", "Type", base.DEC,
        kafka_ext_value_match_types),
    kafka_ext_skip_type = ProtoField.uint8("zilla.kafka_ext.skip_type", "Skip Type", base.DEC, kafka_ext_skip_types),
    kafka_ext_evaluation = ProtoField.uint8("zilla.kafka_ext.evaluation", "Evaluation", base.DEC, kafka_ext_evaluation_types),
    kafka_ext_isolation = ProtoField.uint8("zilla.kafka_ext.isolation", "Isolation", base.DEC, kafka_ext_isolation_types),
    kafka_ext_delta_type = ProtoField.uint8("zilla.kafka_ext.delta_type", "Delta Type", base.DEC, kafka_ext_delta_types),
    kafka_ext_ack_mode_id = ProtoField.int16("zilla.kafka_ext.ack_mode_id", "Ack Mode ID", base.DEC),
    kafka_ext_ack_mode = ProtoField.string("zilla.kafka_ext.ack_mode", "Ack Mode", base.NONE),
    kafka_ext_merged_api = ProtoField.uint8("zilla.kafka_ext.data_api", "Merged API", base.DEC, kafka_ext_apis),
    kafka_ext_deferred = ProtoField.int32("zilla.kafka_ext.deferred", "Deferred", base.DEC),
    kafka_ext_filters = ProtoField.int64("zilla.kafka_ext.filters", "Filters", base.DEC),
    kafka_ext_progress_array_length = ProtoField.int8("zilla.kafka_ext.progress_array_length", "Length", base.DEC),
    kafka_ext_progress_array_size = ProtoField.int8("zilla.kafka_ext.progress_array_size", "Size", base.DEC),
    kafka_ext_ancestor_offset = ProtoField.int64("zilla.kafka_ext.ancestor_offset", "Ancestor Offset", base.DEC),
    kafka_ext_headers_array_length = ProtoField.int8("zilla.kafka_ext.headers_array_length", "Length", base.DEC),
    kafka_ext_headers_array_size = ProtoField.int8("zilla.kafka_ext.headers_array_size", "Size", base.DEC),
    kafka_ext_producer_id = ProtoField.uint64("zilla.kafka_ext.producer_id", "Producer ID", base.HEX),
    kafka_ext_producer_epoch = ProtoField.uint16("zilla.kafka_ext.producer_epoch", "Producer Epoch", base.HEX),
    -- meta
    kafka_ext_partition_leader_id = ProtoField.int32("zilla.kafka_ext.partition_leader_id", "Leader ID", base.DEC),
    -- offset_fetch
    kafka_ext_topic_partition_array_length = ProtoField.int8("zilla.kafka_ext.topic_partition_array_length", "Length", base.DEC),
    kafka_ext_topic_partition_array_size = ProtoField.int8("zilla.kafka_ext.topic_partition_array_size", "Size", base.DEC),
    kafka_ext_topic_partition_offset_array_length = ProtoField.int8("zilla.kafka_ext.topic_partition_offset_array_length",
        "Length", base.DEC),
    kafka_ext_topic_partition_offset_array_size = ProtoField.int8("zilla.kafka_ext.topic_partition_offset_array_size",
        "Size", base.DEC),
    -- describe
    kafka_ext_config_array_length = ProtoField.int8("zilla.kafka_ext.config_array_length", "Length", base.DEC),
    kafka_ext_config_array_size = ProtoField.int8("zilla.kafka_ext.config_array_size", "Size", base.DEC),
    kafka_ext_config_length = ProtoField.int16("zilla.kafka_ext.config_length", "Length", base.DEC),
    kafka_ext_config = ProtoField.string("zilla.kafka_ext.config", "Config", base.NONE),
    -- fetch
    kafka_ext_header_size_max = ProtoField.int32("zilla.kafka_ext.header_size_max", "Header Size Maximum", base.DEC),
    kafka_ext_transactions_array_length = ProtoField.int8("zilla.kafka_ext.transactions_array_length", "Length", base.DEC),
    kafka_ext_transactions_array_size = ProtoField.int8("zilla.kafka_ext.transactions_array_size", "Size", base.DEC),
    kafka_ext_transaction_result = ProtoField.int8("zilla.kafka_ext.transaction_result", "Result", base.DEC,
        kafka_ext_transaction_result_types),
    -- produce
    kafka_ext_transaction_length = ProtoField.int16("zilla.kafka_ext.transaction_length", "Length", base.DEC),
    kafka_ext_transaction = ProtoField.string("zilla.kafka_ext.transaction", "Transaction", base.NONE),
    kafka_ext_sequence = ProtoField.int32("zilla.kafka_ext.sequence", "Sequence", base.DEC),
    kafka_ext_crc32c = ProtoField.uint32("zilla.kafka_ext.crc32c", "CRC32C", base.HEX),

    -- amqp extension
    --     begin
    amqp_ext_address_length = ProtoField.int8("zilla.amqp_ext.address_length", "Length", base.DEC),
    amqp_ext_address = ProtoField.string("zilla.amqp_ext.address", "Name", base.NONE),
    amqp_ext_capabilities = ProtoField.uint8("zilla.amqp_ext.capabilities", "Capabilities", base.DEC,
        amqp_ext_capabilities_types),
    amqp_ext_sender_settle_mode = ProtoField.uint8("zilla.amqp_ext.sender_settle_mode", "Sender Settle Mode", base.DEC,
        amqp_ext_sender_settle_modes),
    amqp_ext_receiver_settle_mode = ProtoField.uint8("zilla.amqp_ext.receiver_settle_mode", "Receiver Settle Mode", base.DEC,
        amqp_ext_receiver_settle_modes),
    --     data
    amqp_ext_delivery_tag_length = ProtoField.int16("zilla.amqp_ext.delivery_tag_length", "Length", base.DEC),
    amqp_ext_delivery_tag = ProtoField.string("zilla.amqp_ext.delivery_tag", "Delivery Tag", base.NONE),
    amqp_ext_message_format = ProtoField.uint32("zilla.amqp_ext.message_format", "Message Format", base.DEC),
    amqp_ext_body_kind = ProtoField.uint8("zilla.amqp_ext.body_kind", "Body Kind", base.DEC, amqp_ext_body_kinds),
    amqp_ext_deferred = ProtoField.int32("zilla.amqp_ext.deferred", "Deferred", base.DEC),
    --     flags
    amqp_ext_transfer_flags = ProtoField.uint8("zilla.amqp_ext.transfer_flags", "Flags", base.HEX),
    amqp_ext_transfer_flags_settled = ProtoField.uint8("zilla.amqp_ext.transfer_flags_settled", "SETTLED",
        base.DEC, flags_types, 0x01),
    amqp_ext_transfer_flags_resume = ProtoField.uint8("zilla.amqp_ext.transfer_flags_resume", "RESUME",
        base.DEC, flags_types, 0x02),
    amqp_ext_transfer_flags_aborted = ProtoField.uint8("zilla.amqp_ext.transfer_flags_aborted", "ABORTED",
        base.DEC, flags_types, 0x04),
    amqp_ext_transfer_flags_batchable = ProtoField.uint8("zilla.amqp_ext.transfer_flags_batchable", "BATCHABLE",
        base.DEC, flags_types, 0x08),
    --     annotations
    amqp_ext_annotations_length = ProtoField.int16("zilla.amqp_ext.annotations_length", "Length", base.DEC),
    amqp_ext_annotations_size = ProtoField.int16("zilla.amqp_ext.annotations_size", "Size", base.DEC),
    amqp_ext_annotation_key_type = ProtoField.uint8("zilla.amqp_ext.annotation_key_type", "Key Type", base.DEC,
        amqp_ext_annotation_key_types),
    amqp_ext_annotation_key_id = ProtoField.uint64("zilla.amqp_ext.annotation_key_id", "Key [ID]", base.HEX),
    amqp_ext_annotation_key_name_length = ProtoField.uint8("zilla.amqp_ext.annotation_key_name_length", "Length", base.DEC),
    amqp_ext_annotation_key_name = ProtoField.string("zilla.amqp_ext.annotation_key_name", "Key Name", base.NONE),
    amqp_ext_annotation_value_length = ProtoField.uint8("zilla.amqp_ext.annotation_value_length", "Length", base.DEC),
    amqp_ext_annotation_value = ProtoField.string("zilla.amqp_ext.annotation_value", "Value", base.NONE),
    --     properties
    amqp_ext_properties_length = ProtoField.int16("zilla.amqp_ext.properties_length", "Length", base.DEC),
    amqp_ext_properties_size = ProtoField.int16("zilla.amqp_ext.properties_size", "Size", base.DEC),
    amqp_ext_properties_fields = ProtoField.uint64("zilla.amqp_ext.properties_fields", "Fields", base.HEX),
    amqp_ext_property_message_id_type = ProtoField.int16("zilla.amqp_ext.message_id_type", "ID Type", base.DEC,
        amqp_ext_message_id_types),
    amqp_ext_property_message_id_ulong = ProtoField.uint64("zilla.amqp_ext.message_id_ulong", "Message ID", base.HEX),
    amqp_ext_property_message_id_uuid_length = ProtoField.int8("zilla.amqp_ext.property_message_id_uuid_length",
        "Length", base.DEC),
    amqp_ext_property_message_id_uuid = ProtoField.string("zilla.amqp_ext.property_message_id_uuid", "Message ID", base.NONE),
    amqp_ext_property_message_id_binary_length = ProtoField.int8("zilla.amqp_ext.property_message_id_binary_length",
        "Length", base.DEC),
    amqp_ext_property_message_id_binary = ProtoField.string("zilla.amqp_ext.property_message_id_binary", "Message ID", base.NONE),
    amqp_ext_property_message_id_stringtype_length = ProtoField.int8("zilla.amqp_ext.property_message_id_stringtype_length",
        "Length", base.DEC),
    amqp_ext_property_message_id_stringtype = ProtoField.string("zilla.amqp_ext.property_message_id_stringtype",
        "Message ID", base.NONE),
    amqp_ext_property_user_id_length = ProtoField.int16("zilla.amqp_ext.property_user_id_length", "Length", base.DEC),
    amqp_ext_property_user_id = ProtoField.string("zilla.amqp_ext.property_user_id", "User ID", base.NONE),
    amqp_ext_property_to_length = ProtoField.int8("zilla.amqp_ext.property_to_length", "Length", base.DEC),
    amqp_ext_property_to = ProtoField.string("zilla.amqp_ext.property_to", "To", base.NONE),
    amqp_ext_property_subject_length = ProtoField.int8("zilla.amqp_ext.property_subject_length", "Length", base.DEC),
    amqp_ext_property_subject = ProtoField.string("zilla.amqp_ext.property_subject", "Subject", base.NONE),
    amqp_ext_property_reply_to_length = ProtoField.int8("zilla.amqp_ext.property_reply_to_length", "Length", base.DEC),
    amqp_ext_property_reply_to = ProtoField.string("zilla.amqp_ext.property_reply_to", "Reply To", base.NONE),
    amqp_ext_property_correlation_id_type = ProtoField.int16("zilla.amqp_ext.correlation_id_type", "ID Type", base.DEC,
        amqp_ext_message_id_types),
    amqp_ext_property_correlation_id_ulong = ProtoField.uint64("zilla.amqp_ext.correlation_id_ulong", "Correlation ID", base.HEX),
    amqp_ext_property_correlation_id_uuid_length = ProtoField.int8("zilla.amqp_ext.property_correlation_id_uuid_length",
        "Length", base.DEC),
    amqp_ext_property_correlation_id_uuid = ProtoField.string("zilla.amqp_ext.property_correlation_id_uuid", "Correlation ID", base.NONE),
    amqp_ext_property_correlation_id_binary_length = ProtoField.int8("zilla.amqp_ext.property_correlation_id_binary_length",
        "Length", base.DEC),
    amqp_ext_property_correlation_id_binary = ProtoField.string("zilla.amqp_ext.property_correlation_id_binary", "Correlation ID", base.NONE),
    amqp_ext_property_correlation_id_stringtype_length = ProtoField.int8("zilla.amqp_ext.property_correlation_id_stringtype_length",
        "Length", base.DEC),
    amqp_ext_property_correlation_id_stringtype = ProtoField.string("zilla.amqp_ext.property_correlation_id_stringtype",
        "Correlation ID", base.NONE),
    amqp_ext_property_content_type_length = ProtoField.int8("zilla.amqp_ext.property_content_type_length", "Length", base.DEC),
    amqp_ext_property_content_type = ProtoField.string("zilla.amqp_ext.property_content_type", "Content Type", base.NONE),
    amqp_ext_property_content_encoding_length = ProtoField.int8("zilla.amqp_ext.property_content_encoding_length", "Length",
        base.DEC),
    amqp_ext_property_content_encoding = ProtoField.string("zilla.amqp_ext.property_content_encoding", "Content Encoding",
        base.NONE),
    amqp_ext_property_absolute_expiry_time = ProtoField.int64("zilla.amqp_ext.property_absolut_expiry_time",
        "Property: Absolute Expiry Time", base.DEC),
    amqp_ext_property_creation_time = ProtoField.int64("zilla.amqp_ext.property_creation_time", "Property: Creation Time",
        base.DEC),
    amqp_ext_property_group_id_length = ProtoField.int8("zilla.amqp_ext.property_group_id_length", "Length", base.DEC),
    amqp_ext_property_group_id = ProtoField.string("zilla.amqp_ext.property_group_id", "Group ID", base.NONE),
    amqp_ext_property_group_sequence = ProtoField.int32("zilla.amqp_ext.property_group_sequence", "Property: Group Sequence", base.DEC),
    amqp_ext_property_reply_to_group_id_length = ProtoField.int8("zilla.amqp_ext.property_reply_to_group_id_length", "Length",
        base.DEC),
    amqp_ext_property_reply_to_group_id = ProtoField.string("zilla.amqp_ext.property_reply_to_group_id", "Reply To Group ID",
        base.NONE),
    --     application_properties
    amqp_ext_application_properties_length = ProtoField.int16("zilla.amqp_ext.application_properties_length", "Length",
        base.DEC),
    amqp_ext_application_properties_size = ProtoField.int16("zilla.amqp_ext.application_properties_size", "Size", base.DEC),
    amqp_ext_application_property_key_length = ProtoField.uint32("zilla.amqp_ext.application_property_key_length", "Length",
        base.DEC),
    amqp_ext_application_property_key = ProtoField.string("zilla.amqp_ext.application_property_key", "Key", base.NONE),
    amqp_ext_application_property_value_length = ProtoField.uint8("zilla.amqp_ext.application_property_value_length", "Length",
        base.DEC),
    amqp_ext_application_property_value = ProtoField.string("zilla.amqp_ext.application_property_value", "Value", base.NONE),
    --     abort
    amqp_ext_condition_length = ProtoField.uint8("zilla.amqp_ext.condition_length", "Length", base.DEC),
    amqp_ext_condition = ProtoField.string("zilla.amqp_ext.condition", "Condition", base.NONE),
}

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
    elseif frame_type_id == WINDOW_ID then
        handle_window_frame(buffer, next_offset, subtree, sequence, acknowledge, maximum, pinfo, info)
    elseif frame_type_id == SIGNAL_ID then
        handle_signal_frame(buffer, next_offset, subtree, pinfo, info)
    elseif frame_type_id == END_ID or frame_type_id == ABORT_ID or frame_type_id == RESET_ID or frame_type_id == CHALLENGE_ID then
        handle_extension(buffer, subtree, pinfo, info, next_offset, frame_type_id)
    end
end

function handle_begin_frame(buffer, offset, subtree, pinfo, info)
    local slice_affinity = buffer(offset, 8)
    subtree:add_le(fields.affinity, slice_affinity)
    handle_extension(buffer, subtree, pinfo, info, offset + 8, BEGIN_ID)
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
    end
    return frame_type
end

function resolve_type(type_id)
    local type = ""
        if type_id == AMQP_ID        then type = "amqp"
    elseif type_id == FILESYSTEM_ID  then type = "filesystem"
    elseif type_id == GRPC_ID        then type = "grpc"
    elseif type_id == HTTP_ID        then type = "http"
    elseif type_id == KAFKA_ID       then type = "kafka"
    elseif type_id == MQTT_ID        then type = "mqtt"
    elseif type_id == PROXY_ID       then type = "proxy"
    elseif type_id == SSE_ID         then type = "sse"
    elseif type_id == TLS_ID         then type = "tls"
    elseif type_id == WS_ID          then type = "ws"
    end
    return type
end

function resolve_dissector(protocol_type, payload)
    local dissector
        if protocol_type == "amqp"  then dissector = Dissector.get("amqp")
    elseif protocol_type == "http"  then dissector = resolve_http_dissector(payload)
    elseif protocol_type == "kafka" then dissector = Dissector.get("kafka")
    elseif protocol_type == "mqtt"  then dissector = Dissector.get("mqtt")
    elseif protocol_type == "tls"   then dissector = Dissector.get("tls")
    end
    return dissector
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

        if stream_type_id == PROXY_ID then
            handle_proxy_extension(buffer, offset + 4, ext_subtree)
        elseif stream_type_id == FILESYSTEM_ID then
            handle_filesystem_extension(buffer, offset + 4, ext_subtree)
        elseif stream_type_id == HTTP_ID then
            handle_http_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == GRPC_ID then
            handle_grpc_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == SSE_ID then
            handle_sse_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == WS_ID then
            handle_ws_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == MQTT_ID then
            handle_mqtt_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == KAFKA_ID then
            handle_kafka_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        elseif stream_type_id == AMQP_ID then
            handle_amqp_extension(buffer, offset + 4, ext_subtree, frame_type_id)
        end

        if stream_type and stream_type ~= "" then
            pinfo.cols.info:set(string.format("%s s=%s", info, stream_type))
        end
    end
end

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

function dissect_length_value(buffer, offset, length_length)
    local slice_length = buffer(offset, length_length)
    local length = math.max(slice_length:le_int(), 0)
    local slice_value = buffer(offset + length_length, length)
    local item_length = length + length_length
    return item_length, slice_length, slice_value
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

function handle_http_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == RESET_ID or frame_type_id == CHALLENGE_ID then
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

function handle_grpc_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID then
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

function handle_sse_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID then
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

function handle_ws_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID then
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

function handle_filesystem_extension(buffer, offset, ext_subtree)
    -- BEGIN frame
    -- capabilities
    local capabilities_offset = offset
    local capabilities_length = 4
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    local capabilities_label = string.format("Capabilities: 0x%08x", slice_capabilities:le_uint())
    local capabilities_subtree = ext_subtree:add(zilla_protocol, slice_capabilities, capabilities_label)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_payload, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_extension, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_changes, slice_capabilities)
    -- path
    local path_offset = capabilities_offset + capabilities_length
    local path_length, slice_path_length, slice_path_text = dissect_length_value(buffer, path_offset, 2)
    add_string_as_subtree(buffer(path_offset, path_length), ext_subtree, "Path: %s",
        slice_path_length, slice_path_text, fields.filesystem_ext_path_length, fields.filesystem_ext_path)
    -- type
    local type_offset = path_offset + path_length
    local type_length, slice_type_length, slice_type_text = dissect_length_value(buffer, type_offset, 2)
    add_string_as_subtree(buffer(type_offset, type_length), ext_subtree, "Type: %s", slice_type_length,
        slice_type_text, fields.filesystem_ext_type_length, fields.filesystem_ext_type)
    -- payload_size
    local payload_size_offset = type_offset + type_length
    local payload_size_length = 8
    local slice_payload_size = buffer(payload_size_offset, payload_size_length)
    ext_subtree:add_le(fields.filesystem_ext_payload_size, slice_payload_size)
    -- tag
    local tag_offset = payload_size_offset + payload_size_length
    local tag_length, slice_tag_length, slice_tag_text = dissect_length_value(buffer, tag_offset, 2)
    add_string_as_subtree(buffer(tag_offset, tag_length), ext_subtree, "Tag: %s", slice_tag_length,
        slice_tag_text, fields.filesystem_ext_tag_length, fields.filesystem_ext_tag)
    -- timeout
    local timeout_offset = tag_offset + tag_length
    local timeout_length = 8
    local slice_timeout = buffer(timeout_offset, timeout_length)
    ext_subtree:add_le(fields.filesystem_ext_timeout, slice_timeout)
end

function handle_mqtt_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == DATA_ID or frame_type_id == FLUSH_ID then
        local kind_length = 1
        local slice_kind = buffer(offset, kind_length)
        local kind = mqtt_ext_kinds[slice_kind:le_int()]
        ext_subtree:add_le(fields.mqtt_ext_kind, slice_kind)
        if frame_type_id == BEGIN_ID then
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
    -- qos_max
    local qos_max_offset = expiry_offset + expiry_length
    local qos_max_length = 2
    local slice_qos_max = buffer(qos_max_offset, qos_max_length)
    ext_subtree:add_le(fields.mqtt_ext_qos_max, slice_qos_max)
    -- packet_size_max
    local packet_size_max_offset = qos_max_offset + qos_max_length
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

function handle_kafka_extension(buffer, offset, ext_subtree, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == DATA_ID or frame_type_id == FLUSH_ID then
        local api_length = 1
        local slice_api = buffer(offset, api_length)
        local api = kafka_ext_apis[slice_api:le_uint()]
        ext_subtree:add_le(fields.kafka_ext_api, slice_api)
        if frame_type_id == BEGIN_ID then
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

function field_exists(list_fields, position)
    return bit.band(tostring(list_fields), bit.lshift(1, position)) > 0
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

local data_dissector = DissectorTable.get("tcp.port")
data_dissector:add(7114, zilla_protocol)
