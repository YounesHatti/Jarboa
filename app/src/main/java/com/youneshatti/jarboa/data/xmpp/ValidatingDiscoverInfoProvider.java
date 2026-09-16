package com.youneshatti.jarboa.data.xmpp;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.jivesoftware.smack.packet.IqData;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IqProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.ParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverInfoBuilder;

/**
 * Validate XEP-0030 entries before constructing Smack's strict value objects. An absent/empty
 * identity category, identity type or feature var must not kill the entire XMPP stream.
 * Invalid entries confer no capabilities; valid siblings and extension providers are preserved.
 * This does not catch malformed XML, extension parsing errors, or change the global error policy.
 */
public final class ValidatingDiscoverInfoProvider extends IqProvider<DiscoverInfo> {
    public enum InvalidField { IDENTITY_CATEGORY, IDENTITY_TYPE, FEATURE_VAR }

    /** Local-only diagnostics; field names are fixed enums, never remote data or XML. */
    public static final class Result extends DiscoverInfo {
        private final Set<InvalidField> invalidFields;

        private Result(DiscoverInfo info, EnumSet<InvalidField> invalidFields) {
            super(info);
            this.invalidFields = Collections.unmodifiableSet(EnumSet.copyOf(invalidFields));
        }

        public Set<InvalidField> getInvalidFields() { return invalidFields; }
    }

    @Override
    public DiscoverInfo parse(XmlPullParser parser, int initialDepth, IqData iqData,
                              XmlEnvironment environment)
            throws XmlPullParserException, IOException, SmackParsingException {
        DiscoverInfoBuilder builder = DiscoverInfo.builder(iqData);
        builder.setNode(parser.getAttributeValue("node"));
        EnumSet<InvalidField> invalid = EnumSet.noneOf(InvalidField.class);
        while (true) {
            XmlPullParser.Event event = parser.next();
            if (event == XmlPullParser.Event.END_DOCUMENT) {
                throw new IOException("Unexpected end of discovery XML");
            }
            if (event == XmlPullParser.Event.END_ELEMENT && parser.getDepth() == initialDepth) {
                return new Result(builder.buildWithoutValidiation(), invalid);
            }
            if (event == XmlPullParser.Event.START_ELEMENT) {
                // Only direct children in the disco namespace are identity/feature entries.
                // Never interpret an unknown wrapper's children as advertised capabilities.
                if (DiscoverInfo.NAMESPACE.equals(parser.getNamespace())) {
                    if ("identity".equals(parser.getName())) {
                        String category = parser.getAttributeValue("category");
                        String type = parser.getAttributeValue("type");
                        boolean validCategory = category != null && !category.isEmpty();
                        boolean validType = type != null && !type.isEmpty();
                        if (!validCategory) invalid.add(InvalidField.IDENTITY_CATEGORY);
                        if (!validType) invalid.add(InvalidField.IDENTITY_TYPE);
                        if (validCategory && validType) {
                            builder.addIdentity(new DiscoverInfo.Identity(category, type,
                                    parser.getAttributeValue("name"), ParserUtils.getXmlLang(parser)));
                        }
                    } else if ("feature".equals(parser.getName())) {
                        String feature = parser.getAttributeValue("var");
                        if (feature == null || feature.isEmpty()) invalid.add(InvalidField.FEATURE_VAR);
                        else builder.addFeature(feature);
                    }
                    ParserUtils.forwardToEndTagOfDepth(parser, parser.getDepth());
                } else {
                    PacketParserUtils.addExtensionElement(builder, parser, environment);
                }
            }
        }
    }
}
