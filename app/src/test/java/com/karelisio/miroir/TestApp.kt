package com.karelisio.miroir

/** Application de test : pas de WorkManager ni d'alarmes sous Robolectric. */
class TestApp : MiroirApp() {
    override fun startBackgroundWork() = Unit
}
