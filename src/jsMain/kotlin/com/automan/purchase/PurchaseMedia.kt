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

fun collectCarPicturesForCompare(): dynamic {
    val pictures = js("[]")
    val containerIds = listOf("carPicturePreview", "existingPicturesList")
    for (containerId in containerIds) {
        val previewDiv = document.getElementById(containerId) ?: continue
        val pictureElements = previewDiv.querySelectorAll("div[data-picture-id]")
        for (i in 0 until pictureElements.length) {
            val element = pictureElements.item(i) as HTMLElement
            val pictureId = element.getAttribute("data-picture-id") ?: continue
            val pictureData = element.getAttribute("data-picture-data") ?: continue
            val pictureObj = js("{}")
            pictureObj.id = pictureId
            pictureObj.data = pictureData
            pictures.push(pictureObj)
        }
    }
    return pictures
}

private fun notifyCarPicturesDomUpdated() {
    val sync = window.asDynamic().syncEditCarPicturesBaselineFromDom
    if (sync != null && js("typeof sync === 'function'").unsafeCast<Boolean>()) {
        sync()
    }
}

private const val CAR_PICTURE_LIGHTBOX_ID = "carPictureLightbox"
private var carPictureLightboxKeyHandler: ((dynamic) -> Unit)? = null

fun openCarPictureLightbox(imageSrc: String) {
    if (imageSrc.isBlank()) return
    closeCarPictureLightbox()

    val overlay = document.createElement("div") as HTMLElement
    overlay.id = CAR_PICTURE_LIGHTBOX_ID
    overlay.setAttribute(
        "style",
        "position: fixed; inset: 0; z-index: 20000; background: rgba(0,0,0,0.92); " +
            "display: flex; align-items: center; justify-content: center; padding: 24px; box-sizing: border-box;",
    )
    overlay.setAttribute("role", "dialog")
    overlay.setAttribute("aria-modal", "true")
    overlay.setAttribute("aria-label", "Car picture")

    val img = document.createElement("img") as HTMLElement
    img.setAttribute("src", imageSrc)
    img.setAttribute("alt", "Car picture")
    img.setAttribute(
        "style",
        "max-width: 100%; max-height: 100%; width: auto; height: auto; object-fit: contain; " +
            "border-radius: 4px; user-select: none; pointer-events: none;",
    )

    val closeBtn = document.createElement("button") as HTMLElement
    closeBtn.textContent = "✕"
    closeBtn.setAttribute("type", "button")
    closeBtn.setAttribute("aria-label", "Close")
    closeBtn.setAttribute(
        "style",
        "position: absolute; top: 16px; right: 16px; width: 40px; height: 40px; border: none; " +
            "border-radius: 50%; background: rgba(255,255,255,0.15); color: #fff; font-size: 22px; " +
            "line-height: 1; cursor: pointer; z-index: 1;",
    )

    val onKeyDown: (dynamic) -> Unit = { event: dynamic ->
        if (event.key == "Escape" || event.keyCode == 27) {
            closeCarPictureLightbox()
        }
    }
    carPictureLightboxKeyHandler = onKeyDown

    closeBtn.addEventListener("click", { event: dynamic ->
        event.stopPropagation()
        closeCarPictureLightbox()
    })
    overlay.addEventListener("click", { _: dynamic -> closeCarPictureLightbox() })
    document.addEventListener("keydown", onKeyDown)

    overlay.appendChild(img)
    overlay.appendChild(closeBtn)
    document.body?.appendChild(overlay)
}

fun closeCarPictureLightbox() {
    carPictureLightboxKeyHandler?.let { handler ->
        document.removeEventListener("keydown", handler)
        carPictureLightboxKeyHandler = null
    }
    document.getElementById(CAR_PICTURE_LIGHTBOX_ID)?.remove()
}

fun wireCarPictureThumbnailClick(img: HTMLElement, imageSrc: String) {
    img.setAttribute("style", "width: 100%; height: 150px; object-fit: contain; cursor: pointer; display: block;")
    img.setAttribute("title", "Click to view full size")
    img.addEventListener("click", { event: dynamic ->
        event.stopPropagation()
        openCarPictureLightbox(imageSrc)
    })
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

    val img = document.createElement("img") as HTMLElement
    img.setAttribute("src", imageSrc)
    wireCarPictureThumbnailClick(img, imageSrc)

    val deleteBtn = document.createElement("button")
    deleteBtn.textContent = "✕"
    deleteBtn.setAttribute(
        "style",
        "position: absolute; top: 5px; right: 5px; background: rgba(255,0,0,0.8); color: white; border: none; border-radius: 50%; width: 25px; height: 25px; cursor: pointer; font-size: 12px; z-index: 2;",
    )
    deleteBtn.addEventListener("click", { event: dynamic ->
        event.stopPropagation()
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
    notifyCarPicturesDomUpdated()
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
    window.asDynamic().openCarPictureLightbox = { imageSrc: dynamic ->
        openCarPictureLightbox(imageSrc?.toString() ?: "")
    }
}
