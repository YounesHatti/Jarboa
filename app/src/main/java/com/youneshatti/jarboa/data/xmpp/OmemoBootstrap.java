package com.youneshatti.jarboa.data.xmpp;

import java.io.File;

import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;

public final class OmemoBootstrap {
    private OmemoBootstrap() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void initialize(File storageDirectory) {
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw new IllegalStateException("Jarboa could not create its private OMEMO key directory.");
        }
        if (!OmemoService.isServiceRegistered()) {
            SignalOmemoService.acknowledgeLicense();
            SignalOmemoService.setup();
            OmemoService service = OmemoService.getInstance();
            service.setOmemoStoreBackend(new SignalFileBasedOmemoStore(storageDirectory));
        }
        OmemoConfiguration.setRenewOldSignedPreKeys(true);
        OmemoConfiguration.setAddOmemoHintBody(true);
    }
}
