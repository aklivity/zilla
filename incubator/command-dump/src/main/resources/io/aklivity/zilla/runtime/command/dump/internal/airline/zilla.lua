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
GRPC_ID = 0xf9c7583a
HTTP_ID = 0x8ab62046
KAFKA_ID = 0x084b20e1
MQTT_ID = 0xd0d41a76
PROXY_ID = 0x8dcea850
TLS_ID = 0x99f321bc

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

local fields = {
    -- header
    frame_type_id = ProtoField.uint32("zilla.frame_type_id", "Frame Type ID", base.HEX),
    frame_type = ProtoField.string("zilla.frame_type", "Frame Type", base.NONE),
    protocol_type_id = ProtoField.uint32("zilla.protocol_type_id", "Protocol Type ID", base.HEX),
    protocol_type = ProtoField.string("zilla.protocol_type", "Protocol Type", base.NONE),
    stream_type_id = ProtoField.uint32("zilla.stream_type_id", "Stream Type ID", base.HEX),
    stream_type = ProtoField.string("zilla.stream_type", "Stream Type", base.NONE),
    worker = ProtoField.uint32("zilla.worker", "Worker", base.DEC),
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
    signal_id = ProtoField.int32("zilla.signal_id", "Signal ID", base.DEC),
    context_id = ProtoField.int32("zilla.context_id", "Context ID", base.DEC),

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
    proxy_ext_info_array_length = ProtoField.uint8("zilla.proxy_ext.info_array_length", "Length", base.DEC),
    proxy_ext_info_array_size = ProtoField.uint8("zilla.proxy_ext.info_array_size", "Size", base.DEC),
    proxy_ext_info_type = ProtoField.uint8("zilla.proxy_ext.info_type", "Type", base.HEX, proxy_ext_info_types),
    proxy_ext_info_length = ProtoField.uint16("zilla.proxy_ext.info_length", "Length", base.DEC),
    proxy_ext_info_alpn = ProtoField.string("zilla.proxy_ext.info_alpn", "Value", base.NONE),
    proxy_ext_info_authority = ProtoField.string("zilla.proxy_ext.info_authority", "Value", base.NONE),
    proxy_ext_info_identity = ProtoField.bytes("zilla.proxy_ext.info_identity", "Value", base.NONE),
    proxy_ext_info_namespace = ProtoField.string("zilla.proxy_ext.info_namespace", "Value", base.NONE),
    proxy_ext_info_secure = ProtoField.string("zilla.proxy_ext.info_secure", "Value", base.NONE),
    proxy_ext_info_secure_type = ProtoField.uint8("zilla.proxy_ext.info_secure_type", "Secure Type", base.HEX,
        proxy_ext_secure_info_types),

    -- http extension
    --     headers
    http_ext_headers_array_length = ProtoField.uint8("zilla.http_ext.headers_array_length", "Length", base.DEC),
    http_ext_headers_array_size = ProtoField.uint8("zilla.http_ext.headers_array_size", "Size", base.DEC),
    http_ext_header_name_length = ProtoField.uint8("zilla.http_ext.header_name_length", "Length", base.DEC),
    http_ext_header_name = ProtoField.string("zilla.http_ext.header_name", "Name", base.NONE),
    http_ext_header_value_length = ProtoField.uint16("zilla.http_ext.header_value_length", "Length", base.DEC),
    http_ext_header_value = ProtoField.string("zilla.http_ext.header_value", "Value", base.NONE),
    --    promise id
    http_ext_promise_id = ProtoField.uint64("zilla.promise_id", "Promise ID", base.HEX),

    -- grpc extension
    grpc_ext_scheme_length = ProtoField.uint16("zilla.grpc_ext.scheme_length", "Length", base.DEC),
    grpc_ext_scheme = ProtoField.string("zilla.grpc_ext.scheme", "Scheme", base.NONE),
    grpc_ext_authority_length = ProtoField.uint16("zilla.grpc_ext.authority_length", "Length", base.DEC),
    grpc_ext_authority = ProtoField.string("zilla.grpc_ext.authority", "Authority", base.NONE),
    grpc_ext_service_length = ProtoField.uint16("zilla.grpc_ext.service_length", "Length", base.DEC),
    grpc_ext_service = ProtoField.string("zilla.grpc_ext.service", "Service", base.NONE),
    grpc_ext_method_length = ProtoField.uint16("zilla.grpc_ext.method_length", "Length", base.DEC),
    grpc_ext_method = ProtoField.string("zilla.grpc_ext.method", "Method", base.NONE),
    grpc_ext_deferred = ProtoField.uint32("zilla.grpc_ext.deferred", "Deferred", base.DEC),
    grpc_ext_status_length = ProtoField.uint16("zilla.grpc_ext.status_length", "Length", base.DEC),
    grpc_ext_status = ProtoField.string("zilla.grpc_ext.status", "Status", base.NONE),
    --    metadata
    grpc_ext_metadata_array_length = ProtoField.uint8("zilla.grpc_ext.metadata_array_length", "Length", base.DEC),
    grpc_ext_metadata_array_size = ProtoField.uint8("zilla.grpc_ext.metadata_array_size", "Size", base.DEC),
    grpc_ext_metadata_type = ProtoField.uint32("zilla.grpc_ext.metadata_type", "Type", base.DEC, grpc_types),
    grpc_ext_metadata_name_length_varint = ProtoField.bytes("zilla.grpc_ext.metadata_name_varint", "Length (Varint)", base.NONE),
    grpc_ext_metadata_name_length = ProtoField.uint32("zilla.grpc_ext.metadata_name_length", "Length", base.DEC),
    grpc_ext_metadata_name = ProtoField.string("zilla.grpc_ext.metadata_name", "Name", base.NONE),
    grpc_ext_metadata_value_length_varint = ProtoField.bytes("zilla.grpc_ext.metadata_value_length_varint", "Length (Varint)", base.NONE),
    grpc_ext_metadata_value_length = ProtoField.uint32("zilla.grpc_ext.metadata_value_length", "Length", base.DEC),
    grpc_ext_metadata_value = ProtoField.string("zilla.grpc_ext.metadata_value", "Value", base.NONE),
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

    -- begin
    if frame_type_id == BEGIN_ID then
        local slice_affinity = buffer(frame_offset + 72, 8)
        subtree:add_le(fields.affinity, slice_affinity)
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 80, frame_type_id)
    end

    -- data
    if frame_type_id == DATA_ID then
        local slice_flags = buffer(frame_offset + 72, 1)
        local flags_label = string.format("Flags: 0x%02x", slice_flags:le_uint())
        local flags_subtree = subtree:add(zilla_protocol, slice_flags, flags_label)
        flags_subtree:add_le(fields.flags_fin, slice_flags)
        flags_subtree:add_le(fields.flags_init, slice_flags)
        flags_subtree:add_le(fields.flags_incomplete, slice_flags)
        flags_subtree:add_le(fields.flags_skip, slice_flags)

        local slice_budget_id = buffer(frame_offset + 73, 8)
        local slice_reserved = buffer(frame_offset + 81, 4)
        local reserved = slice_reserved:le_int();
        local progress = sequence - acknowledge + reserved;
        local progress_maximum = string.format("%s/%s", progress, maximum)
        subtree:add_le(fields.budget_id, slice_budget_id)
        subtree:add_le(fields.reserved, slice_reserved)
        subtree:add(fields.progress, progress)
        subtree:add(fields.progress_maximum, progress_maximum)
        pinfo.cols.info:set(string.format("%s [%s]", info, progress_maximum))

        local slice_payload_length = buffer(frame_offset + 85, 4)
        local payload_length = math.max(slice_payload_length:le_int(), 0)
        local slice_payload = buffer(frame_offset + 89, payload_length)
        local payload_subtree = subtree:add(zilla_protocol, slice_payload, "Payload")
        payload_subtree:add_le(fields.payload_length, slice_payload_length)
        if (payload_length > 0) then
            payload_subtree:add(fields.payload, slice_payload)
        end

        handle_extension(buffer, subtree, pinfo, info, frame_offset + 89 + payload_length, frame_type_id)

        local dissector = resolve_dissector(protocol_type, slice_payload:tvb())
        if dissector then
            dissector:call(slice_payload:tvb(), pinfo, tree)
        end
    end

    -- end
    if frame_type_id == END_ID then
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 72, frame_type_id)
    end

    -- abort
    if frame_type_id == ABORT_ID then
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 72, frame_type_id)
    end

    -- flush
    if frame_type_id == FLUSH_ID then
        local slice_budget_id = buffer(frame_offset + 72, 8)
        local slice_reserved = buffer(frame_offset + 80, 4)
        subtree:add_le(fields.budget_id, slice_budget_id)
        subtree:add_le(fields.reserved, slice_reserved)
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 84, frame_type_id)
    end

    -- reset
    if frame_type_id == RESET_ID then
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 72, frame_type_id)
    end

    -- window
    if frame_type_id == WINDOW_ID then
        local slice_budget_id = buffer(frame_offset + 72, 8)
        local slice_padding = buffer(frame_offset + 80, 4)
        local slice_minimum = buffer(frame_offset + 84, 4)
        local slice_capabilities = buffer(frame_offset + 88, 1)
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

    -- signal
    if frame_type_id == SIGNAL_ID then
        local slice_cancel_id = buffer(frame_offset + 72, 8)
        local slice_signal_id = buffer(frame_offset + 80, 4)
        local slice_context_id = buffer(frame_offset + 84, 4)
        subtree:add_le(fields.cancel_id, slice_cancel_id)
        subtree:add_le(fields.signal_id, slice_signal_id)
        subtree:add_le(fields.context_id, slice_context_id)

        local slice_payload_length = buffer(frame_offset + 88, 4)
        local payload_length = math.max(slice_payload_length:le_int(), 0)
        local slice_payload = buffer(frame_offset + 92, payload_length)
        local payload_subtree = subtree:add(zilla_protocol, slice_payload, "Payload")
        payload_subtree:add_le(fields.payload_length, slice_payload_length)
        if (payload_length > 0) then
            payload_subtree:add(fields.payload, slice_payload)
        end
    end

    -- challenge
    if frame_type_id == CHALLENGE_ID then
        handle_extension(buffer, subtree, pinfo, info, frame_offset + 72, frame_type_id)
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
        if type_id == AMQP_ID  then type = "amqp"
    elseif type_id == GRPC_ID  then type = "grpc"
    elseif type_id == HTTP_ID  then type = "http"
    elseif type_id == KAFKA_ID then type = "kafka"
    elseif type_id == MQTT_ID  then type = "mqtt"
    elseif type_id == PROXY_ID then type = "proxy"
    elseif type_id == TLS_ID   then type = "tls"
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
        local extension_subtree = subtree:add(zilla_protocol, slice_extension, extension_label)
        extension_subtree:add(fields.stream_type_id, slice_stream_type_id)
        extension_subtree:add(fields.stream_type, stream_type)

        if stream_type_id == PROXY_ID then
            handle_proxy_extension(buffer, extension_subtree, offset + 4)
        elseif stream_type_id == HTTP_ID then
            handle_http_extension(buffer, extension_subtree, offset + 4, frame_type_id)
        elseif stream_type_id == GRPC_ID then
            handle_grpc_extension(buffer, extension_subtree, offset + 4, frame_type_id)
        end

        if stream_type and stream_type ~= "" then
            pinfo.cols.info:set(string.format("%s s=%s", info, stream_type))
        end
    end
end

function handle_proxy_extension(buffer, extension_subtree, offset)
    -- address
    local slice_address_family = buffer(offset, 1)
    local address_family_id = slice_address_family:le_int()
    local address_family = proxy_ext_address_family_types[address_family_id]
    local address_subtree_label = string.format("Address: %s", address_family)
    local info_offset
    if address_family == "INET" then
        local slice_protocol = buffer(offset + 1, 1)
        local source_length = buffer(offset + 2, 2):le_int()
        local slice_source = buffer(offset + 4, source_length)
        local destination_length = buffer(offset + 4 + source_length, 2):le_int()
        local slice_destination = buffer(offset + 6 + source_length, destination_length)
        local slice_source_port = buffer(offset + 6 + source_length + destination_length, 2)
        local slice_destination_port = buffer(offset + 8 + source_length + destination_length, 2)
        local length = 10 + source_length + destination_length
        local address_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), address_subtree_label)
        address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
        address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
        address_subtree:add(fields.proxy_ext_address_inet_source, slice_source)
        address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
        address_subtree:add(fields.proxy_ext_address_inet_destination, slice_destination)
        address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
        info_offset = offset + length
    elseif address_family == "INET4" then
        local slice_protocol = buffer(offset + 1, 1)
        local slice_source = buffer(offset + 2, 4)
        local slice_destination = buffer(offset + 6, 4)
        local slice_source_port = buffer(offset + 10, 2)
        local slice_destination_port = buffer(offset + 12, 2)
        local length = 14;
        local address_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), address_subtree_label)
        address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
        address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
        address_subtree:add(fields.proxy_ext_address_inet4_source, slice_source)
        address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
        address_subtree:add(fields.proxy_ext_address_inet4_destination, slice_destination)
        address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
        info_offset = offset + length
    elseif address_family == "INET6" then
        local slice_protocol = buffer(offset + 1, 1)
        local slice_source = buffer(offset + 2, 16)
        local slice_destination = buffer(offset + 18, 16)
        local slice_source_port = buffer(offset + 34, 2)
        local slice_destination_port = buffer(offset + 36, 2)
        local length = 38;
        local address_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), address_subtree_label)
        address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
        address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
        address_subtree:add(fields.proxy_ext_address_inet6_source, slice_source)
        address_subtree:add_le(fields.proxy_ext_address_inet_source_port, slice_source_port)
        address_subtree:add(fields.proxy_ext_address_inet6_destination, slice_destination)
        address_subtree:add_le(fields.proxy_ext_address_inet_destination_port, slice_destination_port)
        info_offset = offset + length;
    elseif address_family == "UNIX" then
        local slice_protocol = buffer(offset + 1, 1)
        local slice_source = buffer(offset + 2, 108)
        local slice_destination = buffer(offset + 110, 108)
        local length = 218
        local address_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), address_subtree_label)
        address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
        address_subtree:add(fields.proxy_ext_address_protocol, slice_protocol)
        address_subtree:add(fields.proxy_ext_address_unix_source, slice_source)
        address_subtree:add(fields.proxy_ext_address_unix_destination, slice_destination)
        info_offset = offset + length
    elseif address_family == "NONE" then
        local length = 1
        local address_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), address_subtree_label)
        address_subtree:add(fields.proxy_ext_address_family, slice_address_family)
        info_offset = offset + length
    end

    -- info
    local slice_info_array_length = buffer(info_offset, 4)
    local slice_info_array_size = buffer(info_offset + 4, 4)
    local info_array_length = slice_info_array_length:le_int()
    local info_array_size = slice_info_array_size:le_int()
    local length = 8
    local label = string.format("Info (%d items)", info_array_size)
    local info_array_subtree = extension_subtree:add(zilla_protocol, buffer(info_offset, length), label)
    info_array_subtree:add_le(fields.proxy_ext_info_array_length, slice_info_array_length)
    info_array_subtree:add_le(fields.proxy_ext_info_array_size, slice_info_array_size)
    local item_offset = info_offset + length
    for i = 1, info_array_size do
        local slice_type_id = buffer(item_offset, 1)
        local type_id = slice_type_id:le_int()
        local type = proxy_ext_info_types[type_id]
        local label_format = "Info: %s: %s"
        item_offset = item_offset + 1
        if type == "ALPN" then
            local item_length, slice_length, slice_text = dissect_length_value(buffer, item_offset, 1)
            add_proxy_string_as_subtree(buffer(item_offset - 1, item_length + 1), extension_subtree, label_format, slice_type_id,
                slice_length, slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_alpn)
            item_offset = item_offset + item_length
        elseif type == "AUTHORITY" then
            local item_length, slice_length, slice_text = dissect_length_value(buffer, item_offset, 2)
            add_proxy_string_as_subtree(buffer(item_offset - 1, item_length + 1), extension_subtree, label_format, slice_type_id,
                slice_length, slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_authority)
            item_offset = item_offset + item_length
        elseif type == "IDENTITY" then
            local item_length, slice_length, slice_bytes = dissect_length_value(buffer, item_offset, 2)
            local label = string.format("Info: %s: 0x%s", type, slice_bytes:bytes())
            local subtree = extension_subtree:add(zilla_protocol, buffer(item_offset - 1, item_length + 1), label)
            subtree:add(fields.proxy_ext_info_type, slice_type_id)
            subtree:add_le(fields.proxy_ext_info_length, slice_length)
            subtree:add(fields.proxy_ext_info_identity, slice_bytes)
            item_offset = item_offset + item_length
        elseif type == "SECURE" then
            local slice_secure_type_id = buffer(item_offset, 1)
            local secure_type_id = slice_secure_type_id:le_int();
            local secure_type = proxy_ext_secure_info_types[secure_type_id]
            item_offset = item_offset + 1
            local length_length
            if secure_type == "VERSION" or secure_type == "CIPHER" or secure_type == "SIGNATURE" or secure_type == "KEY" then
                length_length = 1
            elseif secure_type == "NAME" then
                length_length = 2
            end
            local item_length, slice_length, slice_text = dissect_length_value(buffer, item_offset, length_length)
            local label = string.format("Info: %s: %s: %s", type, secure_type, slice_text:string())
            local subtree = extension_subtree:add(zilla_protocol, buffer(item_offset - 1, item_length + 1), label)
            subtree:add(fields.proxy_ext_info_type, slice_type_id)
            subtree:add(fields.proxy_ext_info_secure_type, slice_secure_type_id)
            subtree:add_le(fields.proxy_ext_info_length, slice_length)
            subtree:add(fields.proxy_ext_info_secure, slice_text)
            item_offset = item_offset + item_length
        elseif type == "NAMESPACE" then
            local item_length, slice_length, slice_text = dissect_length_value(buffer, item_offset, 2)
            add_proxy_string_as_subtree(buffer(item_offset - 1, item_length + 1), extension_subtree, label_format, slice_type_id,
                slice_length, slice_text, fields.proxy_ext_info_type, fields.proxy_ext_info_length, fields.proxy_ext_info_namespace)
            item_offset = item_offset + item_length
        end
    end
end

function dissect_length_value(buffer, item_offset, length_length)
    local slice_length = buffer(item_offset, length_length)
    local length = slice_length:le_int()
    local slice_value = buffer(item_offset + length_length, length)
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

function handle_http_extension(buffer, extension_subtree, offset, frame_type_id)
    if frame_type_id == BEGIN_ID or frame_type_id == RESET_ID or frame_type_id == CHALLENGE_ID then
        dissect_and_add_http_headers(buffer, extension_subtree, offset, "Headers", "Header")
    elseif frame_type_id == END_ID then
        dissect_and_add_http_headers(buffer, extension_subtree, offset, "Trailers", "Trailer")
    elseif frame_type_id == FLUSH_ID then
        slice_promise_id = buffer(offset, 8)
        extension_subtree:add_le(fields.http_ext_promise_id, slice_promise_id)
        dissect_and_add_http_headers(buffer, extension_subtree, offset + 8, "Promises", "Promise")
    end
end

function dissect_and_add_http_headers(buffer, extension_subtree, offset, plural_name, singular_name)
    local slice_headers_array_length = buffer(offset, 4)
    local slice_headers_array_size = buffer(offset + 4, 4)
    local headers_array_length = slice_headers_array_length:le_int()
    local headers_array_size = slice_headers_array_size:le_int()
    local length = 8
    local label = string.format("%s (%d items)", plural_name, headers_array_size)
    local headers_array_subtree = extension_subtree:add(zilla_protocol, buffer(offset, length), label)
    headers_array_subtree:add_le(fields.http_ext_headers_array_length, slice_headers_array_length)
    headers_array_subtree:add_le(fields.http_ext_headers_array_size, slice_headers_array_size)
    local item_offset = offset + length
    for i = 1, headers_array_size do
        local name_length, slice_name_length, slice_name = dissect_length_value(buffer, item_offset, 1)
        local value_offset = item_offset + name_length
        local value_length, slice_value_length, slice_value = dissect_length_value(buffer, value_offset, 2)
        local label = string.format("%s: %s: %s", singular_name, slice_name:string(), slice_value:string())
        local subtree = extension_subtree:add(zilla_protocol, buffer(item_offset, name_length + value_length), label)
        subtree:add_le(fields.http_ext_header_name_length, slice_name_length)
        subtree:add(fields.http_ext_header_name, slice_name)
        subtree:add_le(fields.http_ext_header_value_length, slice_value_length)
        subtree:add(fields.http_ext_header_value, slice_value)
        item_offset = item_offset + name_length + value_length
    end
end

function handle_grpc_extension(buffer, extension_subtree, offset, frame_type_id)
    if frame_type_id == BEGIN_ID then
        -- scheme
        local scheme_offset = offset
        local scheme_length, slice_scheme_length, slice_scheme_text = dissect_length_value(buffer, scheme_offset, 2)
        add_simple_string_as_subtree(buffer(scheme_offset, scheme_length), extension_subtree, "Scheme: %s", slice_scheme_length,
            slice_scheme_text, fields.grpc_ext_scheme_length, fields.grpc_ext_scheme)
        -- authority
        local authority_offset = scheme_offset + scheme_length
        local authority_length, slice_authority_length, slice_authority_text = dissect_length_value(buffer, authority_offset, 2)
        add_simple_string_as_subtree(buffer(authority_offset, authority_length), extension_subtree, "Authority: %s", slice_authority_length,
            slice_authority_text, fields.grpc_ext_authority_length, fields.grpc_ext_authority)
        -- service
        local service_offset = authority_offset + authority_length
        local service_length, slice_service_length, slice_service_text = dissect_length_value(buffer, service_offset, 2)
        add_simple_string_as_subtree(buffer(service_offset, service_length), extension_subtree, "Service: %s", slice_service_length,
            slice_service_text, fields.grpc_ext_service_length, fields.grpc_ext_service)
        -- method
        local method_offset = service_offset + service_length
        local method_length, slice_method_length, slice_method_text = dissect_length_value(buffer, method_offset, 2)
        add_simple_string_as_subtree(buffer(method_offset, method_length), extension_subtree, "Method: %s", slice_method_length,
            slice_method_text, fields.grpc_ext_method_length, fields.grpc_ext_method)
        -- metadata array
        local metadata_array_offset = method_offset + method_length
        local slice_metadata_array_length = buffer(metadata_array_offset, 4)
        local slice_metadata_array_size = buffer(metadata_array_offset + 4, 4)
        local metadata_array_length = slice_metadata_array_length:le_int()
        local metadata_array_size = slice_metadata_array_size:le_int()
        local length = 8
        local label = string.format("Metadata (%d items)", metadata_array_size)
        local metadata_array_subtree = extension_subtree:add(zilla_protocol, buffer(metadata_array_offset, length), label)
        metadata_array_subtree:add_le(fields.grpc_ext_metadata_array_length, slice_metadata_array_length)
        metadata_array_subtree:add_le(fields.grpc_ext_metadata_array_size, slice_metadata_array_size)
        local item_offset = metadata_array_offset + length
        for i = 1, metadata_array_size do
            local record_length = dissect_and_add_grpc_metadata(buffer, extension_subtree, item_offset)
            item_offset = item_offset + record_length
        end
    elseif frame_type_id == DATA_ID then
        local slice_deferred = buffer(offset, 4)
        extension_subtree:add_le(fields.grpc_ext_deferred, slice_deferred)
    elseif frame_type_id == ABORT_ID or frame_type_id == RESET_ID then
        local status_length, slice_status_length, slice_status_text = dissect_length_value(buffer, offset, 2)
        add_simple_string_as_subtree(buffer(offset, status_length), extension_subtree, "Status: %s", slice_status_length,
            slice_status_text, fields.grpc_ext_status_length, fields.grpc_ext_status)
    end
end

function add_simple_string_as_subtree(buffer, tree, label_format, slice_length, slice_text, field_length, field_text)
    local text = slice_text:string()
    local label = string.format(label_format, text)
    local subtree = tree:add(zilla_protocol, buffer, label)
    subtree:add_le(field_length, slice_length)
    subtree:add(field_text, slice_text)
end

function dissect_and_add_grpc_metadata(buffer, extension_subtree, metadata_offset)
    local offset = metadata_offset
    -- type
    local slice_type_id = buffer(offset, 1)
    local type = grpc_types[slice_type_id:le_int()]
    offset = offset + 1
    -- name_length
    local name_length, slice_name_length_varint, length_name_length = decode_varint32(buffer, offset)
    offset = offset + length_name_length
    -- name
    local slice_name = buffer(offset, name_length)
    local name = slice_name:string()
    offset = offset + name_length
    -- value_length
    local value_length, slice_value_length_varint, length_value_length = decode_varint32(buffer, offset)
    offset = offset + length_value_length
    -- value
    local slice_value = buffer(offset, value_length)
    local value = slice_value:string()
    -- add subtree
    local record_length = 1 + length_name_length + name_length + length_value_length + value_length
    local label = string.format("Metadata: [%s] %s: %s", type, name, value)
    local metadata_subtree = extension_subtree:add(zilla_protocol, buffer(metadata_offset, record_length), label)
    metadata_subtree:add(fields.grpc_ext_metadata_type, slice_type_id)
    metadata_subtree:add(fields.grpc_ext_metadata_name_length_varint, slice_name_length_varint)
    metadata_subtree:add(fields.grpc_ext_metadata_name_length, name_length)
    metadata_subtree:add(fields.grpc_ext_metadata_name, slice_name)
    metadata_subtree:add(fields.grpc_ext_metadata_value_length_varint, slice_value_length_varint)
    metadata_subtree:add(fields.grpc_ext_metadata_value_length, value_length)
    metadata_subtree:add(fields.grpc_ext_metadata_value, slice_value)
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

local data_dissector = DissectorTable.get("tcp.port")
data_dissector:add(7114, zilla_protocol)
