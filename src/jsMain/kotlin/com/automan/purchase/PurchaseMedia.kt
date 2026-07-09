package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import kotlin.js.Promise

private var cachedR2MediaEnabled: Boolean? = null
private var cachedMaxFileSizeBytes: Int = 5 * 1024 * 1024
private val pendingCarPictureFiles = mutableListOf<File>()

fun isR2CarPictureStorageEnabled(): Boolean = cachedR2MediaEnabled == true

fun ensureCarPictureMediaConfig(onReady: (Boolean) -> Unit = {}) {
    if (cachedR2MediaEnabled != null) {
        onReady(cachedR2MediaEnabled == true)
        return
    }
    window.fetch(apiUrl("config/media"))
        .then { response: dynamic ->
            if (response.ok) response.json() else js("({ r2Enabled: false })")
        }
        .then { cfg: dynamic ->
            cachedR2MediaEnabled = cfg.r2Enabled == true
            val maxSize = cfg.maxFileSizeBytes
            if (maxSize != null && maxSize != js("undefined")) {
                cachedMaxFileSizeBytes = (maxSize as Number).toInt()
            }
            onReady(cachedR2MediaEnabled == true)
        }
        .catch { _: dynamic ->
            cachedR2MediaEnabled = false
            onReady(false)
        }
}

fun resetPendingCarPictureUploads() {
    pendingCarPictureFiles.clear()
}

fun uploadPendingCarPicturesAfterCreate(purchaseId: Long): Promise<dynamic> {
    if (pendingCarPictureFiles.isEmpty()) {
        return Promise.resolve(js("[]"))
    }
    val files = pendingCarPictureFiles.toList()
    pendingCarPictureFiles.clear()
    var chain: Promise<dynamic> = Promise.resolve(js("[]"))
    for (file in files) {
        chain = chain.then { _: dynamic -> uploadCarPictureFile(purchaseId, file) }
    }
    return chain
}

fun uploadCarPictureFile(purchaseId: Long, file: File): Promise<dynamic> {
    val formData = js("new FormData()")
    formData.append("file", file)
    val requestInit = js("{}")
    requestInit.method = "POST"
    requestInit.body = formData
    return window.fetch(apiUrl("purchases/$purchaseId/media"), requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json()
            else response.text().then { err: dynamic ->
                Promise.reject(js("Error(err || 'Upload failed')"))
            }
        }
}

private fun appendCarPicturePreview(
    container: HTMLElement,
    pictureId: String,
    imageSrc: String,
    r2Media: Boolean,
    purchaseId: Long?,
    onRemove: (() -> Unit)? = null,
) {
    val pictureElement = document.createElement("div") as HTMLElement
    pictureElement.setAttribute(
        "style",
        "position: relative; border: 1px solid #ddd; border-radius: 8px; overflow: hidden; background: white;",
    )
    pictureElement.setAttribute("data-picture-id", pictureId)
    pictureElement.setAttribute("data-picture-data", imageSrc)
    if (r2Media) {
        pictureElement.setAttribute("data-r2-media", "true")
        if (purchaseId != null) {
            pictureElement.setAttribute("data-r2-purchase-id", purchaseId.toString())
        }
    }

    val img = document.createElement("img")
    img.setAttribute("src", imageSrc)
    img.setAttribute("style", "width: 100%; height: 150px; object-fit: cover;")

    val deleteBtn = document.createElement("button")
    deleteBtn.textContent = "✕"
    deleteBtn.setAttribute(
        "style",
        "position: absolute; top: 5px; right: 5px; background: rgba(255,0,0,0.8); color: white; border: none; border-radius: 50%; width: 25px; height: 25px; cursor: pointer; font-size: 12px;",
    )
    deleteBtn.addEventListener("click", { _: dynamic ->
        if (r2Media && purchaseId != null) {
            val mediaId = pictureId.toLongOrNull()
            if (mediaId != null) {
                window.fetch(apiUrl("purchases/$purchaseId/media/$mediaId"), js("({ method: 'DELETE' })"))
                    .catch { e: dynamic -> console.error("Failed to delete R2 media:", e) }
            }
        }
        pictureElement.remove()
        onRemove?.invoke()
    })

    pictureElement.appendChild(img)
    pictureElement.appendChild(deleteBtn)
    container.appendChild(pictureElement)
}

fun renderR2CarPictureItems(items: dynamic, containerId: String, purchaseId: Long) {
    val container = document.getElementById(containerId) as? HTMLElement ?: return
    container.innerHTML = ""
    if (!js("Array.isArray(items)").unsafeCast<Boolean>()) return
    val count = js("items.length").unsafeCast<Int>()
    for (i in 0 until count) {
        val item = js("items[i]")
        val id = js("String(item.id)").toString()
        val url = js("String(item.url || '')").toString()
        if (url.isEmpty()) continue
        appendCarPicturePreview(container, id, url, r2Media = true, purchaseId = purchaseId)
    }
}

fun loadCarPicturesWithR2Fallback(
    purchaseData: dynamic,
    containerId: String,
    legacyLoader: () -> Unit,
) {
    val purchaseId = (purchaseData.id as? Number)?.toLong()
    if (purchaseId == null) {
        legacyLoader()
        return
    }
    ensureCarPictureMediaConfig { enabled ->
        if (!enabled) {
            legacyLoader()
            return@ensureCarPictureMediaConfig
        }
        window.fetch(apiUrl("purchases/$purchaseId/media"))
            .then { response: dynamic -> if (response.ok) response.json() else js("[]") }
            .then { items: dynamic ->
                val hasItems = js("Array.isArray(items) && items.length > 0").unsafeCast<Boolean>()
                if (hasItems) {
                    renderR2CarPictureItems(items, containerId, purchaseId)
                } else {
                    legacyLoader()
                }
            }
            .catch { _: dynamic -> legacyLoader() }
    }
}

fun handleR2CarPictureUpload(input: HTMLInputElement, purchaseId: Long?) {
    val files = input.files ?: return
    if (files.length == 0) return

    val previewId = if (purchaseId != null) "existingPicturesList" else "carPicturePreview"
    val previewDiv = document.getElementById(previewId) as? HTMLElement
    if (previewDiv == null) {
        console.warn("Car picture preview container not found: $previewId")
        return
    }

    for (i in 0 until files.length) {
        val file = files.item(i) as? File ?: continue
        if ((file.type as? String)?.startsWith("image/") != true) continue
        val fileSize = (file.asDynamic().size as? Number)?.toInt() ?: 0
        if (fileSize > cachedMaxFileSizeBytes) {
            showMessage("File ${file.name} exceeds maximum image size", "error")
            continue
        }
        if (purchaseId != null) {
            uploadCarPictureFile(purchaseId, file)
                .then { saved: dynamic ->
                    val id = js("String(saved.id)").toString()
                    val url = js("String(saved.url || '')").toString()
                    if (url.isNotEmpty()) {
                        appendCarPicturePreview(previewDiv, id, url, r2Media = true, purchaseId = purchaseId)
                    }
                }
                .catch { e: dynamic ->
                    showMessage("Failed to upload ${file.name}: ${e.message ?: e}", "error")
                }
        } else {
            pendingCarPictureFiles.add(file)
            val objectUrl = js("URL.createObjectURL(file)") as String
            appendCarPicturePreview(
                previewDiv,
                "pending_${i}_${file.name.hashCode()}",
                objectUrl,
                r2Media = false,
                purchaseId = null,
                onRemove = { pendingCarPictureFiles.remove(file) },
            )
        }
    }
}

fun registerCarPictureMediaBridges() {
    window.asDynamic().handleR2CarPictureUpload = { input: dynamic, purchaseId: dynamic ->
        val htmlInput = input as? HTMLInputElement
        if (htmlInput != null) {
            val id = when (purchaseId) {
                is Number -> purchaseId.toLong()
                is String -> purchaseId.toLongOrNull()
                else -> null
            }
            handleR2CarPictureUpload(htmlInput, id)
            htmlInput.value = ""
        }
    }
    window.asDynamic().isR2CarPictureStorageEnabled = { isR2CarPictureStorageEnabled() }
}
