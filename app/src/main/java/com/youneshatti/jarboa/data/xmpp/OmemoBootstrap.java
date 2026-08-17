package com.youneshatti.jarboa.data.xmpp;

import java.io.File;

import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;

public final class OmemoBootstrap {
    private OmemoBootstrap() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void initialize(File storageDirectory) {
        ensureStorageDirectory(storageDirectory);
        if (!OmemoService.isServiceRegistered()) {
            SignalOmemoService.acknowledgeLicense();
            SignalOmemoService.setup();
        }
        installStore(storageDirectory);
        OmemoConfiguration.setRenewOldSignedPreKeys(true);
        OmemoConfiguration.setAddOmemoHintBody(true);
    }

    /**
     * Replaces Smack's in-memory OMEMO cache after local keys are erased on sign-out.
     */
    public static synchronized void resetStorage(File storageDirectory) {
        ensureStorageDirectory(storageDirectory);
        if (!OmemoService.isServiceRegistered()) {
            initialize(storageDirectory);
            return;
        }
        installStore(storageDirectory);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installStore(File storageDirectory) {
        OmemoService service = OmemoService.getInstance();
        service.setOmemoStoreBackend(
                new SignalCachingOmemoStore(new SignalFileBasedOmemoStore(storageDirectory))
        );
    }

    private static void ensureStorageDirectory(File storageDirectory) {
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw new IllegalStateException("Jarboa could not create its private OMEMO key directory.");
        }
        if (!storageDirectory.isDirectory()) {
            throw new IllegalStateException("Jarboa's private OMEMO key path is not a directory.");
        }
    }
}
