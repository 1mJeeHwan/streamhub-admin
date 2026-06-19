package org.streamhub.api.v1.album.hls;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link HlsPackagingService#safeExtension(String)}: a clean short extension is kept,
 * but any path-traversal payload (slashes, backslashes, {@code ..}) or otherwise unsafe extension
 * falls back to {@code .mp3}, so the temp input path cannot escape the work directory.
 */
class HlsPackagingExtensionTest {

    @ParameterizedTest
    @CsvSource({
            "song.mp3, .mp3",
            "song.WAV, .WAV",
            "song.flac, .flac",
            "a.b.m4a, .m4a",
            "track.opus, .opus"
    })
    void safeExtension_keepsCleanExtension(String filename, String expected) {
        assertThat(HlsPackagingService.safeExtension(filename)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x.mp3/../../../tmp/evil",   // traversal after a fake extension
            "x.mp3/../../etc/passwd",
            "evil.mp3\\..\\..\\win",     // backslash traversal
            "..",                        // no real extension, dot is traversal
            "name.with.dots./escape",    // slash in the "extension"
            "noextension",               // no dot at all
            "x.toolongext",              // > 5 chars
            "x.has space",               // non-alphanumeric
            "x.",                        // empty extension
            "x.e!"                       // illegal char
    })
    void safeExtension_rejectsTraversalAndUnsafe_defaultsToMp3(String filename) {
        assertThat(HlsPackagingService.safeExtension(filename)).isEqualTo(".mp3");
    }

    @Test
    void safeExtension_nullDefaultsToMp3() {
        assertThat(HlsPackagingService.safeExtension(null)).isEqualTo(".mp3");
    }
}
