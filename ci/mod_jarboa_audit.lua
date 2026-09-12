-- Test fixtures only: never enabled on a user's server or in the Android application.
local function audit_message(event)
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
        body:find("After reconnect", 1, true) or body:find("While Alice", 1, true) or
        body:find("Text during upload", 1, true) or
        body:find("aesgcm:", 1, true) then
        module:log("error", "JARBOA_TEST_LEAK malformed encrypted message: %s", id)
    else
        module:log("info", "JARBOA_TEST_ENCRYPTED %s", id)
    end
end

-- Smack may lock an established one-to-one chat to the contact's active resource after the first
-- reply. Prosody consequently routes some outgoing stanzas as full JIDs and others as bare JIDs.
module:hook("pre-message/bare", audit_message, 1000)
module:hook("pre-message/full", audit_message, 1000)

-- Deliberately slow slot response: OMEMO discovery and messaging must still work while it waits.
local st = require "util.stanza"
-- Reject the recovery fixture account's first two bundle publications. Authentication succeeds,
-- but OMEMO must fail closed and then recover without UI interaction or another login.
local rejected_bundles = 0
module:hook("iq-set/bare/http://jabber.org/protocol/pubsub:pubsub", function(event)
    if event.origin.username ~= "recovery" or rejected_bundles >= 2 then return end
    local pubsub = event.stanza:get_child("pubsub", "http://jabber.org/protocol/pubsub")
    local publish = pubsub and pubsub:get_child("publish")
    if not publish or not (publish.attr.node or ""):match("^eu%.siacs%.conversations%.axolotl%.bundles:") then return end
    rejected_bundles = rejected_bundles + 1
    module:log("info", "JARBOA_TEST_OMEMO_TEMPORARY_FAILURE %d", rejected_bundles)
    event.origin.send(st.error_reply(event.stanza, "wait", "service-unavailable"))
    return true
end, 1000)

module:hook("iq-get/host/urn:xmpp:http:upload:0:request", function(event)
    local request = event.stanza:get_child("request", "urn:xmpp:http:upload:0")
    if not request or request.attr.filename ~= "ci-delayed.jpg" then return end
    module:log("info", "JARBOA_TEST_DELAYED_UPLOAD")
    local reply = st.reply(event.stanza):tag("slot", { xmlns = "urn:xmpp:http:upload:0" })
        :tag("put", { url = "https://upload.jarboa.test/ci-delayed.jpg" }):up()
        :tag("get", { url = "https://upload.jarboa.test/ci-delayed.jpg" })
    module:add_timer(4, function() event.origin.send(reply) end)
    return true
end, 1000)
