-- Test fixtures only: never enabled on a user's server or in the Android application.
module:hook("pre-message/bare", function(event)
    local stanza = event.stanza
    local id = stanza.attr.id or ""
    if id:sub(1, 3) ~= "ci-" then return end
    if id == "ci-blocked" then
        module:log("error", "JARBOA_TEST_LEAK rejected message was transmitted")
        return
    end
    local encrypted = stanza:get_child("encrypted", "eu.siacs.conversations.axolotl")
    local body = stanza:get_child_text("body") or ""
    if not encrypted or not encrypted:get_child("payload") or
        body:find("Encrypted hello", 1, true) or body:find("Encrypted reply", 1, true) or
        body:find("After reconnect", 1, true) or body:find("While Alice", 1, true) then
        module:log("error", "JARBOA_TEST_LEAK malformed encrypted message: %s", id)
    else
        module:log("info", "JARBOA_TEST_ENCRYPTED %s", id)
    end
end, 1000)
