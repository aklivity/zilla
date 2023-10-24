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

local fields = {
    -- all frames
    frame_type_id = ProtoField.uint32("zilla.frame_type_id", "frameTypeId", base.HEX),
    frame_type_name = ProtoField.string("zilla.frame_type_name", "frameTypeName", base.NONE),
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
    stream_type_id = ProtoField.int32("zilla.stream_type_id", "streamTypeId", base.DEC),
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
        origin_id = buffer(4, 8),
        routed_id = buffer(12, 8),
        stream_id = buffer(20, 8),
        sequence = buffer(28, 8),
        acknowledge = buffer(36, 8),
        maximum = buffer(44, 4),
        timestamp = buffer(48, 8),
        trace_id = buffer(56, 8),
        authorization = buffer(64, 8)
    }

    local frame_type_id = slices.frame_type_id:le_int()
    local frame_type_name = "UNKNOWN"
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

    subtree:add_le(fields.frame_type_id, slices.frame_type_id)
    subtree:add_le(fields.frame_type_name, frame_type_name)
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
        slices.affinity = buffer(72, 8)
        subtree:add_le(fields.affinity, slices.affinity)

        if buffer:len() > 80 then
            slices.extension = buffer(80)
            subtree:add(fields.extension, slices.extension)
        end
    end

    -- data
    if frame_type_id == DATA_ID then
        slices.flags = buffer(72, 1)
        slices.budget_id = buffer(73, 8)
        slices.reserved = buffer(81, 4)
        slices.length = buffer(85, 4)
        slices.stream_type_id = buffer(89, 4)

        subtree:add_le(fields.flags, slices.flags)
        subtree:add_le(fields.budget_id, slices.budget_id)
        subtree:add_le(fields.reserved, slices.reserved)
        subtree:add_le(fields.length, slices.length)
        subtree:add_le(fields.stream_type_id, slices.stream_type_id)

        local sequence = slices.sequence:le_int64();
        local acknowledge = slices.acknowledge:le_int64();
        local maximum = slices.maximum:le_int();
        local reserved = slices.reserved:le_int();
        local offset = sequence - acknowledge + reserved;
        local offset_maximum = offset .. "/" .. maximum
        subtree:add(fields.offset, offset)
        subtree:add(fields.offset_maximum, offset_maximum)
        --pinfo.cols.info:set(offset_maximum)

        local length = slices.length:le_int()
        --subtree:add(fields.payload, buffer(89, length))
        subtree:add(fields.payload, buffer(93, length))

        local oid = string.format("%016x", tostring(slices.origin_id:le_uint64()))
        local rid = string.format("%016x", tostring(slices.routed_id:le_uint64()))
        -- print(string.format("%016x %016x", tostring(oid), tostring(rid)))
        --print(ridh)
        --if oid == 0x0000000a0000000e or rid == 0x0000000a0000000e then
        --if rid == 0x0000000a0000000e then
        --if oid == "0000000a0000000e" or rid == "0000000a0000000e" then
        -- if oid == "0000000100000005" or rid == "0000000100000005" then

        local stream_type_id = slices.stream_type_id:le_int()
        print(stream_type_id)
        local dissector
            if stream_type_id == 4 then dissector = Dissector.get("http")
        elseif stream_type_id == 5 then dissector = Dissector.get("kafka")
        --elseif stream_type_id == 6 then dissector = Dissector.get("http")
        elseif stream_type_id == 6 then dissector = Dissector.get("kafka")
        elseif stream_type_id == 7 then dissector = Dissector.get("mqtt")
        end

        if dissector then
          --local dissector = Dissector.get("http2")
          --local dissector = Dissector.get("mqtt")
          --local dissector = Dissector.get("http")
          --print("hello")
          --dissector:call(buffer(89, len):tvb(), pinfo, tree)
          dissector:call(buffer(93, len):tvb(), pinfo, tree)
        end
    end

    -- window
    if frame_type_id == WINDOW_ID then
        slices.budget_id = buffer(72, 8)
        slices.padding = buffer(80, 4)
        slices.minimum = buffer(84, 4)
        slices.capabilities = buffer(88, 1)

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


  -- local http_dissector = Dissector.get("http")
  -- http_dissector:call(buffer(89, len):tvb(), pinfo, tree)

  -- local status, err = pcall(http2_dissector.call, http2_dissector, buffer(89, len):tvb(), pinfo, tree)
  -- print(status)
  -- print(err)
  
  -- local pld = buffer(89, len):tvb()
  -- local protocol = determine_subprotocol(pld)
  -- if protocol ~= "unknown" then
  --   print(protocol)
  --   local dissector = Dissector.get(protocol)
  --   print(dissector)
  --   dissector:call(pld, pinfo, tree)
  -- end
end

-- local tcp_port = DissectorTable.get("tcp.port")
-- tcp_port:add(59274, zilla_protocol)
local data_dissector = DissectorTable.get("ethertype")
data_dissector:add(0x4242, zilla_protocol)

-- DissectorTable.new("zilla.routed_id", "Zilla routedId", ftypes.uint64, base.HEX)

-- print(data_dissector)

-- function determine_subprotocol(payload)
--     if payload:range(0, 4):string() == "HTTP" then
--         return "http"
--     elseif payload:range(0, 3):string() == "PRI" then
--         return "http2"
--     elseif payload:range(0, 3):int() + 9 == payload:len() then
--         return "http2"
--     else
--         return "unknown"
--     end
-- end


-- local function dump(o)
--    if type(o) == 'table' then
--       local s = '{ '
--       for k,v in pairs(o) do
--          if type(k) ~= 'number' then k = '"'..k..'"' end
--          s = s .. '['..k..'] = ' .. dump(v) .. ',' .. "\n"
--       end
--       return s .. '} '
--    else
--       return tostring(o)
--    end
-- end

local function hello()
  -- print(DissectorTable.get("tcp.port"))
  -- print(dump(DissectorTable.list()))
  print("Hello Ati!")
end
register_menu("Test/Hello", hello, MENU_TOOLS_UNSORTED)
