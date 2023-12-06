zilla_protocol = Proto("Zilla", "Zilla Frames")

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
    -- all frames
    frame_type_id = ProtoField.uint32("zilla.frame_type_id", "frameTypeId", base.HEX),
    frame_type_name = ProtoField.string("zilla.frame_type_name", "frameTypeName", base.NONE),
    protocol_type_id = ProtoField.uint32("zilla.protocol_type_id", "protocolTypeId", base.HEX),
    protocol_type_name = ProtoField.string("zilla.protocol_type_name", "protocolTypeName", base.NONE),
    stream_type_id = ProtoField.uint32("zilla.stream_type_id", "streamTypeId", base.HEX),
    stream_type_name = ProtoField.string("zilla.stream_type_name", "streamTypeName", base.NONE),
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

    -- begin frame
    affinity = ProtoField.uint64("zilla.affinity", "affinity", base.HEX),
    extension = ProtoField.bytes("zilla.extension", "extension", base.NONE),

    -- data frame
    flags = ProtoField.uint8("zilla.flags", "flags", base.HEX),
    budget_id = ProtoField.uint64("zilla.budget_id", "budgetId", base.HEX),
    reserved = ProtoField.int32("zilla.reserved", "reserved", base.DEC),
    length = ProtoField.int32("zilla.length", "length", base.DEC),
    offset = ProtoField.int64("zilla.offset", "offset", base.DEC), -- TODO: name?
    offset_maximum = ProtoField.string("zilla.offset_maximum", "offset/maximum", base.NONE), -- TODO: name?
    payload = ProtoField.protocol("zilla.payload", "payload", base.HEX),
    -- extension

    -- window frame
    -- budget_id
    padding = ProtoField.int32("zilla.padding", "padding", base.DEC),
    minimum = ProtoField.int32("zilla.minimum", "minimum", base.DEC),
    capabilities = ProtoField.uint8("zilla.capabilities", "capabilities", base.HEX)
}

zilla_protocol.fields = fields;

function zilla_protocol.dissector(buffer, pinfo, tree)
    if buffer:len() == 0 then return end

    local subtree = tree:add(zilla_protocol, buffer(), "Zilla Frame")

    local slices = {
        frame_type_id = buffer(0, 4),
        protocol_type_id = buffer(4, 4),
        origin_id = buffer(8, 8),
        routed_id = buffer(16, 8),
        stream_id = buffer(24, 8),
        sequence = buffer(32, 8),
        acknowledge = buffer(40, 8),
        maximum = buffer(48, 4),
        timestamp = buffer(52, 8),
        trace_id = buffer(60, 8),
        authorization = buffer(68, 8)
    }

    local frame_type_id = slices.frame_type_id:le_uint()
    local frame_type_name = resolve_frame_type_name(frame_type_id)
    subtree:add_le(fields.frame_type_id, slices.frame_type_id)
    subtree:add(fields.frame_type_name, frame_type_name)

    local protocol_type_id = slices.protocol_type_id:le_uint()
    local protocol_type_name = resolve_type_name(protocol_type_id)
    subtree:add_le(fields.protocol_type_id, slices.protocol_type_id)
    subtree:add(fields.protocol_type_name, protocol_type_name)

    subtree:add_le(fields.origin_id, slices.origin_id)
    subtree:add_le(fields.routed_id, slices.routed_id)
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
    subtree:add_le(fields.sequence, slices.sequence)
    subtree:add_le(fields.acknowledge, slices.acknowledge)
    subtree:add_le(fields.maximum, slices.maximum)
    subtree:add_le(fields.timestamp, slices.timestamp)
    subtree:add_le(fields.trace_id, slices.trace_id)
    subtree:add_le(fields.authorization, slices.authorization)

    pinfo.cols.protocol = zilla_protocol.name
    pinfo.cols.info:set('todo ...')

    -- begin
    if frame_type_id == BEGIN_ID then
        slices.affinity = buffer(76, 8)
        subtree:add_le(fields.affinity, slices.affinity)

        if buffer:len() > 84 then
            slices.stream_type_id = buffer(84, 4)
            subtree:add(fields.stream_type_id, slices.stream_type_id)
            local stream_type_id = slices.stream_type_id:le_uint();
            local stream_type_name = resolve_type_name(stream_type_id)
            subtree:add(fields.stream_type_name, stream_type_name)

            slices.extension = buffer(84)
            subtree:add(fields.extension, slices.extension)
        end
    end

    -- data
    if frame_type_id == DATA_ID then
        slices.flags = buffer(76, 1)
        slices.budget_id = buffer(77, 8)
        slices.reserved = buffer(85, 4)
        slices.length = buffer(89, 4)
        local length = slices.length:le_int()
        slices.payload = buffer(93, length)

        subtree:add_le(fields.flags, slices.flags)
        subtree:add_le(fields.budget_id, slices.budget_id)
        subtree:add_le(fields.reserved, slices.reserved)
        subtree:add_le(fields.length, slices.length)
        subtree:add(fields.payload, slices.payload)

        local sequence = slices.sequence:le_int64();
        local acknowledge = slices.acknowledge:le_int64();
        local maximum = slices.maximum:le_int();
        local reserved = slices.reserved:le_int();
        local offset = sequence - acknowledge + reserved;
        local offset_maximum = offset .. "/" .. maximum
        subtree:add(fields.offset, offset)
        subtree:add(fields.offset_maximum, offset_maximum)

        local dissector = resolve_dissector(protocol_type_name, slices.payload:tvb())
        if dissector then
            dissector:call(slices.payload:tvb(), pinfo, tree)
        end
    end

    -- window
    if frame_type_id == WINDOW_ID then
        slices.budget_id = buffer(76, 8)
        slices.padding = buffer(84, 4)
        slices.minimum = buffer(88, 4)
        slices.capabilities = buffer(92, 1)

        subtree:add_le(fields.budget_id, slices.budget_id)
        subtree:add_le(fields.padding, slices.padding)
        subtree:add_le(fields.minimum, slices.minimum)
        subtree:add_le(fields.capabilities, slices.capabilities)

        local sequence = slices.sequence:le_int64();
        local acknowledge = slices.acknowledge:le_int64();
        local maximum = slices.maximum:le_int();
        local offset = sequence - acknowledge;
        local offset_maximum = offset .. "/" .. maximum
        subtree:add(fields.offset, offset)
        subtree:add(fields.offset_maximum, offset_maximum)

        pinfo.cols.info:set("[" .. offset_maximum .. "]")
    end
end

function resolve_frame_type_name(frame_type_id)
    local frame_type_name = ""
        if frame_type_id == BEGIN_ID then frame_type_name = "BEGIN"
    elseif frame_type_id == DATA_ID then frame_type_name = "DATA"
    elseif frame_type_id == END_ID then frame_type_name = "END"
    elseif frame_type_id == ABORT_ID then frame_type_name = "ABORT"
    elseif frame_type_id == FLUSH_ID then frame_type_name = "FLUSH"
    elseif frame_type_id == RESET_ID then frame_type_name = "RESET"
    elseif frame_type_id == WINDOW_ID then frame_type_name = "WINDOW"
    elseif frame_type_id == SIGNAL_ID then frame_type_name = "SIGNAL"
    elseif frame_type_id == CHALLENGE_ID then frame_type_name = "CHALLENGE"
    end
    return frame_type_name
end

function resolve_type_name(type_id)
    local type_name = ""
        if type_id == AMQP_ID  then type_name = "amqp"
    elseif type_id == GRPC_ID  then type_name = "grpc"
    elseif type_id == HTTP_ID  then type_name = "http"
    elseif type_id == KAFKA_ID then type_name = "kafka"
    elseif type_id == MQTT_ID  then type_name = "mqtt"
    elseif type_id == PROXY_ID then type_name = "proxy"
    elseif type_id == TLS_ID   then type_name = "tls"
    end
    return type_name
end

function resolve_dissector(protocol_type_name, payload)
    local dissector
        if protocol_type_name == "amqp"  then dissector = Dissector.get("amqp")
    elseif protocol_type_name == "grpc"  then dissector = Dissector.get("grpc")
    elseif protocol_type_name == "http"  then dissector = resolve_http_dissector(payload)
    elseif protocol_type_name == "kafka" then dissector = Dissector.get("kafka")
    elseif protocol_type_name == "mqtt"  then dissector = Dissector.get("mqtt")
    elseif protocol_type_name == "tls"   then dissector = Dissector.get("tls")
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
