package com.opportunitytracker.app.ui.share

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.opportunitytracker.app.OpportunityApp
import com.opportunitytracker.app.R
import com.opportunitytracker.app.data.ocr.ImageOcrHelper
import com.opportunitytracker.app.data.repository.OpportunityRepository
import com.opportunitytracker.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val type = intent?.type

        if (action == Intent.ACTION_SEND) {
            // Case 1: Plain Text / Link share
            if (type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

                if (!sharedText.isNullOrBlank()) {
                    handleSharedContent(sharedText)
                    return
                }
            }
            // Case 2: Image / Screenshot / Poster flyer share
            else if (type?.startsWith("image/") == true) {
                @Suppress("DEPRECATION")
                val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.data

                if (imageUri != null) {
                    handleSharedImage(imageUri)
                    return
                }
            }
        }

        Toast.makeText(this, "No text, link, or image found in share", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleSharedImage(imageUri: Uri) {
        Toast.makeText(this, "Opportunity Tracker: Scanning poster text...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val ocrResult = ImageOcrHelper.extractTextFromImageUri(applicationContext, imageUri)

            withContext(Dispatchers.Main) {
                if (ocrResult.isSuccess) {
                    val extractedText = ocrResult.getOrThrow()
                    handleSharedContent(extractedText)
                } else {
                    val err = ocrResult.exceptionOrNull()?.localizedMessage ?: "Failed to read image"
                    Toast.makeText(applicationContext, "OCR Error: $err", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun handleSharedContent(content: String) {
        Toast.makeText(this, "Opportunity Tracker: Extracting in background...", Toast.LENGTH_SHORT).show()

        val repository = OpportunityRepository(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.processAndSaveOpportunity(content)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val opportunity = result.getOrThrow()
                    showSuccessNotification(opportunity.id, opportunity.title, opportunity.deadlineMillis, opportunity.formUrl)
                    Toast.makeText(
                        applicationContext,
                        "Saved: ${opportunity.title}\nAlarm & calendar reminders scheduled.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    Toast.makeText(
                        applicationContext,
                        "Extraction error: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }
        }
    }

    private fun showSuccessNotification(oppId: Long, title: String, deadlineMillis: Long, formUrl: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dateStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(deadlineMillis))

        // Open App Intent
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            oppId.toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, OpportunityApp.SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Opportunity Saved: $title")
            .setContentText("Due $dateStr • Calendar & Alarm Set")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)

        // Action: Open Application Form if URL is available
        if (formUrl.isNotBlank()) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formUrl))
            val formPendingIntent = PendingIntent.getActivity(
                this,
                oppId.toInt() + 10,
                browserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_launcher_foreground, "Open Form", formPendingIntent)
        }

        notificationManager.notify(oppId.toInt(), builder.build())
    }
}
