zilla_protocol = Proto("Zilla", "Zilla Frames")

HEADER_OFFSET = 0
LABELS_OFFSET = 8

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

local fields = {
    -- header
    frame_type_id = ProtoField.uint32("zilla.frame_type_id", "frameTypeId", base.HEX),
    frame_type = ProtoField.string("zilla.frame_type", "frameType", base.NONE),
    protocol_type_id = ProtoField.uint32("zilla.protocol_type_id", "protocolTypeId", base.HEX),
    protocol_type = ProtoField.string("zilla.protocol_type", "protocolType", base.NONE),
    stream_type_id = ProtoField.uint32("zilla.stream_type_id", "streamTypeId", base.HEX),
    stream_type = ProtoField.string("zilla.stream_type", "streamType", base.NONE),

    -- labels
    labels_length = ProtoField.uint32("zilla.labels_length", "labelsLength", base.DEC),
    labels = ProtoField.bytes("zilla.labels", "labels", base.NONE),
    origin_namespace = ProtoField.string("zilla.origin_namespace", "originNamespace", base.STRING),
    origin_binding = ProtoField.string("zilla.origin_binding", "originBinding", base.STRING),
    routed_namespace = ProtoField.string("zilla.routed_namespace", "routedNamespace", base.STRING),
    routed_binding = ProtoField.string("zilla.routed_binding", "routedBinding", base.STRING),

    -- all frames
    origin_id = ProtoField.uint64("zilla.origin_id", "originId", base.HEX),
    routed_id = ProtoField.uint64("zilla.routed_id", "routedId", base.HEX),
    stream_id = ProtoField.uint64("zilla.stream_id", "streamId", base.HEX),
    stream_dir = ProtoField.string("zilla.stream_dir", "streamDirection", base.NONE),
    init_stream_id = ProtoField.uint64("zilla.init_stream_id", "initStreamId", base.HEX),
    sequence = ProtoField.int64("zilla.sequence", "sequence", base.DEC),
    acknowledge = ProtoField.int64("zilla.acknowledge", "acknowledge", base.DEC),
    maximum = ProtoField.int32("zilla.maximum", "maximum", base.DEC),
    timestamp = ProtoField.uint64("zilla.timestamp", "timestamp", base.HEX),
    trace_id = ProtoField.uint64("zilla.trace_id", "traceId", base.HEX),
    authorization = ProtoField.uint64("zilla.authorization", "authorization", base.HEX),

    -- almost all frames
    extension = ProtoField.bytes("zilla.extension", "extension", base.NONE),

    -- begin frame
    affinity = ProtoField.uint64("zilla.affinity", "affinity", base.HEX),

    -- data frame
    flags = ProtoField.uint8("zilla.flags", "flags", base.HEX),
    budget_id = ProtoField.uint64("zilla.budget_id", "budgetId", base.HEX),
    reserved = ProtoField.int32("zilla.reserved", "reserved", base.DEC),
    length = ProtoField.int32("zilla.length", "length", base.DEC),
    offset = ProtoField.int64("zilla.offset", "offset", base.DEC),
    offset_maximum = ProtoField.string("zilla.offset_maximum", "offset/maximum", base.NONE),
    payload = ProtoField.protocol("zilla.payload", "payload", base.HEX),

    -- window frame
    padding = ProtoField.int32("zilla.padding", "padding", base.DEC),
    minimum = ProtoField.int32("zilla.minimum", "minimum", base.DEC),
    capabilities = ProtoField.uint8("zilla.capabilities", "capabilities", base.HEX),

    -- signal frame
    cancel_id = ProtoField.uint64("zilla.cancel_id", "cancelId", base.HEX),
    signal_id = ProtoField.int32("zilla.signal_id", "signalId", base.DEC),
    context_id = ProtoField.int32("zilla.context_id", "contextId", base.DEC),
}

zilla_protocol.fields = fields;

function zilla_protocol.dissector(buffer, pinfo, tree)
    if buffer:len() == 0 then return end

    local subtree = tree:add(zilla_protocol, buffer(), "Zilla Frame")
    local slices = {}

    -- header
    local headerSubtree = subtree:add(zilla_protocol, buffer(), "Header")
    slices.frame_type_id = buffer(HEADER_OFFSET, 4)
    local frame_type_id = slices.frame_type_id:le_uint()
    local frame_type = resolve_frame_type(frame_type_id)
    headerSubtree:add_le(fields.frame_type_id, slices.frame_type_id)
    headerSubtree:add(fields.frame_type, frame_type)

    slices.protocol_type_id = buffer(HEADER_OFFSET + 4, 4)
    local protocol_type_id = slices.protocol_type_id:le_uint()
    local protocol_type = resolve_type(protocol_type_id)
    headerSubtree:add_le(fields.protocol_type_id, slices.protocol_type_id)
    headerSubtree:add(fields.protocol_type, protocol_type)

    -- labels
    local labelsSubtree = subtree:add(zilla_protocol, buffer(), "Labels")
    slices.labels_length = buffer(LABELS_OFFSET, 4)
    local labels_length = slices.labels_length:le_uint()
    slices.labels = buffer(LABELS_OFFSET + 4, labels_length)
    labelsSubtree:add_le(fields.labels_length, slices.labels_length)
    labelsSubtree:add(fields.labels, slices.labels)

    local label_offset = LABELS_OFFSET + 4;
    local origin_namespace_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    slices.origin_namespace = buffer(label_offset, origin_namespace_length)
    label_offset = label_offset + origin_namespace_length
    labelsSubtree:add(fields.origin_namespace, slices.origin_namespace)

    local origin_binding_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    slices.origin_binding = buffer(label_offset, origin_binding_length)
    label_offset = label_offset + origin_binding_length
    labelsSubtree:add(fields.origin_binding, slices.origin_binding)

    local routed_namespace_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    slices.routed_namespace = buffer(label_offset, routed_namespace_length)
    label_offset = label_offset + routed_namespace_length
    labelsSubtree:add(fields.routed_namespace, slices.routed_namespace)

    local routed_binding_length = buffer(label_offset, 4):le_uint()
    label_offset = label_offset + 4
    slices.routed_binding = buffer(label_offset, routed_binding_length)
    label_offset = label_offset + routed_binding_length
    labelsSubtree:add(fields.routed_binding, slices.routed_binding)

    -- frame
    local frame_offset = LABELS_OFFSET + labels_length
    slices.origin_id = buffer(frame_offset + 4, 8)
    subtree:add_le(fields.origin_id, slices.origin_id)
    slices.routed_id = buffer(frame_offset + 12, 8)
    subtree:add_le(fields.routed_id, slices.routed_id)
    slices.stream_id = buffer(frame_offset + 20, 8)
    subtree:add_le(fields.stream_id, slices.stream_id)
    local stream_id = slices.stream_id:le_uint64();
    local stream_dir
    local init_stream_id
    if (stream_id % 2) == UInt64(0) then
        stream_dir = "REP"
        init_stream_id = stream_id + UInt64(1)
    else
        stream_dir = "INI"
        init_stream_id = stream_id
    end
    subtree:add(fields.stream_dir, stream_dir)
    subtree:add(fields.init_stream_id, init_stream_id)

    slices.sequence = buffer(frame_offset + 28, 8)
    subtree:add_le(fields.sequence, slices.sequence)
    slices.acknowledge = buffer(frame_offset + 36, 8)
    subtree:add_le(fields.acknowledge, slices.acknowledge)
    slices.maximum = buffer(frame_offset + 44, 4)
    subtree:add_le(fields.maximum, slices.maximum)
    slices.timestamp = buffer(frame_offset + 48, 8)
    subtree:add_le(fields.timestamp, slices.timestamp)
    slices.trace_id = buffer(frame_offset + 56, 8)
    subtree:add_le(fields.trace_id, slices.trace_id)
    slices.authorization = buffer(frame_offset + 64, 8)
    subtree:add_le(fields.authorization, slices.authorization)

    pinfo.cols.protocol = zilla_protocol.name
    local info = "ZILLA " .. frame_type .. " " .. stream_dir
    if protocol_type ~= "" then
        info = info .. " p=" .. protocol_type
    end
    pinfo.cols.info:set(info)

    -- begin
    if frame_type_id == BEGIN_ID then
        slices.affinity = buffer(frame_offset + 72, 8)
        subtree:add_le(fields.affinity, slices.affinity)
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 80)
    end

    -- data
    if frame_type_id == DATA_ID then
        slices.flags = buffer(frame_offset + 72, 1)
        subtree:add_le(fields.flags, slices.flags)
        slices.budget_id = buffer(frame_offset + 73, 8)
        subtree:add_le(fields.budget_id, slices.budget_id)
        slices.reserved = buffer(frame_offset + 81, 4)
        subtree:add_le(fields.reserved, slices.reserved)

        local sequence = slices.sequence:le_int64();
        local acknowledge = slices.acknowledge:le_int64();
        local maximum = slices.maximum:le_int();
        local reserved = slices.reserved:le_int();
        local offset = sequence - acknowledge + reserved;
        local offset_maximum = offset .. "/" .. maximum
        subtree:add(fields.offset, offset)
        subtree:add(fields.offset_maximum, offset_maximum)

        local payloadSubtree = subtree:add(zilla_protocol, buffer(), "Payload")
        slices.length = buffer(frame_offset + 85, 4)
        local length = slices.length:le_int()
        slices.payload = buffer(frame_offset + 89, length)
        payloadSubtree:add_le(fields.length, slices.length)
        payloadSubtree:add(fields.payload, slices.payload)
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 89 + length)

        local dissector = resolve_dissector(protocol_type, slices.payload:tvb())
        if dissector then
            dissector:call(slices.payload:tvb(), pinfo, tree)
        end
    end

    -- end
    if frame_type_id == END_ID then
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 72)
    end

    -- abort
    if frame_type_id == ABORT_ID then
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 72)
    end

    -- flush
    if frame_type_id == FLUSH_ID then
        slices.budget_id = buffer(frame_offset + 72, 8)
        subtree:add_le(fields.budget_id, slices.budget_id)
        slices.reserved = buffer(frame_offset + 80, 4)
        subtree:add_le(fields.reserved, slices.reserved)
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 84)
    end

    -- reset
    if frame_type_id == RESET_ID then
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 72)
    end

    -- window
    if frame_type_id == WINDOW_ID then
        slices.budget_id = buffer(frame_offset + 72, 8)
        subtree:add_le(fields.budget_id, slices.budget_id)
        slices.padding = buffer(frame_offset + 80, 4)
        subtree:add_le(fields.padding, slices.padding)
        slices.minimum = buffer(frame_offset + 84, 4)
        subtree:add_le(fields.minimum, slices.minimum)
        slices.capabilities = buffer(frame_offset + 88, 1)
        subtree:add_le(fields.capabilities, slices.capabilities)

        local sequence = slices.sequence:le_int64();
        local acknowledge = slices.acknowledge:le_int64();
        local maximum = slices.maximum:le_int();
        local offset = sequence - acknowledge;
        local offset_maximum = offset .. "/" .. maximum
        subtree:add(fields.offset, offset)
        subtree:add(fields.offset_maximum, offset_maximum)

        pinfo.cols.info:set(info .. " [" .. offset_maximum .. "]")
    end

    -- signal
    if frame_type_id == SIGNAL_ID then
        slices.cancel_id = buffer(frame_offset + 72, 8)
        subtree:add_le(fields.cancel_id, slices.cancel_id)
        slices.signal_id = buffer(frame_offset + 80, 4)
        subtree:add_le(fields.signal_id, slices.signal_id)
        slices.context_id = buffer(frame_offset + 84, 4)
        subtree:add_le(fields.context_id, slices.context_id)

        local payloadSubtree = subtree:add(zilla_protocol, buffer(), "Payload")
        slices.length = buffer(frame_offset + 88, 4)
        local length = slices.length:le_int()
        slices.payload = buffer(frame_offset + 92, length)
        payloadSubtree:add_le(fields.length, slices.length)
        payloadSubtree:add(fields.payload, slices.payload)
    end

    -- challenge
    if frame_type_id == CHALLENGE_ID then
        handle_extension(buffer, slices, subtree, pinfo, info, frame_offset + 72)
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

function handle_extension(buffer, slices, subtree, pinfo, info, offset)
    if buffer:len() > offset then
        local extensionSubtree = subtree:add(zilla_protocol, buffer(), "Extension")
        slices.stream_type_id = buffer(offset, 4)
        extensionSubtree:add(fields.stream_type_id, slices.stream_type_id)

        local stream_type_id = slices.stream_type_id:le_uint();
        local stream_type = resolve_type(stream_type_id)
        extensionSubtree:add(fields.stream_type, stream_type)

        slices.extension = buffer(offset)
        extensionSubtree:add(fields.extension, slices.extension)

        pinfo.cols.info:set(info .. " s=" .. stream_type)
    end
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
    elseif protocol_type == "grpc"  then dissector = Dissector.get("grpc")
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

local data_dissector = DissectorTable.get("ethertype")
data_dissector:add(0x5a41, zilla_protocol)
