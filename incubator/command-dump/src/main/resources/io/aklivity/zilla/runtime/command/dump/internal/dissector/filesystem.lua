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
-- filesystem dissector
FILESYSTEM_ID = 0xe4e6aa9e

add_field("filesystem_ext_capabilities", ProtoField.uint32("zilla.filesystem_ext.capabilities", "Capabilities", base.HEX))
add_field("filesystem_ext_capabilities_create_directory", ProtoField.uint32("zilla.filesystem_ext.capabilities_create_directory",
        "CREATE_DIRECTORY", base.DEC, flags_types, 0x01))
add_field("filesystem_ext_capabilities_create_file", ProtoField.uint32("zilla.filesystem_ext.capabilities_create_file",
        "CREATE_FILE", base.DEC, flags_types, 0x02))
add_field("filesystem_ext_capabilities_delete_directory", ProtoField.uint32("zilla.filesystem_ext.capabilities_delete_directory",
        "DELETE_DIRECTORY", base.DEC, flags_types, 0x04))
add_field("filesystem_ext_capabilities_delete_file", ProtoField.uint32("zilla.filesystem_ext.capabilities_delete_file",
        "DELETE_FILE", base.DEC, flags_types, 0x08))
add_field("filesystem_ext_capabilities_read_directory", ProtoField.uint32("zilla.filesystem_ext.capabilities_read_directory",
        "READ_DIRECTORY", base.DEC, flags_types, 0x10))
add_field("filesystem_ext_capabilities_read_file", ProtoField.uint32("zilla.filesystem_ext.capabilities_read_file",
        "READ_FILE", base.DEC, flags_types, 0x20))
add_field("filesystem_ext_capabilities_read_file_changes", ProtoField.uint32("zilla.filesystem_ext.capabilities_read_file_changes",
        "READ_FILE_CHANGES", base.DEC, flags_types, 0x40))
add_field("filesystem_ext_capabilities_read_metadata", ProtoField.uint32("zilla.filesystem_ext.capabilities_read_metadata",
        "READ_METADATA", base.DEC, flags_types, 0x80))
add_field("filesystem_ext_capabilities_write_file", ProtoField.uint32("zilla.filesystem_ext.capabilities_write_file",
        "WRITE_FILE", base.DEC, flags_types, 0x100))
add_field("filesystem_ext_directory_length", ProtoField.int16("zilla.filesystem_ext.directory_length", "Length", base.DEC))
add_field("filesystem_ext_directory", ProtoField.string("zilla.filesystem_ext.directory", "Directory", base.NONE))
add_field("filesystem_ext_path_length", ProtoField.int16("zilla.filesystem_ext.path_length", "Length", base.DEC))
add_field("filesystem_ext_path", ProtoField.string("zilla.filesystem_ext.path", "Path", base.NONE))
add_field("filesystem_ext_type_length", ProtoField.int16("zilla.filesystem_ext.type_length", "Length", base.DEC))
add_field("filesystem_ext_type", ProtoField.string("zilla.filesystem_ext.type", "Type", base.NONE))
add_field("filesystem_ext_payload_size", ProtoField.int64("zilla.filesystem_ext.payload_size", "Payload Size", base.DEC))
add_field("filesystem_ext_tag_length", ProtoField.int16("zilla.filesystem_ext.tag_length", "Length", base.DEC))
add_field("filesystem_ext_tag", ProtoField.string("zilla.filesystem_ext.tag", "Tag", base.NONE))
add_field("filesystem_ext_timeout", ProtoField.int64("zilla.filesystem_ext.timeout", "Timeout", base.DEC))

function handle_filesystem_extension(buffer, offset, ext_subtree)
    -- BEGIN frame
    -- capabilities
    local capabilities_offset = offset
    local capabilities_length = 4
    local slice_capabilities = buffer(capabilities_offset, capabilities_length)
    local capabilities_label = string.format("Capabilities: 0x%08x", slice_capabilities:le_uint())
    local capabilities_subtree = ext_subtree:add(zilla_protocol, slice_capabilities, capabilities_label)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_create_directory, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_create_file, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_delete_directory, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_delete_file, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_directory, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_file, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_file_changes, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_read_metadata, slice_capabilities)
    capabilities_subtree:add_le(fields.filesystem_ext_capabilities_write_file, slice_capabilities)
    -- directory
    local directory_offset = capabilities_offset + capabilities_length
    local directory_length, slice_directory_length, slice_directory_text = dissect_length_value(buffer, directory_offset, 2)
    add_string_as_subtree(buffer(directory_offset, directory_length), ext_subtree, "Directory: %s",
        slice_directory_length, slice_directory_text, fields.filesystem_ext_directory_length, fields.filesystem_ext_directory)
    -- path
    local path_offset = directory_offset + directory_length
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

register_dissector(FILESYSTEM_ID, "filesystem", handle_filesystem_extension)

