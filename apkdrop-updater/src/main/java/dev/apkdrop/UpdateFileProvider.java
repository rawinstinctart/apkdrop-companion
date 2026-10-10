package dev.apkdrop;

/** Separate manifest identity to coexist with an app's existing AndroidX FileProvider. */
public final class UpdateFileProvider extends androidx.core.content.FileProvider {
    public UpdateFileProvider() { super(); }
}
