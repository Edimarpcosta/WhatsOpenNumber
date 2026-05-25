package com.example.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ContactHistory
import com.example.data.database.MessageTemplate
import com.example.data.gemini.ExtractedContact
import com.example.ui.viewmodel.ConversaViewModel
import com.example.utils.PhoneUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversaMainScreen(
    viewModel: ConversaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val phoneNumber by viewModel.phoneNumber.collectAsStateWithLifecycle()
    val contactName by viewModel.contactName.collectAsStateWithLifecycle()
    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val selectedTemplateId by viewModel.selectedTemplateId.collectAsStateWithLifecycle()
    val clipboardDetectedNumber by viewModel.clipboardDetectedNumber.collectAsStateWithLifecycle()
    val showWhatsAppChooser by viewModel.showWhatsAppChooser.collectAsStateWithLifecycle()
    
    // AI states
    val aiInputText by viewModel.aiInputText.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractedContacts by viewModel.extractedContacts.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    
    // DB flows
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val templateList by viewModel.templateList.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("WhatsApp", "Extração IA", "Modelos & Histórico")
    
    // Dialog control states
    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubble,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Conversa Direta",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.checkContextClipboard()
                            Toast.makeText(context, "Buscando número copiado...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Verificar Área de Transferência",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Elegant M3 Tabs
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (activeTab) {
                    0 -> WhatsAppTabContent(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        messageText = messageText,
                        selectedTemplateId = selectedTemplateId,
                        clipboardDetectedNumber = clipboardDetectedNumber,
                        templateList = templateList,
                        onPhoneChange = { viewModel.setPhoneNumber(it) },
                        onNameChange = { viewModel.setContactName(it) },
                        onMsgChange = { viewModel.setMessageText(it) },
                        onSelectTemplate = { viewModel.selectTemplate(it) },
                        onUseClipboard = { viewModel.useClipboardNumber() },
                        onDismissClipboard = { viewModel.dismissClipboardSuggestion() },
                        onStartWhatsApp = {
                            if (phoneNumber.isNotBlank()) {
                                viewModel.startWhatsAppFlow(context)
                            } else {
                                Toast.makeText(context, "Digite um número de telefone válido.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddContact = {
                            if (phoneNumber.isNotBlank()) {
                                try {
                                    val intent = viewModel.getAddContactIntent()
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Iniciando criador de contatos...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro ao iniciar criação de contato nativo.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Digite um número primeiro.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShareLink = {
                            if (phoneNumber.isNotBlank()) {
                                val cleanNum = PhoneUtils.formatForWhatsApp(phoneNumber)
                                val link = "https://wa.me/$cleanNum"
                                clipboardManager.setText(AnnotatedString(link))
                                Toast.makeText(context, "Link do WhatsApp copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Digite um número de telefone primeiro.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    1 -> AiExtractorTabContent(
                        aiInputText = aiInputText,
                        isExtracting = isExtracting,
                        extractedContacts = extractedContacts,
                        aiError = aiError,
                        onTextChange = { viewModel.setAiInputText(it) },
                        onExtract = { viewModel.performAiExtraction() },
                        onUseContact = { contact ->
                            viewModel.useExtractedContact(contact)
                            activeTab = 0 // return to main sending form
                            Toast.makeText(context, "Contato carregado na tela principal!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> TemplatesAndHistoryTabContent(
                        historyList = historyList,
                        templateList = templateList,
                        onAddTemplateClick = { showAddTemplateDialog = true },
                        onDeleteTemplate = { viewModel.deleteTemplate(it) },
                        onClearHistoryClick = { showClearHistoryDialog = true },
                        onDeleteHistoryItem = { viewModel.deleteHistoryItem(it) },
                        onSelectHistoryItem = { historyItem ->
                            viewModel.setPhoneNumber(historyItem.phoneNumber)
                            viewModel.setContactName(historyItem.contactName)
                            activeTab = 0
                            Toast.makeText(context, "Carregado: ${historyItem.phoneNumber}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Custom Portuguese Message Template Creator Dialog
        if (showAddTemplateDialog) {
            AddTemplateDialog(
                onDismiss = { showAddTemplateDialog = false },
                onAdd = { title, content ->
                    viewModel.addCustomTemplate(title, content)
                    showAddTemplateDialog = false
                    Toast.makeText(context, "Modelo adicionado!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // History Clear Confirm Dialog
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Limpar Histórico") },
                text = { Text("Deseja realmente apagar todo o histórico de interações do aplicativo? Esta ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearHistory()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "Histórico limpo com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Limpar Tudo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // WhatsApp App Version Chooser Dialog
        if (showWhatsAppChooser) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissWhatsAppChooser() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubble,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escolha o WhatsApp",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Detectamos mais de uma versão do WhatsApp instalada. Qual delas você gostaria de utilizar para iniciar a conversa?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Option 1: WhatsApp Normal
                        Surface(
                            onClick = {
                                viewModel.launchWhatsAppWithPackage(context, "com.whatsapp")
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChatBubble,
                                    contentDescription = "WhatsApp Pessoal",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "WhatsApp Normal",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Para mensagens pessoais",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Option 2: WhatsApp Business
                        Surface(
                            onClick = {
                                viewModel.launchWhatsAppWithPackage(context, "com.whatsapp.w4b")
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Business,
                                    contentDescription = "WhatsApp Business",
                                    tint = Color(0xFF14C656),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "WhatsApp Business",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Para mensagens empresariais",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissWhatsAppChooser() }
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}

@Composable
fun WhatsAppTabContent(
    phoneNumber: String,
    contactName: String,
    messageText: String,
    selectedTemplateId: Int?,
    clipboardDetectedNumber: String?,
    templateList: List<MessageTemplate>,
    onPhoneChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onMsgChange: (String) -> Unit,
    onSelectTemplate: (MessageTemplate) -> Unit,
    onUseClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onStartWhatsApp: () -> Unit,
    onAddContact: () -> Unit,
    onShareLink: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clipboard detection banner
        if (clipboardDetectedNumber != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPasteGo,
                                contentDescription = "Área de Transferência",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Número na Área de Transferência",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Detectamos o número: $clipboardDetectedNumber",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = onDismissClipboard,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text("Ignorar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onUseClipboard,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Usar Número")
                            }
                        }
                    }
                }
            }
        }

        // Contact details form card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Dados do Contato",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Phone text field
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = onPhoneChange,
                        label = { Text("Número de Telefone") },
                        placeholder = { Text("Ex: 11999998888 ou +55...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Telefone"
                            )
                        },
                        trailingIcon = {
                            if (phoneNumber.isNotEmpty()) {
                                IconButton(onClick = { onPhoneChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpar"
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Optional name text field
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = onNameChange,
                        label = { Text("Nome do Contato (Opcional)") },
                        placeholder = { Text("Ex: João da Silva") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Nome"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // WhatsApp message config card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Mensagem Rápida para WhatsApp",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Horizontal quick selector template list
                    if (templateList.isNotEmpty()) {
                        Text(
                            text = "Selecione um modelo de mensagem:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                            ) {
                                items(templateList) { template ->
                                    val isSelected = selectedTemplateId == template.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectTemplate(template) },
                                        label = { 
                                            Text(
                                                text = template.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            ) 
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selecionado",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Message custom text box
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = onMsgChange,
                        label = { Text("Texto da Mensagem (Opcional)") },
                        placeholder = { Text("Escreva uma saudação ou use um modelo acima...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Message,
                                contentDescription = "Mensagem"
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Beautiful, heavy call-to-actions (FAB-like fullwidth)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Large primary button for WhatsApp
                Button(
                    onClick = onStartWhatsApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("whatsapp_button"),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Enviar",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Iniciar conversa no WhatsApp",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Contact insertion button
                    OutlinedButton(
                        onClick = onAddContact,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("contact_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Adicionar Contato",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Criar Contato",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    // Direct sharing option
                    OutlinedButton(
                        onClick = onShareLink,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar Link",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copiar Link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiExtractorTabContent(
    aiInputText: String,
    isExtracting: Boolean,
    extractedContacts: List<ExtractedContact>,
    aiError: String?,
    onTextChange: (String) -> Unit,
    onExtract: () -> Unit,
    onUseContact: (ExtractedContact) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "IA",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Extração com Inteligência Artificial",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Cole blocos de textos, e-mails, listas de transmissão ou cartões de negócio. A IA irá decifrar os nomes e números telefônicos para iniciar ações com apenas um toque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Paste block area
        item {
            OutlinedTextField(
                value = aiInputText,
                onValueChange = onTextChange,
                label = { Text("Bloco de texto bagunçado") },
                placeholder = { Text("Ex: Olá, meu nome é Edimar Pinheiro. Podem me chamar no 5511987654321, obrigado.") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
            )
        }

        // Extract triggering button
        item {
            Button(
                onClick = {
                    keyboardController?.hide()
                    onExtract()
                },
                enabled = aiInputText.isNotBlank() && !isExtracting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Analisando com IA...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Extrair"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Identificar Números",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error message banner
        if (aiError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Erro",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = aiError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Extracted list
        if (extractedContacts.isNotEmpty()) {
            item {
                Text(
                    text = "Contatos Encontrados (${extractedContacts.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(extractedContacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = contact.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = PhoneUtils.formatDisplayNumber(contact.phone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Actions for this extracted item
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onUseContact(contact) },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Carregar contato"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TemplatesAndHistoryTabContent(
    historyList: List<ContactHistory>,
    templateList: List<MessageTemplate>,
    onAddTemplateClick: () -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    onClearHistoryClick: () -> Unit,
    onDeleteHistoryItem: (Int) -> Unit,
    onSelectHistoryItem: (ContactHistory) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECTION 1: WHATSAPP TEMPLATES ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modelos de Mensagem",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onAddTemplateClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar Novo Modelo")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar")
                }
            }
        }

        if (templateList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Nenhum modelo de mensagem criado ainda. Crie modelos rápidos clicando em Adicionar.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(templateList, key = { it.id }) { template ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = template.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = template.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onDeleteTemplate(template.id) },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Apagar modelo"
                            )
                        }
                    }
                }
            }
        }

        // --- SECTION 2: HISTORY ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Histórico de Interações",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (historyList.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistoryClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Limpar Todo Histórico")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar")
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Nenhuma ação realizada ainda. Suas conversas iniciadas aparecerão registradas aqui para acesso rápido.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(historyList, key = { it.id }) { historyItem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectHistoryItem(historyItem) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Icon based on type
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (historyItem.actionType == "WHATSAPP") {
                                            Color(0xFFE8F5E9)
                                        } else {
                                            Color(0xFFE3F2FD)
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (historyItem.actionType == "WHATSAPP") {
                                        Icons.Default.ChatBubbleOutline
                                    } else {
                                        Icons.Default.PersonOutline
                                    },
                                    contentDescription = historyItem.actionType,
                                    tint = if (historyItem.actionType == "WHATSAPP") {
                                        Color(0xFF2E7D32)
                                    } else {
                                        Color(0xFF1565C0)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = historyItem.contactName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = PhoneUtils.formatDisplayNumber(historyItem.phoneNumber),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTimestamp(historyItem.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteHistoryItem(historyItem.id) },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remover do histórico",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Add Template Dialog in Portuguese
@Composable
fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Novo Modelo de Mensagem",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título do Modelo") },
                    placeholder = { Text("Ex: Resposta Rápida, Preços") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Mensagem") },
                    placeholder = { Text("Olá! Aqui estão as informações...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(title, content) },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
