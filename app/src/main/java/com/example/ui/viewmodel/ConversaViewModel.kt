package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ContactHistory
import com.example.data.database.DatabaseRepository
import com.example.data.database.MessageTemplate
import com.example.data.gemini.ExtractedContact
import com.example.data.gemini.GeminiExtractor
import com.example.utils.PhoneUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

class ConversaViewModel(
    application: Application,
    private val repository: DatabaseRepository
) : AndroidViewModel(application) {

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _contactName = MutableStateFlow("")
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _selectedTemplateId = MutableStateFlow<Int?>(null)
    val selectedTemplateId: StateFlow<Int?> = _selectedTemplateId.asStateFlow()

    private val _clipboardDetectedNumber = MutableStateFlow<String?>(null)
    val clipboardDetectedNumber: StateFlow<String?> = _clipboardDetectedNumber.asStateFlow()

    // Chooser states for WhatsApp / WhatsApp Business selection
    private val _showWhatsAppChooser = MutableStateFlow(false)
    val showWhatsAppChooser: StateFlow<Boolean> = _showWhatsAppChooser.asStateFlow()

    private val _chooserPhoneNumber = MutableStateFlow("")
    val chooserPhoneNumber: StateFlow<String> = _chooserPhoneNumber.asStateFlow()

    // Callbacks for automatic flow completion
    var onWhatsAppLaunchedCallback: (() -> Unit)? = null
    var onWhatsAppCancelledCallback: (() -> Unit)? = null

    // AI Extraction States
    private val _aiInputText = MutableStateFlow("")
    val aiInputText: StateFlow<String> = _aiInputText.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractedContacts = MutableStateFlow<List<ExtractedContact>>(emptyList())
    val extractedContacts: StateFlow<List<ExtractedContact>> = _extractedContacts.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // Db collections
    val historyList: StateFlow<List<ContactHistory>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val templateList: StateFlow<List<MessageTemplate>> = repository.allTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Initialization without premature clipboard scans
    }

    fun setPhoneNumber(number: String) {
        _phoneNumber.value = number
    }

    fun setContactName(name: String) {
        _contactName.value = name
    }

    fun setMessageText(text: String) {
        _messageText.value = text
        _selectedTemplateId.value = null // reset selection if customized
    }

    fun setAiInputText(text: String) {
        _aiInputText.value = text
    }

    fun selectTemplate(template: MessageTemplate) {
        _selectedTemplateId.value = template.id
        _messageText.value = template.content
    }

    /**
     * Checks the clipboard for potential phone numbers.
     */
    fun checkContextClipboard() {
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val description = clipboard.primaryClipDescription
                if (description != null && (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        val extracted = PhoneUtils.extractPhoneNumber(text)
                        if (extracted != null) {
                            val cleanedExtracted = PhoneUtils.cleanPhoneNumber(extracted)
                            // Make sure we aren't suggesting the current number already entered
                            val currentCleaned = PhoneUtils.cleanPhoneNumber(_phoneNumber.value)
                            if (cleanedExtracted != currentCleaned && cleanedExtracted.length >= 8) {
                                _clipboardDetectedNumber.value = extracted
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ConversaViewModel", "Error checking clipboard safely", e)
        }
    }

    fun useClipboardNumber() {
        _clipboardDetectedNumber.value?.let { num ->
            _phoneNumber.value = num
            _clipboardDetectedNumber.value = null
        }
    }

    fun dismissClipboardSuggestion() {
        _clipboardDetectedNumber.value = null
    }

    /**
     * Process AI extraction of names/numbers.
     */
    fun performAiExtraction() {
        val text = _aiInputText.value
        if (text.isBlank()) return

        _isExtracting.value = true
        _aiError.value = null
        _extractedContacts.value = emptyList()

        viewModelScope.launch {
            try {
                val result = GeminiExtractor.extractContacts(text)
                _extractedContacts.value = result
                if (result.isEmpty()) {
                    // Try simple local regex extraction if AI didn't find anything
                    val localMatch = PhoneUtils.extractPhoneNumber(text)
                    if (localMatch != null) {
                        _extractedContacts.value = listOf(
                            ExtractedContact(
                                name = "Contato Detectado",
                                phone = PhoneUtils.cleanPhoneNumber(localMatch)
                            )
                        )
                    } else {
                        _aiError.value = "Nenhum número de telefone identificado no texto."
                    }
                }
            } catch (e: Exception) {
                // Try fallback logic on error
                val localMatch = PhoneUtils.extractPhoneNumber(text)
                if (localMatch != null) {
                    _extractedContacts.value = listOf(
                        ExtractedContact(
                            name = "Contato (Local Regex)",
                            phone = PhoneUtils.cleanPhoneNumber(localMatch)
                        )
                    )
                } else {
                    _aiError.value = "Erro ao conectar com a Inteligência Artificial. Verifique se a chave de API está configurada."
                }
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun useExtractedContact(contact: ExtractedContact) {
        _phoneNumber.value = contact.phone
        _contactName.value = if (contact.name != "Contato") contact.name else ""
    }

    fun dismissWhatsAppChooser() {
        _showWhatsAppChooser.value = false
        onWhatsAppCancelledCallback?.invoke()
    }

    fun getWhatsAppPackages(context: Context): WhatsAppStatus {
        val hasNormal = isAppInstalled(context, "com.whatsapp")
        val hasBusiness = isAppInstalled(context, "com.whatsapp.w4b")
        return WhatsAppStatus(hasNormal = hasNormal, hasBusiness = hasBusiness)
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Centralized flow to open WhatsApp.
     * Checks if both Normal and Business are installed.
     * If both are, opens our custom in-app chooser dialog.
     * If only one, opens it immediately.
     * If none, falls back to standard wa.me URL.
     */
    fun startWhatsAppFlow(context: Context, targetPhone: String = "", targetName: String = "") {
        val phoneToUse = targetPhone.ifBlank { _phoneNumber.value }
        if (phoneToUse.isBlank()) {
            return
        }

        val cleanedPhone = PhoneUtils.formatForWhatsApp(phoneToUse)
        if (cleanedPhone.isBlank()) {
            return
        }

        val nameToUse = targetName.ifBlank { _contactName.value }
        
        // Save action to local history database
        saveActionToHistory(cleanedPhone, nameToUse, "WHATSAPP")

        val status = getWhatsAppPackages(context)
        if (status.hasNormal && status.hasBusiness) {
            // Both are installed! Show chooser
            _chooserPhoneNumber.value = cleanedPhone
            _showWhatsAppChooser.value = true
        } else if (status.hasNormal) {
            // Only normal is installed, open immediately
            try {
                val intent = getWhatsAppIntentForPackage("com.whatsapp", cleanedPhone, _messageText.value)
                context.startActivity(intent)
                onWhatsAppLaunchedCallback?.invoke()
            } catch (e: Exception) {
                openWhatsAppFallback(context, cleanedPhone)
            }
        } else if (status.hasBusiness) {
            // Only business is installed, open immediately
            try {
                val intent = getWhatsAppIntentForPackage("com.whatsapp.w4b", cleanedPhone, _messageText.value)
                context.startActivity(intent)
                onWhatsAppLaunchedCallback?.invoke()
            } catch (e: Exception) {
                openWhatsAppFallback(context, cleanedPhone)
            }
        } else {
            // Neither is found or restricted, open fallback
            openWhatsAppFallback(context, cleanedPhone)
        }
    }

    fun launchWhatsAppWithPackage(context: Context, packageName: String) {
        val phone = _chooserPhoneNumber.value.ifBlank { PhoneUtils.formatForWhatsApp(_phoneNumber.value) }
        _showWhatsAppChooser.value = false
        if (phone.isNotBlank()) {
            try {
                val intent = getWhatsAppIntentForPackage(packageName, phone, _messageText.value)
                context.startActivity(intent)
                onWhatsAppLaunchedCallback?.invoke()
            } catch (e: Exception) {
                openWhatsAppFallback(context, phone)
            }
        }
    }

    private fun getWhatsAppIntentForPackage(packageName: String, cleanedPhone: String, messageText: String): Intent {
        val uriString = if (messageText.isNotBlank()) {
            val encodedMsg = URLEncoder.encode(messageText, "UTF-8")
            "https://wa.me/$cleanedPhone?text=$encodedMsg"
        } else {
            "https://wa.me/$cleanedPhone"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun openWhatsAppFallback(context: Context, cleanedPhone: String) {
        val message = _messageText.value
        val uriString = if (message.isNotBlank()) {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            "https://wa.me/$cleanedPhone?text=$encodedMsg"
        } else {
            "https://wa.me/$cleanedPhone"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onWhatsAppLaunchedCallback?.invoke()
        } catch (e: Exception) {
            Log.e("ConversaViewModel", "Failed fallback", e)
        }
    }

    /**
     * Creates an INTENT to Insert a Contact in the native address book.
     * This is extremely smooth, clean, and DOES NOT require critical/dangerous Permissions!
     */
    fun getAddContactIntent(): Intent {
        val rawNum = _phoneNumber.value
        val formattedPhone = PhoneUtils.formatDisplayNumber(rawNum)
        val name = _contactName.value

        // Save action to history database
        saveActionToHistory(rawNum, name, "CONTACT")

        return Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, formattedPhone)
            if (name.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, name)
            }
        }
    }

    private fun saveActionToHistory(phone: String, name: String, type: String) {
        viewModelScope.launch {
            repository.insertHistory(
                ContactHistory(
                    phoneNumber = phone,
                    contactName = name.ifBlank { "Contato sem nome" },
                    actionType = type
                )
            )
        }
    }

    // Room operations
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun addCustomTemplate(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.insertTemplate(MessageTemplate(title = title, content = content))
        }
    }

    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            repository.deleteTemplateById(id)
        }
    }
}

// Custom viewmodel factory is required since we pass the repository
class ConversaViewModelFactory(
    private val application: Application,
    private val repository: DatabaseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConversaViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class WhatsAppStatus(val hasNormal: Boolean, val hasBusiness: Boolean)
