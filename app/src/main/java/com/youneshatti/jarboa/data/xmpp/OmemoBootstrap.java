package com.youneshatti.jarboa.data.xmpp;

import java.io.File;
import java.util.TreeSet;

import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jxmpp.jid.BareJid;

public final class OmemoBootstrap {
    private static File configuredStorageDirectory;

    private OmemoBootstrap() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void initialize(File storageDirectory) {
        ensureStorageDirectory(storageDirectory);
        File absoluteStorageDirectory = storageDirectory.getAbsoluteFile();
        if (configuredStorageDirectory != null) {
            if (!configuredStorageDirectory.equals(absoluteStorageDirectory)) {
                throw new IllegalStateException("Smack's OMEMO store is already configured for another directory.");
            }
            return;
        }

        if (!OmemoService.isServiceRegistered()) {
            SignalOmemoService.acknowledgeLicense();
            SignalOmemoService.setup();
        }
        OmemoService service = OmemoService.getInstance();
        service.setOmemoStoreBackend(
                new SignalCachingOmemoStore(new SignalFileBasedOmemoStore(absoluteStorageDirectory))
        );
        configuredStorageDirectory = absoluteStorageDirectory;
        OmemoConfiguration.setRenewOldSignedPreKeys(true);
        OmemoConfiguration.setAddOmemoHintBody(true);
    }

    /**
     * Purges every local device belonging to an account from Smack's existing store and cache.
     * Smack permits installing its store backend only once per process, so the backend must not
     * be replaced during sign-out.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void purgeLocalDevices(BareJid localUser) {
        if (configuredStorageDirectory == null || !OmemoService.isServiceRegistered()) {
            throw new IllegalStateException("Smack's OMEMO store has not been configured.");
        }
        OmemoStore store = OmemoService.getInstance().getOmemoStoreBackend();
        for (Integer deviceId : new TreeSet<Integer>(store.localDeviceIdsOf(localUser))) {
            store.purgeOwnDeviceKeys(new OmemoDevice(localUser, deviceId));
        }
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
