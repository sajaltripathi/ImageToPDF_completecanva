package com.sajal.imagetopdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.FileNotFoundException

class MainActivity : Activity() {
    private val requestPickImages = 1001
    private val requestCreatePdf = 1002

    private val pages = mutableListOf<ImagePage>()
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var convertButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        renderPages()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Image to PDF"
            textSize = 26f
            setTextColor(Color.rgb(38, 50, 56))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        root.addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val pickButton = Button(this).apply {
            text = "Pick Image(s)"
            setOnClickListener { pickImages() }
        }
        root.addView(pickButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        convertButton = Button(this).apply {
            text = "Convert to PDF"
            setOnClickListener { choosePdfSaveLocation() }
        }
        root.addView(convertButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 16)
        }
        root.addView(statusText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progressBar, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        setContentView(root)
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, requestPickImages)
    }

    private fun choosePdfSaveLocation() {
        if (pages.isEmpty()) {
            Toast.makeText(this, "Please select at least one image.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "ImageToPDF.pdf")
        }
        startActivityForResult(intent, requestCreatePdf)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            requestPickImages -> handlePickedImages(data)
            requestCreatePdf -> data.data?.let { createPdf(it) }
        }
    }

    private fun handlePickedImages(data: Intent) {
        val selected = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) selected.add(clip.getItemAt(i).uri)
        }
        data.data?.let { selected.add(it) }

        selected.distinct().forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers do not allow persistable grants. The app still only uses user-selected URIs.
            }
            pages.add(ImagePage(uri, getDisplayName(uri)))
        }
        renderPages()
    }

    private fun renderPages() {
        listContainer.removeAllViews()
        statusText.text = if (pages.isEmpty()) "No images selected." else "${pages.size} image(s) selected."
        convertButton.isEnabled = pages.isNotEmpty()

        pages.forEachIndexed { index, page ->
            listContainer.addView(createPageRow(index, page))
        }
    }

    private fun createPageRow(index: Int, page: ImagePage): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 14, 0, 14)
        }

        val thumb = ImageView(this).apply {
            setBackgroundColor(Color.rgb(238, 238, 238))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(loadThumbnail(page.uri, 128))
        }
        row.addView(thumb, LinearLayout.LayoutParams(128, 128))

        val text = TextView(this).apply {
            text = "Page ${index + 1}\n${page.name}"
            textSize = 14f
            setTextColor(Color.rgb(33, 33, 33))
            setPadding(20, 0, 20, 0)
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val up = Button(this).apply {
            text = "↑"
            isEnabled = index > 0
            setOnClickListener {
                pages.swap(index, index - 1)
                renderPages()
            }
        }
        val down = Button(this).apply {
            text = "↓"
            isEnabled = index < pages.lastIndex
            setOnClickListener {
                pages.swap(index, index + 1)
                renderPages()
            }
        }
        val remove = Button(this).apply {
            text = "Remove"
            setOnClickListener {
                pages.removeAt(index)
                renderPages()
            }
        }
        controls.addView(up)
        controls.addView(down)
        controls.addView(remove)
        row.addView(controls)
        return row
    }

    private fun createPdf(destination: Uri) {
        setBusy(true)
        try {
            val pdf = PdfDocument()
            pages.forEachIndexed { index, page ->
                val bitmap = loadFullBitmap(page.uri)
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val pdfPage = pdf.startPage(pageInfo)
                drawBitmapOriginalSize(pdfPage.canvas, bitmap)
                pdf.finishPage(pdfPage)
                bitmap.recycle()
            }

            contentResolver.openOutputStream(destination, "w")?.use { out ->
                pdf.writeTo(out)
            } ?: throw FileNotFoundException("Unable to open output PDF.")
            pdf.close()
            Toast.makeText(this, "PDF saved successfully.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF creation failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            setBusy(false)
        }
    }

    private fun drawBitmapOriginalSize(canvas: Canvas, bitmap: Bitmap) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private fun loadFullBitmap(uri: Uri): Bitmap {
        contentResolver.openInputStream(uri).use { input ->
            return BitmapFactory.decodeStream(input)
                ?: throw IllegalArgumentException("Unsupported or corrupted image.")
        }
    }

    private fun loadThumbnail(uri: Uri, maxSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxSize, maxSize)
        options.inJustDecodeBounds = false
        return contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "Selected image"
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        convertButton.isEnabled = !busy && pages.isNotEmpty()
    }

    private data class ImagePage(val uri: Uri, val name: String)

    private fun <T> MutableList<T>.swap(i: Int, j: Int) {
        val temp = this[i]
        this[i] = this[j]
        this[j] = temp
    }
}
