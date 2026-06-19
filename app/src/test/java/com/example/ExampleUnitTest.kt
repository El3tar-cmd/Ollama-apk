package com.example
 
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
 
class ExampleUnitTest {
  @Test
  fun downloadAndExtractLibcxx() {
    val aarUrl = "https://repo1.maven.org/maven2/com/facebook/fbjni/fbjni/0.6.0/fbjni-0.6.0.aar"
    val tempFile = File("fbjni-temp.aar")
    if (tempFile.exists()) tempFile.delete()

    println("Downloading fbjni AAR from: $aarUrl")
    try {
        val url = URL(aarUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val code = conn.responseCode
        println("Response Code: $code")
        if (code == HttpURLConnection.HTTP_OK) {
            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            println("Downloaded AAR file completely!")
            
            // Now unzip and look for jni/arm64-v8a/libc++_shared.so
            var found = false
            ZipInputStream(BufferedInputStream(tempFile.inputStream())).use { zip ->
                var entry = zip.getNextEntry()
                while (entry != null) {
                    val name = entry.name
                    if (name.contains("libc++_shared.so") && name.contains("arm64-v8a")) {
                        println("Found target entry inside AAR: $name")
                        val destFile = File("src/main/jniLibs/arm64-v8a/libc++_shared.so")
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            zip.copyTo(out)
                        }
                        println("SUCCESS: Extracted to jniLibs: ${destFile.absolutePath}")
                        
                        // Also duplicate in assets
                        val assetDestFile = File("src/main/assets/arm64-v8a/libc++_shared.so")
                        assetDestFile.parentFile?.mkdirs()
                        destFile.copyTo(assetDestFile, overwrite = true)
                        println("SUCCESS: Copied backup to assets!")
                        
                        found = true
                        break
                    }
                    entry = zip.getNextEntry()
                }
            }
            if (!found) {
                println("ERROR: libc++_shared.so not found inside the AAR!")
            }
        } else {
            println("ERROR: HTTP request failed with code $code")
        }
    } catch (e: Exception) {
        println("ERROR: Failed to download/extract: ${e.message}")
        e.printStackTrace()
    } finally {
        if (tempFile.exists()) tempFile.delete()
    }
  }
}
