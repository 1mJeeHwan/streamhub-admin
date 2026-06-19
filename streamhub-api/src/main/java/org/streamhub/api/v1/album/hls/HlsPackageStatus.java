package org.streamhub.api.v1.album.hls;

/**
 * Bulk-encryption progress counts returned by {@code GET /v1/album/hls/package-status}.
 *
 * @param total     all tracks that are or will be encrypted (packaged + remaining)
 * @param packaged  tracks already packaged as encrypted full tracks
 * @param remaining unpackaged tracks with a non-blank previewUrl, still to be processed
 */
public record HlsPackageStatus(long total, long packaged, long remaining) {
}
