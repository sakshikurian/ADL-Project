package com.ecotracker.ui.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.data.LoggableActivity
import com.ecotracker.databinding.ActivityInteractiveMediaBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.util.Locale

class InteractiveMediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInteractiveMediaBinding
    private lateinit var repository: LocalEcoRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var capturedBitmap: Bitmap? = null

    // Audio recording & playback
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private lateinit var audioFile: File

    // Permissions
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePhotoLauncher.launch(null)
        } else {
            Toast.makeText(this, "Camera permission needed to capture photo proof", Toast.LENGTH_SHORT).show()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleAudioRecording()
        } else {
            Toast.makeText(this, "Microphone permission needed to record audio note", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchGpsCoordinates()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            binding.ivPreviewPhoto.setImageBitmap(bitmap)
            binding.layoutPhotoPlaceholder.visibility = View.GONE
            Toast.makeText(this, "Eco photo proof captured! 📸", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            binding.ivPreviewPhoto.setImageURI(uri)
            binding.layoutPhotoPlaceholder.visibility = View.GONE
            Toast.makeText(this, "Eco photo selected from gallery! 🖼️", Toast.LENGTH_SHORT).show()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                binding.viewNetworkDot.backgroundTintList = ContextCompat.getColorStateList(this@InteractiveMediaActivity, R.color.fresh_green)
                binding.tvNetworkStatus.text = getString(R.string.media_network_online)
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                binding.viewNetworkDot.backgroundTintList = ContextCompat.getColorStateList(this@InteractiveMediaActivity, R.color.accent_amber)
                binding.tvNetworkStatus.text = getString(R.string.media_network_offline)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInteractiveMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocalEcoRepository(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        audioFile = File(cacheDir, "eco_audio_note.m4a")

        setupListeners()
        setupNetworkMonitoring()
        fetchGpsCoordinates()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhotoLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnPickGallery.setOnClickListener {
            pickGalleryLauncher.launch("image/*")
        }

        binding.btnRefreshGps.setOnClickListener {
            fetchGpsCoordinates()
        }

        binding.btnRecordAudio.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toggleAudioRecording()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.btnPlayAudio.setOnClickListener {
            toggleAudioPlayback()
        }

        binding.btnSubmitVerifiedAction.setOnClickListener {
            submitVerifiedAction()
        }
    }

    private fun setupNetworkMonitoring() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isOnline) {
            binding.viewNetworkDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.fresh_green)
            binding.tvNetworkStatus.text = getString(R.string.media_network_online)
        } else {
            binding.viewNetworkDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_amber)
            binding.tvNetworkStatus.text = getString(R.string.media_network_offline)
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun fetchGpsCoordinates() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            binding.tvGpsCoordinates.text = "Acquiring GPS satellite fix…"
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        binding.tvGpsCoordinates.text = String.format(
                            Locale.getDefault(),
                            "%.4f° N, %.4f° E (±%.1fm accuracy)",
                            location.latitude,
                            location.longitude,
                            location.accuracy
                        )
                    } else {
                        // Campus fallback coordinates
                        currentLatitude = 12.9716
                        currentLongitude = 77.5946
                        binding.tvGpsCoordinates.text = "12.9716° N, 77.5946° E (Campus Reference)"
                    }
                }
            } catch (e: SecurityException) {
                binding.tvGpsCoordinates.text = "GPS Geotag: Location permission required"
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun toggleAudioRecording() {
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            binding.btnRecordAudio.text = getString(R.string.media_stop_audio)
            binding.btnRecordAudio.strokeColor = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            binding.tvAudioStatus.text = "🔴 Recording voice note… Tap Stop when done"
            binding.btnPlayAudio.isEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            binding.btnRecordAudio.text = getString(R.string.media_record_audio)
            binding.btnRecordAudio.strokeColor = ContextCompat.getColorStateList(this, R.color.primary_green)
            binding.tvAudioStatus.text = "✅ Audio note recorded (${audioFile.length() / 1024} KB). Ready to play!"
            binding.btnPlayAudio.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAudioPlayback() {
        if (!isPlaying) {
            startPlayback()
        } else {
            stopPlayback()
        }
    }

    private fun startPlayback() {
        if (!audioFile.exists()) {
            Toast.makeText(this, "No recorded audio note found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPlayback()
                }
            }
            isPlaying = true
            binding.btnPlayAudio.text = "Stop Audio"
            binding.tvAudioStatus.text = "▶️ Playing recorded voice observation…"
        } catch (e: Exception) {
            Toast.makeText(this, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            binding.btnPlayAudio.text = getString(R.string.media_play_audio)
            binding.tvAudioStatus.text = "⏹️ Playback complete"
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    private fun submitVerifiedAction() {
        val title = binding.etActionTitle.text.toString().trim().ifBlank {
            "Verified Eco Action (GPS & Photo Attached)"
        }

        val geoTag = if (currentLatitude != null && currentLongitude != null) {
            String.format(Locale.getDefault(), " [📍 %.4f, %.4f]", currentLatitude, currentLongitude)
        } else ""

        val finalTitle = "$title$geoTag"

        val activity = LoggableActivity(
            label = finalTitle,
            points = 25,
            co2Kg = 0.5,
            waterLitres = 10
        )
        repository.logActivity(activity)

        Toast.makeText(this, "Verified Eco Action Logged! +25 Points 🌱", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {}
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}
