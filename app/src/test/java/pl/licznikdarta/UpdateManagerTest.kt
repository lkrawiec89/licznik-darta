package pl.licznikdarta

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun parseCustomUpdateJson_valid() {
        val json = JSONObject().apply {
            put("versionCode", 34)
            put("versionName", "1.1.23")
            put("apkUrl", "https://example.com/app-release.apk")
            put("releaseNotes", "Nowości w wersji 1.1.23")
            put("mandatory", false)
        }

        val info = UpdateManager.parseCustomUpdateJson(json)
        assertNotNull(info)
        assertEquals(34, info!!.versionCode)
        assertEquals("1.1.23", info.versionName)
        assertEquals("https://example.com/app-release.apk", info.apkUrl)
        assertEquals("Nowości w wersji 1.1.23", info.releaseNotes)
        assertFalse(info.mandatory)
    }

    @Test
    fun parseCustomUpdateJson_downloadUrlFallback() {
        val json = JSONObject().apply {
            put("versionCode", 35)
            put("downloadUrl", "https://example.com/build35.apk")
        }

        val info = UpdateManager.parseCustomUpdateJson(json)
        assertNotNull(info)
        assertEquals(35, info!!.versionCode)
        assertEquals("https://example.com/build35.apk", info.apkUrl)
    }

    @Test
    fun parseCustomUpdateJson_missingApk() {
        val json = JSONObject().apply {
            put("versionCode", 35)
        }
        val info = UpdateManager.parseCustomUpdateJson(json)
        assertNull(info)
    }

    @Test
    fun parseGitHubRelease_validWithApkAsset() {
        val json = JSONObject().apply {
            put("tag_name", "v1.1.23")
            put("body", "Release notes:\n- Fixes\nbuild: 34")
            put("assets", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "source.tar.gz")
                    put("browser_download_url", "https://github.com/source.tar.gz")
                })
                put(JSONObject().apply {
                    put("name", "LicznikDarta-1.1.23.apk")
                    put("browser_download_url", "https://github.com/releases/download/v1.1.23/LicznikDarta-1.1.23.apk")
                })
            })
        }

        val info = UpdateManager.parseGitHubRelease(json)
        assertNotNull(info)
        assertEquals(34, info!!.versionCode)
        assertEquals("1.1.23", info.versionName)
        assertEquals("https://github.com/releases/download/v1.1.23/LicznikDarta-1.1.23.apk", info.apkUrl)
        assertTrue(info.releaseNotes.contains("Fixes"))
    }

    @Test
    fun parseGitHubRelease_noApkAssetReturnsNull() {
        val json = JSONObject().apply {
            put("tag_name", "v1.1.23")
            put("body", "build: 34")
            put("assets", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "changelog.txt")
                    put("browser_download_url", "https://github.com/changelog.txt")
                })
            })
        }

        val info = UpdateManager.parseGitHubRelease(json)
        assertNull(info)
    }

    @Test
    fun parseVersionCodeFromTag_semver() {
        assertEquals(10123, UpdateManager.parseVersionCodeFromTag("v1.1.23"))
        assertEquals(10200, UpdateManager.parseVersionCodeFromTag("1.2.0"))
        assertEquals(34, UpdateManager.parseVersionCodeFromTag("v34"))
    }
}
