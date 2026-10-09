package de.rawinstinctai.apkdrop;

/** A self-update can only target this installed package and its original signer. */
final class CompanionIdentity {
    static void require(InstallContract release,InstalledState installed) {
        if(installed==null || !release.slug.equals("apkdrop-companion")
                || !release.packageName.equals(installed.packageName) || !release.signers.equals(installed.signers))
            throw new SecurityException("Selbst-Update blockiert · Paket oder ursprüngliche Signatur stimmt nicht.");
    }
}
