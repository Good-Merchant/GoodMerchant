package com.example.goodmerchant

import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.example.goodmerchant.Imgur.ImgurApi
import com.example.goodmerchant.Imgur.ImgurUploadJson
import com.example.goodmerchant.Imgur.imagetoUrl
import com.example.goodmerchant.Recyclerview.ListFragmentDirections
import com.example.goodmerchant.Retrofit.*
import com.example.goodmerchant.ViewModel.productViewmodel
import com.example.goodmerchant.databinding.FragmentMainBinding
import com.google.firebase.storage.FirebaseStorage
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.android.synthetic.main.splash.*
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.RequestBody
import org.json.JSONObject
import org.json.JSONTokener
import java.io.*
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection


class MainFragment : Fragment() {

    private var bitmap: Bitmap? = null
    var c = 1
    var ch = 1

    private lateinit var viewModel: productViewmodel
    lateinit var imageUri: Uri
    lateinit var binding: FragmentMainBinding
    lateinit var imageLink: URL
    lateinit var imageTag: String
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(productViewmodel::class.java)
        binding = FragmentMainBinding.inflate(layoutInflater, container, false)

        //manual search

        var temp: Boolean = false
        binding.searchicon.setOnClickListener {
            if (binding.searchtext.text != null) {
                imageTag = ""
                imageTag = binding.searchtext.text.toString()

                if(imageTag != "")
                fillListfragment(imageTag)
                else{
                    Toast.makeText(
                        requireActivity(),
                        "Retry",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        //switch
        binding.switch1.setOnCheckedChangeListener { _, isChecked ->
            c = if (isChecked) 2
            else 1
        }

        binding.camera.setOnClickListener {
                ch = 1
                pickImagecam()

        }
        binding.gallery.setOnClickListener {
                ch = 2
                pickImage()

        }

        return binding.root
    }

    private fun pickImagecam() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(takePictureIntent, 110)
    }

    private fun pickImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), 110)
    }

    private fun slectimage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, 100)
    }

    /* private fun uploadimage() {
        val progressBar = ProgressDialog(context)
        progressBar.setMessage("Analizing")
        progressBar.setCancelable(false)
        progressBar.show()

        val formatter = SimpleDateFormat("yyyy__MM__dd__HH__mm__ss", Locale.getDefault())
        val now = Date()
        val filename = formatter.format(now)
        val storageReference = FirebaseStorage.getInstance().getReference("images/$filename")
        storageReference.putFile(imageUri).addOnSuccessListener {
            storageReference.downloadUrl.addOnSuccessListener {
                if (progressBar.isShowing) progressBar.dismiss()
                imageLink = URL(it.toString())
                Log.d("%%%%%", imageLink.toString())
                getTags()

            }.addOnFailureListener {
                if (progressBar.isShowing) progressBar.dismiss()
                Log.d("%%%%%", "Failed", it)
            }
        }.addOnFailureListener {
            if (progressBar.isShowing) progressBar.dismiss()
            Log.d("%%%%%", "Failed", it)
        }

    }    */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 110 && resultCode == RESULT_OK) {
            //  imageUri = data?.data!!
            //  uploadimage()
            if (ch == 1) {
                bitmap = null
                bitmap = data?.extras?.get("data") as Bitmap
            }

            else if(ch == 2) {
                data?.data?.let {
                    bitmap = null
                    bitmap = getBitmapFromUri(it);
                }

            }

            if(c==2) {
            val recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            bitmap?.let {
                val image = InputImage.fromBitmap(it, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        imageTag = ""
                        imageTag = visionText.text

                        if(imageTag != "")
                            fillListfragment(imageTag)
                        else{
                            Toast.makeText(
                                requireActivity(),
                                "Retry",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    /*  Toast.makeText(
                            requireActivity(),
                            visionText.text,
                            Toast.LENGTH_LONG
                        ).show() */
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireActivity(),
                            "Error: " + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            if(c==1) {
                bitmap?.let {

                    val image = InputImage.fromBitmap(it, 0)

                    val localModel = LocalModel.Builder()
                        .setAssetFilePath("mnasnet_1.3_224_1_metadata_1.tflite")
                        // or .setAbsoluteFilePath(absolute file path to model file)
                        // or .setUri(URI to model file)
                        .build()

                    val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
                        .setConfidenceThreshold(0.5f)
                        .setMaxResultCount(5)
                        .build()
                    val labeler = ImageLabeling.getClient(customImageLabelerOptions)

                    labeler.process(image)
                        .addOnSuccessListener { labels ->
                            //for (label in labels) {} - when multiple labels needed
                             imageTag = ""
                             imageTag = labels[0].text

                            if(imageTag != "")
                                fillListfragment(imageTag)
                            else{
                                Toast.makeText(
                                    requireActivity(),
                                    "Retry",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                         /*   Toast.makeText(
                                requireActivity(),
                                labels[0].text,
                                Toast.LENGTH_LONG
                            ).show() */
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireActivity(),
                                "Error: " + e.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }


            if (bitmap == null)
                Toast.makeText(
                    requireActivity(),
                    "Please select an image!",
                    Toast.LENGTH_SHORT
                ).show()
        }
    }

    //URI to bitmap
    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        val parcelFileDescriptor = activity?.contentResolver?.openFileDescriptor(uri, "r")
        val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()
        return image
    }

    fun getTags() {
        val tag = tagservices.tagInstance.getTag(imageLink.toString())
        tag.enqueue(object : Callback<imagetagResult> {
            override fun onResponse(
                call: Call<imagetagResult>,
                response: Response<imagetagResult>
            ) {
                val currenttag: imagetagResult? = response.body()
                if (currenttag != null) {
                    val tagDetail: String = currenttag.searchInformation!!.query_displayed
                    imageTag = tagDetail
                    fillListfragment(imageTag)

                    Log.d("%%%%%", tagDetail)
                }
            }

            override fun onFailure(call: Call<imagetagResult>, t: Throwable) {
                Log.d("%%%%%", "Failed sharam ati hai?", t)
            }
        })
    }

    fun fillListfragment(tag: String) {

        viewModel.repository.getProducts(tag)
        binding.progressBarMain.visibility = View.VISIBLE
        binding.frontscreen.visibility = View.GONE
        Handler().postDelayed({
            val productDetailList: Array<productModal> = viewModel.repository.getproductsfromlist()
            val directions =
                MainFragmentDirections.actionMainFragmentToListFragment(productDetailList)
            findNavController().navigate(directions)
            binding.progressBarMain.visibility = View.GONE
            binding.frontscreen.visibility = View.VISIBLE
        }, 10000)
    }
}









