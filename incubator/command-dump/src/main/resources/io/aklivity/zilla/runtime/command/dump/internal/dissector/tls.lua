-- tls dissector
TLS_ID = 0x99f321bc
register_dissector(TLS_ID, "tls", nil, function (payload) return Dissector.get("tls") end)

