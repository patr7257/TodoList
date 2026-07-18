package dk.dtu.update;

/**
 * A newer GitHub release the client can offer to install.
 *
 * @param version         the release version without a leading "v", e.g. "1.2.3"
 * @param releasePageUrl  the release html_url (used for "Release notes" and fallbacks)
 * @param assetUrl        download URL of the installer asset for this platform,
 *                        or null when no auto-installable asset was found
 */
public record ReleaseInfo(String version, String releasePageUrl, String assetUrl) {

    /**
     * True when this release ships an installer asset we can download and launch.
     */
    public boolean hasInstallableAsset() {
        return assetUrl != null && !assetUrl.isBlank();
    }
}
