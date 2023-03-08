package com.example.myapplication

import TesseractOCR
import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.myapplication.databinding.ActivityMainBinding
import com.vmadalin.easypermissions.EasyPermissions
import kotlinx.coroutines.CoroutineScope
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQUEST_CODE_CAMERA_AND_MEDIA_PERMISSION = 123
        const val REQUEST_IMAGE_CAPTURE = 321
    }

    var listData: MutableList<ResultScanModel> = ArrayList()
    var mCurrentPhotoPath = ""
    var oldPhotoURI: Uri? = null
    var photoURI1: Uri? = null
    private fun getMediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            btnAdd.setOnClickListener {
                if (EasyPermissions.hasPermissions(
                        this@MainActivity,
                        Manifest.permission.CAMERA,
                        getMediaPermission()
                    )
                ) {
                    openCamera()
                } else {
                    EasyPermissions.requestPermissions(
                        this@MainActivity,
                        rationale = "rasionalisasi",
                        requestCode = REQUEST_CODE_CAMERA_AND_MEDIA_PERMISSION,
                        Manifest.permission.CAMERA, getMediaPermission()
                    )
                }
            }
            pbOcr.visibility = View.GONE
        }
    }

    private fun openCamera() {
        var takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(applicationContext.packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this@MainActivity, "error createImageFile", Toast.LENGTH_SHORT)
                    .show()
                Log.i("File error", ex.toString())
            }
            if (photoFile != null) {
                oldPhotoURI = photoURI1
                photoURI1 = FileProvider.getUriForFile(this@MainActivity,"com.example.myapplication.mycontentprovider",photoFile)
//                photoURI1 = Uri.fromFile(photoFile);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                takePictureIntent.clipData = ClipData.newUri(applicationContext.contentResolver, "Image", photoURI1)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI1)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // EasyPermissions handles the request result.
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        openCamera()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        Toast.makeText(
            this@MainActivity,
            "You must accept permission for using this feature",
            Toast.LENGTH_SHORT
        )
    }

    @Throws(IOException::class)
    fun createImageFile(): File? {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("MMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File? =
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null) {
            val image: File = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",  /* suffix */
                storageDir /* directory */
            )
            // Save a file: path for use with ACTION_VIEW intents
            mCurrentPhotoPath = image.absolutePath
            return image
        }
        return null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    var bmp: Bitmap? = null
                    try {
                        val inputStream: InputStream? =
                            photoURI1?.let { applicationContext.contentResolver.openInputStream(it) }
                                ?: null
                        val options: BitmapFactory.Options = BitmapFactory.Options()
                        bmp = BitmapFactory.decodeStream(inputStream, null, options)
                    } catch (ex: Exception) {
                        Log.i(javaClass.simpleName, ex.message!!)
                        Toast.makeText(this@MainActivity, "error InputStream", Toast.LENGTH_SHORT)
                            .show()
                    }

//                    firstImage.setImageBitmap(bmp)
                    if (bmp != null) {
                        doOCR(bmp)
                        val os: OutputStream
                        try {
                            os = FileOutputStream(photoURI1!!.path)
                            bmp.compress(Bitmap.CompressFormat.JPEG, 100, os)
                            os.flush()
                            os.close()
                        } catch (ex: Exception) {
                            Log.e(javaClass.simpleName, ex.message!!)
                            Toast.makeText(this@MainActivity, "error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun doOCR(bitmap: Bitmap) {
        binding.pbOcr.visibility = View.VISIBLE
        Thread {
            val tesseractOCR = TesseractOCR(this@MainActivity,"")
            val srcText: String? = tesseractOCR.getOCRResult(bitmap)
            runOnUiThread {
                if (srcText != null && srcText != "") {
                    listData.add(ResultScanModel(input = srcText))
                }
                binding.pbOcr.visibility = View.GONE
            }
        }.start()
    }
}