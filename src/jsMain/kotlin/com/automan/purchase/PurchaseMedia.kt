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

private val allowedCarPictureExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tif", "tiff",
)

internal const val CAR_PICTURE_FILE_ACCEPT =
    "image/*,.heic,.heif,.bmp,.tif,.tiff,.gif,.webp,.jpg,.jpeg,.png"

internal fun isAllowedCarPictureFile(file: File): Boolean {
    val name = (file.name as? String).orEmpty().lowercase()
    val ext = name.substringAfterLast('.', missingDelimiterValue = "")
    if (ext == "svg") return false
    val type = (file.type as? String)?.trim()?.lowercase().orEmpty()
    if (type == "image/svg+xml" || type == "image/svg") return false
    if (type.startsWith("image/")) return true
    return ext in allowedCarPictureExtensions
}

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
    allowDelete: Boolean = true,
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

    pictureElement.appendChild(img)
    if (allowDelete) {
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
        pictureElement.appendChild(deleteBtn)
    }
    container.appendChild(pictureElement)
}

fun renderR2CarPictureItems(
    items: dynamic,
    containerId: String,
    purchaseId: Long,
    readOnly: Boolean = false,
) {
    val container = document.getElementById(containerId) as? HTMLElement ?: return
    container.innerHTML = ""
    if (!js("Array.isArray(items)").unsafeCast<Boolean>()) return
    val count = js("items.length").unsafeCast<Int>()
    for (i in 0 until count) {
        val item = js("items[i]")
        val id = js("String(item.id)").toString()
        val url = js("String(item.url || '')").toString()
        if (url.isEmpty()) continue
        appendCarPicturePreview(
            container,
            id,
            url,
            r2Media = true,
            purchaseId = purchaseId,
            allowDelete = !readOnly,
        )
    }
    if (!readOnly) {
        notifyCarPicturesDomUpdated()
    }
}

fun loadCarPicturesWithR2Fallback(
    purchaseData: dynamic,
    containerId: String,
    readOnly: Boolean = false,
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
                    renderR2CarPictureItems(items, containerId, purchaseId, readOnly = readOnly)
                } else {
                    legacyLoader()
                }
            }
            .catch { _: dynamic -> legacyLoader() }
    }
}

private fun vehicleSummaryPicturesEmptyHtml(): String =
    """<div style="grid-column:1/-1;padding:12px 14px;border:1px dashed #d1d5db;border-radius:8px;background:#f9fafb;color:#6b7280;font-size:13px;">No car pictures saved for this vehicle.</div>"""

private fun vehicleSummaryPicturesLoadingHtml(): String =
    """<div style="grid-column:1/-1;padding:12px 14px;color:#6b7280;font-size:13px;">Loading pictures…</div>"""

private fun carPicturesRawFromPurchase(p: dynamic): dynamic {
    if (p == null || p == js("undefined")) return null
    val camel = js("p.carPictures")
    if (camel != null && camel != js("undefined")) {
        val camelEmptyStr = js("typeof camel === 'string' && String(camel).trim() === ''").unsafeCast<Boolean>()
        if (!camelEmptyStr) return camel
    }
    return js("p.car_pictures")
}

/** Parse legacy carPictures JSON into image sources; returns how many thumbnails were appended. */
private fun renderLegacyCarPicturesReadOnly(purchaseData: dynamic, containerId: String): Int {
    val container = document.getElementById(containerId) as? HTMLElement ?: return 0
    val carPicturesRaw = carPicturesRawFromPurchase(purchaseData)
    if (carPicturesRaw == null || carPicturesRaw == js("undefined")) return 0

    val carPictures: dynamic = try {
        when {
            js("Array.isArray(carPicturesRaw)").unsafeCast<Boolean>() -> carPicturesRaw
            carPicturesRaw is String -> {
                val s = js("String(carPicturesRaw).trim()").toString()
                if (s.isEmpty()) return 0
                var parsed = JSON.parse<dynamic>(s)
                if (js("typeof parsed === 'string'").unsafeCast<Boolean>()) {
                    val inner = js("String(parsed).trim()").toString()
                    if (inner.isNotEmpty()) parsed = JSON.parse<dynamic>(inner)
                }
                parsed
            }
            else -> return 0
        }
    } catch (_: Throwable) {
        return 0
    }

    if (!js("Array.isArray(carPictures)").unsafeCast<Boolean>()) return 0
    val count = js("carPictures.length").unsafeCast<Int>()
    if (count <= 0) return 0

    container.innerHTML = ""
    var rendered = 0
    for (i in 0 until count) {
        val picture = js("carPictures[i]")
        val pictureId = js("picture.id !== undefined && picture.id !== null ? String(picture.id) : ''").toString()
        var pictureData = ""
        if (js("typeof picture === 'string'").unsafeCast<Boolean>()) {
            pictureData = picture.toString()
        } else {
            pictureData = js("picture.data != null ? String(picture.data) : ''").toString()
            if (pictureData.isEmpty()) pictureData = js("picture.src != null ? String(picture.src) : ''").toString()
            if (pictureData.isEmpty()) pictureData = js("picture.url != null ? String(picture.url) : ''").toString()
            if (pictureData.isEmpty()) pictureData = js("picture.image != null ? String(picture.image) : ''").toString()
        }
        if (pictureData.isEmpty()) continue
        appendCarPicturePreview(
            container,
            if (pictureId.isEmpty()) "legacy_$i" else pictureId,
            pictureData,
            r2Media = false,
            purchaseId = null,
            allowDelete = false,
        )
        rendered++
    }
    return rendered
}

/**
 * Read-only Car Pictures for Vehicle Summary (Purchase List eye icon).
 * Uses R2 media when enabled; falls back to legacy carPictures (fetching full purchase if needed).
 */
fun loadVehicleSummaryCarPictures(purchaseData: dynamic, containerId: String) {
    val container = document.getElementById(containerId) as? HTMLElement ?: return
    container.innerHTML = vehicleSummaryPicturesLoadingHtml()

    val purchaseId = (purchaseData.id as? Number)?.toLong()
    val finishEmpty = {
        val el = document.getElementById(containerId) as? HTMLElement
        if (el != null) el.innerHTML = vehicleSummaryPicturesEmptyHtml()
    }
    val tryLegacyFrom = { data: dynamic ->
        val n = renderLegacyCarPicturesReadOnly(data, containerId)
        if (n <= 0) finishEmpty()
    }

    loadCarPicturesWithR2Fallback(purchaseData, containerId, readOnly = true) {
        val localCount = renderLegacyCarPicturesReadOnly(purchaseData, containerId)
        if (localCount > 0) return@loadCarPicturesWithR2Fallback
        if (purchaseId == null || purchaseId <= 0L) {
            finishEmpty()
            return@loadCarPicturesWithR2Fallback
        }
        // List-row cache often omits full carPictures JSON — fetch once for legacy fallback.
        window.fetch(apiUrl("purchases/purchase/$purchaseId"))
            .then { response: dynamic -> if (response.ok) response.json() else null }
            .then { full: dynamic ->
                if (full == null || full == js("undefined")) {
                    finishEmpty()
                } else {
                    tryLegacyFrom(full)
                }
            }
            .catch { _: dynamic -> finishEmpty() }
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
        if (!isAllowedCarPictureFile(file)) {
            showMessage("File ${file.name} is not a supported image type", "error")
            continue
        }
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

/**
 * Global entry used by `onchange="handleCarPictureUpload(this)"` on Quick Purchase, Add, and Edit.
 * Registered at app init so Quick Purchase works without opening the Add form first
 * (the Add form used to define this only inside its inline script).
 */
fun handleCarPictureUpload(input: HTMLInputElement) {
    val files = input.files ?: return
    if (files.length == 0) return

    val editIdEl = document.getElementById("editId") as? HTMLInputElement
    val purchaseId = editIdEl?.value?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    ensureCarPictureMediaConfig { enabled ->
        if (enabled) {
            handleR2CarPictureUpload(input, purchaseId)
        } else {
            handleLegacyCarPictureUpload(input)
        }
        input.value = ""
    }
}

/** Legacy base64 preview path when R2 media is disabled (same contract as the old inline JS). */
private fun handleLegacyCarPictureUpload(input: HTMLInputElement) {
    val files = input.files ?: return
    if (files.length == 0) return

    val previewDiv = document.getElementById("carPicturePreview") as? HTMLElement
    if (previewDiv == null) {
        console.warn("Car picture preview container not found: carPicturePreview")
        return
    }

    val progressDiv = document.getElementById("uploadProgress") as? HTMLElement
    val progressBar = document.getElementById("progressBar") as? HTMLElement
    val progressText = document.getElementById("progressText") as? HTMLElement
    progressDiv?.style?.display = "block"
    progressBar?.style?.width = "0%"
    progressText?.textContent = "Preparing upload..."

    var uploadedCount = 0
    var scheduledCount = 0
    for (i in 0 until files.length) {
        val file = files.item(i) as? File ?: continue
        if (!isAllowedCarPictureFile(file)) {
            showMessage("File ${file.name} is not a supported image type", "error")
            continue
        }
        val fileSize = (file.asDynamic().size as? Number)?.toInt() ?: 0
        if (fileSize > cachedMaxFileSizeBytes) {
            showMessage("File ${file.name} exceeds maximum image size", "error")
            continue
        }
        scheduledCount++
        val index = i
        val reader = js("new FileReader()")
        reader.onload = { event: dynamic ->
            val result = js("event && event.target && event.target.result")?.toString()
            if (!result.isNullOrBlank()) {
                val pictureId =
                    "pic_${js("Date.now()")}_${js("Math.random().toString(36).substr(2, 9)")}"
                appendCarPicturePreview(
                    previewDiv,
                    pictureId,
                    result,
                    r2Media = false,
                    purchaseId = null,
                )
                window.setTimeout({
                    uploadedCount++
                    val total = if (scheduledCount > 0) scheduledCount else 1
                    val progress = (uploadedCount.toDouble() / total.toDouble()) * 100.0
                    progressBar?.style?.width = "${progress}%"
                    progressText?.textContent = "Uploaded $uploadedCount/$total pictures"
                    if (uploadedCount >= scheduledCount) {
                        window.setTimeout({
                            progressDiv?.style?.display = "none"
                            progressText?.textContent = "All pictures uploaded successfully!"
                        }, 1000)
                    }
                }, 1000 + (index * 500))
            }
        }
        reader.readAsDataURL(file)
    }
    if (scheduledCount == 0) {
        progressDiv?.style?.display = "none"
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
    // Must exist before any form mounts — Quick Purchase calls this via inline onchange.
    window.asDynamic().handleCarPictureUpload = { input: dynamic ->
        val htmlInput = input as? HTMLInputElement
        if (htmlInput != null) {
            handleCarPictureUpload(htmlInput)
        }
    }
    window.asDynamic().isR2CarPictureStorageEnabled = { isR2CarPictureStorageEnabled() }
    window.asDynamic().openCarPictureLightbox = { imageSrc: dynamic ->
        openCarPictureLightbox(imageSrc?.toString() ?: "")
    }
}
