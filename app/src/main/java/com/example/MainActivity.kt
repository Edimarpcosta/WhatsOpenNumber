package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.database.AppDatabase
import com.example.data.database.DatabaseRepository
import com.example.ui.screens.ConversaMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ConversaViewModel
import com.example.ui.viewmodel.ConversaViewModelFactory
import com.example.utils.PhoneUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var viewModel: ConversaViewModel? = null
    private var shouldFinishOnStop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Local Room database
        val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = DatabaseRepository(database.contactHistoryDao(), database.messageTemplateDao())

        // ViewModel instantiation via factory
        val factory = ConversaViewModelFactory(application, repository)
        val vm = ViewModelProvider(this, factory)[ConversaViewModel::class.java]
        viewModel = vm

        // Process incoming shared data on launch
        handleSharedIntent(intent)

        setContent {
            MyApplicationTheme {
                ConversaMainScreen(viewModel = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotBlank()) {
                viewModel?.let { vm ->
                    // Set both standard number and AI input so they can choose
                    val cleanedPhone = PhoneUtils.extractPhoneNumber(sharedText)
                    if (cleanedPhone != null) {
                        vm.setPhoneNumber(PhoneUtils.cleanPhoneNumber(cleanedPhone))
                    }
                    vm.setAiInputText(sharedText)
                    // Trigger a check for newer clipboards if needed
                    vm.checkContextClipboard()
                }
            }
        } else if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
            if (selectedText.isNotBlank()) {
                viewModel?.let { vm ->
                    val cleanedPhone = PhoneUtils.extractPhoneNumber(selectedText)
                    if (cleanedPhone != null) {
                        val finalNum = PhoneUtils.cleanPhoneNumber(cleanedPhone)
                        vm.setPhoneNumber(finalNum)
                        
                        // Finish MainActivity once the WhatsApp target of choice is launched or cancelled
                        vm.onWhatsAppLaunchedCallback = {
                            shouldFinishOnStop = true
                            lifecycleScope.launch {
                                delay(1200)
                                if (shouldFinishOnStop && !isFinishing) {
                                    finish()
                                }
                            }
                        }
                        vm.onWhatsAppCancelledCallback = {
                            finish()
                        }
                        
                        // Instantly initiate the flow (checks Business vs Normal)
                        vm.startWhatsAppFlow(this, finalNum, "")
                    } else {
                        vm.setAiInputText(selectedText)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (shouldFinishOnStop) {
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Automatically scan clipboard for phone numbers only when app has target window focus
            viewModel?.checkContextClipboard()
        }
    }
}
