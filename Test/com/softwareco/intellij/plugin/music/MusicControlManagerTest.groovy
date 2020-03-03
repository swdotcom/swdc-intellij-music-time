package com.softwareco.intellij.plugin.music

class MusicControlManagerTest extends GroovyTestCase {
    void testIsSpotifyConnected() {
        assertEquals(true, MusicControlManager.spotifyCacheState)
    }

    void testIsSpotifyDisconnected() {
        assertEquals(false, MusicControlManager.spotifyCacheState)
    }
}
