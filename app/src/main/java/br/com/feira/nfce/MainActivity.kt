package br.com.feira.nfce

import android.content.Intent
import android.os.Bundle
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.domain.Normalizador
import br.com.feira.nfce.notas.model.NotaFiscal
import java.text.NumberFormat
import java.math.BigDecimal
import java.util.Locale
import br.com.feira.nfce.auth.AuthScreen
import br.com.feira.nfce.financeiro.presentation.*
import br.com.feira.nfce.financeiro.data.AccountEntity
import br.com.feira.nfce.financeiro.domain.Money
import java.time.LocalDate
import br.com.feira.nfce.notas.data.NotaComItens
import br.com.feira.nfce.ui.ExpensePalette
import br.com.feira.nfce.ui.MyExpensesFilterChip
import br.com.feira.nfce.ui.MyExpensesTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val financeViewModel: FinanceViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            MyExpensesTheme {
                val userId by viewModel.userId.collectAsState()
                val user by viewModel.currentUser.collectAsState()
                LaunchedEffect(userId) {
                    financeViewModel.setUser(userId)
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = userId > 0L
                        isAppearanceLightNavigationBars = userId > 0L
                    }
                }
                val state by viewModel.state.collectAsState()
                val categories by viewModel.categorias.collectAsState()
                val notes by viewModel.notas.collectAsState()
                val words by viewModel.keywords.collectAsState()
                val overrides by viewModel.overrides.collectAsState()
                val categoryMove by viewModel.categoryMove.collectAsState()
                val qrTest by viewModel.qrTest.collectAsState()
                val legacyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)financeViewModel.importLegacy(uri) }
                val scannerOptions = remember {
                    GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                }
                val scanner = remember(this) { GmsBarcodeScanning.getClient(this, scannerOptions) }
                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.importar(uri, contentResolver)
                }
                if(userId<=0L) AuthScreen(viewModel) else ExpensesShell(
                    userName=user?.name.orEmpty(), state=state, categories=categories, notes=notes, words=words, overrides=overrides,
                    categoryMove=categoryMove, qrTest=qrTest, vm=viewModel, finance=financeViewModel,email=user?.email.orEmpty(),onLegacyImport={legacyPicker.launch(arrayOf("application/octet-stream","application/x-sqlite3","*/*"))},
                    onImport={filePicker.launch(arrayOf("text/html","text/*","application/octet-stream","*/*"))},
                    onExport={note->share(viewModel.exportar(note))}, onLogout=viewModel::sair,
                    scanQr={ scanner.startScan().addOnSuccessListener { viewModel.consultarQrCode(it.rawValue) }.addOnFailureListener { viewModel.scannerQrFalhou() } },
                    onQrReview={viewModel.voltarResumoConsultaQr()}, onQrSave=viewModel::salvarNotaConsultadaQr,
                    onQrNew={viewModel.limparConsultaQr()}
                )
            }
        }
    }

    private fun share(json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "NFC-e importada")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar dados da NFC-e"))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExpensesShell(
    userName:String,state:RevisaoUiState,categories:List<CategoriaEntity>,notes:List<NotaComItens>,words:List<br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity>,overrides:List<br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity>,categoryMove:CategoryMoveRequest?,qrTest:QrTestUiState,vm:MainViewModel,finance:FinanceViewModel,email:String,onLegacyImport:()->Unit,onImport:()->Unit,onExport:(NotaComItens)->Unit,onLogout:()->Unit,scanQr:()->Unit,onQrReview:()->Unit,onQrSave:()->Unit,onQrNew:()->Unit
) {
    val nav= rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route?:"inicio"
    val accounts by finance.accounts.collectAsState();val financeCategories by finance.categories.collectAsState();val error by finance.message.collectAsState();var registerInvoice by remember { mutableStateOf<Long?>(null) };var openTransactionOnEnter by remember{mutableStateOf<String?>(null)}
    data class MainTab(val route:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
    val tabs=listOf(MainTab("inicio","Principal",Icons.Filled.Home),MainTab("transacoes","Transações",Icons.Filled.ReceiptLong),MainTab("relatorios","Relatório",Icons.Filled.PieChart),MainTab("plano","Planejamento",Icons.Filled.TrackChanges),MainTab("categorias","Categorias",Icons.Filled.GridView))
    val mainTabRoutes=tabs.map{it.route}.toSet()
    val navigateToTab:(String)->Unit={destination->
        if(destination in mainTabRoutes){
            nav.navigate(destination){
                popUpTo("inicio"){saveState=false}
                launchSingleTop=true
                restoreState=false
            }
        }
    }
    val firstName=userName.trim().substringBefore(' ').ifBlank { "Minha conta" }
    Scaffold(
        containerColor=ExpensePalette.Background,
        topBar={
            Row(Modifier.fillMaxWidth().background(ExpensePalette.Background).padding(start=20.dp,end=14.dp,top=14.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(ExpensePalette.Purple,Color(0xFFA26CFF))),CircleShape),contentAlignment=Alignment.Center){Text(firstName.take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Bold)}
                Column(Modifier.weight(1f).padding(start=12.dp)){Text(if(route=="inicio")"Olá," else "My Expenses",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text(if(route=="inicio")firstName else when(route){"transacoes"->"Transações";"relatorios"->"Relatório";"plano"->"Planejamento";"categorias"->"Categorias";"contas"->"Contas";"notas"->"Notas fiscais";else->"Configurações"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=ExpensePalette.Ink)}
                IconButton(onClick={nav.navigate("perfil"){launchSingleTop=true}}){Icon(Icons.Filled.Settings,contentDescription="Configurações",tint=ExpensePalette.Muted)}
            }
        },
        bottomBar={NavigationBar(containerColor=Color.White,contentColor=ExpensePalette.Muted,tonalElevation=0.dp){tabs.forEach{tab->NavigationBarItem(selected=route==tab.route,onClick={navigateToTab(tab.route)},icon={Icon(tab.icon,contentDescription=null)},label={Text(tab.label,maxLines=1)},colors=NavigationBarItemDefaults.colors(selectedIconColor=ExpensePalette.Purple,selectedTextColor=ExpensePalette.Purple,indicatorColor=Color.Transparent,unselectedIconColor=ExpensePalette.Muted,unselectedTextColor=ExpensePalette.Muted))}}}
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(error!=null) TextButton(onClick=finance::dismissMessage){Text(error!!,color=MaterialTheme.colorScheme.error)}
            NavHost(navController=nav,startDestination="inicio",modifier=Modifier.weight(1f)) {
                composable("inicio"){DashboardScreen(finance,onAccounts={nav.navigate("contas")},onReport={nav.navigate("relatorios")},onTransactions={nav.navigate("transacoes")},onAddTransaction={type->openTransactionOnEnter=type;nav.navigate("transacoes")})}
                composable("transacoes"){TransactionsScreen(finance,initialAction=openTransactionOnEnter,onInitialActionHandled={openTransactionOnEnter=null})}
                composable("contas"){AccountsScreen(finance)}
                composable("plano"){PlanningScreen(finance)}
                composable("relatorios"){ReportsScreen(finance)}
                composable("categorias") {
                    var fiscalCategories by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            MyExpensesFilterChip(selected=!fiscalCategories,onClick={fiscalCategories=false},label={Text("Financeiras")})
                            MyExpensesFilterChip(selected=fiscalCategories,onClick={fiscalCategories=true},label={Text("Regras de NFC-e")})
                        }
                        if(fiscalCategories) CategoryScreen(categories,words,overrides,categoryMove,vm,Modifier.weight(1f))
                        else CategoryManagementScreen(finance,Modifier.weight(1f))
                    }
                }
                composable("notas") {
                    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onImport){Text("Importar HTML/MHTML")};OutlinedButton(onClick=scanQr){Text("Ler QR da SEFAZ-AM")}}
                        when {
                            qrTest.notaConferencia!=null -> NfceReviewScreen(qrTest.notaConferencia!!,Modifier.weight(1f),onBack=onQrReview,onSave=onQrSave,saving=qrTest.salvandoNota,saved=qrTest.notaSalva,message=qrTest.mensagemSalvamento,onNewQuery=onQrNew)
                            qrTest.qrLido -> QrTestScreen(qrTest,Modifier.weight(1f),onScan=scanQr)
                            state.nota!=null && state.sucesso() -> ImportSuccess(state.aviso){vm.limparRevisao()}
                            state.nota!=null -> ReviewScreen(state,categories.filter{it.ativa},vm)
                            else -> HomeScreen(state,notes,onImport,onExport,vm::excluirNota,onRegisterExpense={registerInvoice=it},modifier=Modifier.weight(1f))
                        }
                    }
                }
                composable("perfil") {
                    Column(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        val migration by finance.migrationReport.collectAsState()
                        Text("Sua conta",style=MaterialTheme.typography.titleLarge,color=ExpensePalette.Ink)
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(userName,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(email,color=ExpensePalette.Muted);Text("Seus dados ficam armazenados localmente neste aparelho.",modifier=Modifier.padding(top=8.dp),color=ExpensePalette.Muted)}}
                        ProfileAction("Contas","Gerencie saldos, tipos e contas vinculadas"){nav.navigate("contas")}
                        ProfileAction("Notas fiscais","Importe HTML/MHTML, consulte QR e revise NFC-e"){nav.navigate("notas")}
                        ProfileAction("Importar banco legado","Escolha banco.db; o arquivo original permanece intacto",onLegacyImport)
                        migration?.let{Card(Modifier.fillMaxWidth()){Text(it,Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall)}}
                        OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
                    }
                }
            }
        }
    }
    registerInvoice?.let { invoiceId -> InvoiceExpenseDialog(invoiceId,accounts,financeCategories,{registerInvoice=null}){cat,account,date->finance.createInvoiceExpense(invoiceId,cat,account,date);registerInvoice=null} }
}

@Composable private fun ProfileAction(title:String,subtitle:String,onClick:()->Unit){Card(onClick=onClick,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,color=ExpensePalette.Muted,style=MaterialTheme.typography.bodySmall)};Text("›",color=ExpensePalette.Purple,style=MaterialTheme.typography.headlineSmall)}}}

@Composable
private fun InvoiceExpenseDialog(invoiceId:Long,accounts:List<AccountEntity>,categories:List<CategoriaEntity>,dismiss:()->Unit,save:(Long?,Long?,String)->Unit){
    var category by remember(categories){mutableStateOf(categories.firstOrNull{it.ativa}?.id)};var account by remember(accounts){mutableStateOf(accounts.firstOrNull()?.id)};var date by remember{mutableStateOf(LocalDate.now().toString())}
    var catOpen by remember{mutableStateOf(false)};var accountOpen by remember{mutableStateOf(false)}
    AlertDialog(
        onDismissRequest=dismiss,
        title={Text("Registrar nota como despesa")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("A nota será vinculada a uma única transação e passará a contar no saldo e nos relatórios.")
            OutlinedTextField(date,{date=it},label={Text("Data AAAA-MM-DD")},singleLine=true)
            Box(Modifier.fillMaxWidth()) {
                InvoiceSelectionButton(categories.firstOrNull{it.id==category}?.nome,"Selecionar categoria"){catOpen=true}
                DropdownMenu(catOpen,{catOpen=false}){categories.filter{it.ativa}.forEach{DropdownMenuItem(text={Text(it.nome)},onClick={category=it.id;catOpen=false})}}
            }
            Box(Modifier.fillMaxWidth()) {
                InvoiceSelectionButton(accounts.firstOrNull{it.id==account}?.name,"Selecionar conta"){accountOpen=true}
                DropdownMenu(accountOpen,{accountOpen=false}){accounts.forEach{DropdownMenuItem(text={Text(it.name)},onClick={account=it.id;accountOpen=false})}}
            }
        }},
        confirmButton={TextButton(onClick={save(category,account,date)}){Text("Registrar despesa")}},
        dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}}
    )
}

@Composable
private fun InvoiceSelectionButton(selectedLabel:String?,placeholder:String,onClick:()->Unit){
    if(selectedLabel.isNullOrBlank()){
        OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth()){
            Text(placeholder,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }else{
        Button(
            onClick=onClick,
            modifier=Modifier.fillMaxWidth(),
            colors=ButtonDefaults.buttonColors(containerColor=ExpensePalette.PurpleLight,contentColor=ExpensePalette.PurpleDark)
        ){
            Icon(Icons.Filled.Check,contentDescription="Selecionado",modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(selectedLabel,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QrTestScreen(state: QrTestUiState, modifier: Modifier = Modifier, onScan: () -> Unit) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Consultar NFC-e", style = MaterialTheme.typography.titleLarge)
        Text("Leia o QR Code impresso na NFC-e. O conteúdo e a chave não serão exibidos nem gravados em Logcat.")
        Button(onClick = onScan, enabled = !state.carregando) { Text("Escanear QR Code") }
        Text(
            when {
                !state.qrLido -> "QR Code: aguardando leitura"
                state.nfceAmReconhecida -> "QR Code: NFC-e do Amazonas reconhecida"
                else -> "QR Code: lido, mas não reconhecido como NFC-e do Amazonas"
            },
            style = MaterialTheme.typography.bodyLarge
        )
        state.usaHttps?.let {
            Text("Transporte: ${if (it) "HTTPS" else "HTTP (sem criptografia)"}")
        }
        state.respostaUsaHttps?.let {
            Text("Transporte da resposta final: ${if (it) "HTTPS" else "HTTP (sem criptografia)"}")
        }
        if (state.carregando) {
            CircularProgressIndicator()
            Text("Consultando diretamente a SEFAZ-AM…")
        }
        state.statusHttp?.let { Text("Código HTTP: $it") }
        state.tipoConteudo?.let { Text("Content-Type: $it") }
        state.bytesRecebidos?.let { Text("Bytes recebidos: $it") }
        if (state.statusHttp != null) Text(
            "Redirecionamento: " + when {
                !state.redirecionada -> "não detectado"
                state.redirecionamentoBloqueado -> "detectado e bloqueado"
                else -> "detectado e seguido no domínio autorizado"
            }
        )
        state.html?.let { Text("Conteúdo HTML: ${if (it) "sim" else "não"}") }
        state.estruturaNfce?.let { Text("Estrutura de NFC-e: ${if (it) "encontrada" else "não encontrada"}") }
        state.produtosIdentificados?.let { Text("Produtos encontrados pelos seletores: $it") }
        state.produtosComCamposCompletos?.let { Text("Produtos com quantidade, unidade e valores: $it") }
        state.parserAceitouHtml?.let { Text("Parser AM: ${if (it) "aceitou o HTML" else "não conseguiu interpretar"}") }
        state.mensagem?.let { Text(it, color = if (state.parserAceitouHtml == false || !state.nfceAmReconhecida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun NfceReviewScreen(
    nota: NotaFiscal,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    saved: Boolean,
    message: String?,
    onNewQuery: () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Conferir NFC-e", style = MaterialTheme.typography.titleLarge)
                Text(nota.emitente, style = MaterialTheme.typography.titleMedium)
                Text("Emissão: ${nota.dataEmissao?.toString() ?: "não informada"}")
                Text("Número: ${nota.numero ?: "não informado"} · Série: ${nota.serie ?: "não informada"}")
                Text("Chave: ${nota.chave.mascararChave()}")
                Text("Produtos extraídos: ${nota.itens.size}" + (nota.quantidadeItensInformada?.let { " · informado: $it" } ?: ""))
                nota.totalProdutos?.let { Text("Total dos produtos: ${it.emReais()}") }
                nota.descontos?.let { Text("Descontos: ${it.emReais()}") }
                nota.acrescimos?.let { Text("Acréscimos, frete e despesas: ${it.emReais()}") }
                Text("Total da nota: ${nota.total.emReais()}", style = MaterialTheme.typography.titleMedium)
                nota.valorPago?.let { Text("Valor pago: ${it.emReais()}") }
                nota.troco?.let { Text("Troco: ${it.emReais()}") }
            }
            item { Text("Produtos", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(nota.itens) { index, item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. ${item.descricao}", style = MaterialTheme.typography.titleMedium)
                        Text("Quantidade: ${item.quantidade.toPlainString()} ${item.unidade}")
                        Text("Valor unitário: ${item.valorUnitario.emReais()}")
                        Text("Valor total: ${item.valorTotal.emReais()}")
                    }
                }
            }
            item {
                Text("Pagamentos", style = MaterialTheme.typography.titleLarge)
                if (nota.pagamentos.isEmpty()) Text("Nenhuma forma de pagamento identificada.")
            }
            itemsIndexed(nota.pagamentos) { index, pagamento ->
                Text("${index + 1}. ${pagamento.forma}: ${pagamento.valor.emReais()}")
            }
            item {
                Text("Validações", style = MaterialTheme.typography.titleLarge)
                Text(if (nota.validacao.avisos.isEmpty()) "Resultado geral: conferência sem divergências" else "Resultado geral: há campos para conferir")
                Text("Chave: ${if (nota.validacao.chaveValida) "válida" else "inválida"}")
                Text("Quantidade: ${nota.validacao.qtdItensConfere?.let { if (it) "confere" else "diverge" } ?: "não informada"}")
                Text("Soma dos itens: ${nota.validacao.somaItens.emReais()} · total ajustado: ${if (nota.validacao.somaItensConfereComTotal) "confere" else "diverge"}")
                nota.validacao.avisos.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Voltar") }
            Button(onClick = onSave, enabled = !saving && !saved, modifier = Modifier.weight(1f)) {
                Text(when { saved -> "Salva"; saving -> "Salvando…"; else -> "Salvar nota" })
            }
            Button(onClick = onNewQuery, modifier = Modifier.weight(1f)) { Text("Nova consulta") }
        }
        message?.let { Text(it, color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }
}

private fun BigDecimal.emReais(): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(this)

private fun String.emReais(): String = toBigDecimalOrNull()?.emReais() ?: this

private fun String.mascararChave(): String = if (length == 44) "${"•".repeat(40)}${takeLast(4)}" else "Chave indisponível"

private fun RevisaoUiState.sucesso() = salvo

@Composable
private fun HomeScreen(
    state: RevisaoUiState,
    notes: List<br.com.feira.nfce.notas.data.NotaComItens>,
    onImport: () -> Unit,
    onExport: (br.com.feira.nfce.notas.data.NotaComItens) -> Unit,
    onDelete: (Long) -> Unit,
    onRegisterExpense: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteToDelete by remember { mutableStateOf<br.com.feira.nfce.notas.data.NotaComItens?>(null) }
    var noteToShow by remember { mutableStateOf<br.com.feira.nfce.notas.data.NotaComItens?>(null) }
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Excluir nota fiscal?") },
            text = { Text("Tem certeza de que deseja excluir esta nota fiscal? Os produtos e pagamentos associados a ela também serão removidos.") },
            confirmButton = {
                TextButton(onClick = { onDelete(note.nota.id); noteToDelete = null }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text("Cancelar") } }
        )
    }
    val selectedNote = noteToShow
    if (selectedNote != null) {
        NotaPersistidaDetails(selectedNote, onBack = { noteToShow = null }, modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        Text("Importe um arquivo HTML ou MHTML salvo da consulta NFC-e da SEFAZ-AM. O processamento acontece no aparelho.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImport, enabled = !state.carregando) { Text(if (state.carregando) "Lendo arquivo…" else "Importar nota") }
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Notas importadas", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.nota.id }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.nota.emitente, style = MaterialTheme.typography.titleMedium)
                        Text("CNPJ ${note.nota.cnpj} · ${note.nota.dataEmissao.orEmpty()}")
                        Text("Total R$ ${note.nota.total} · ${note.itens.size} produtos")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            TextButton(onClick = { noteToShow = note }) { Text("Detalhes",maxLines=1,softWrap=false) }
                            TextButton(onClick = { onRegisterExpense(note.nota.id) }) { Text("Registrar despesa",maxLines=1,softWrap=false) }
                            TextButton(onClick = { onExport(note) }) { Text("Exportar JSON",maxLines=1,softWrap=false) }
                            TextButton(onClick = { noteToDelete = note }) { Text("Excluir nota",maxLines=1,softWrap=false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotaPersistidaDetails(
    note: br.com.feira.nfce.notas.data.NotaComItens,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text("Detalhes da NFC-e", style = MaterialTheme.typography.titleLarge)
        Text(note.nota.emitente, style = MaterialTheme.typography.titleMedium)
        Text("Emissão: ${note.nota.dataEmissao ?: "não informada"}")
        Text("Número: ${note.nota.numero ?: "não informado"} · Série: ${note.nota.serie ?: "não informada"}")
        Text("Chave: ${note.nota.chave.mascararChave()}")
        Text("Total da nota: ${note.nota.total.emReais()}")
        note.nota.totalProdutos?.let { Text("Total dos produtos: ${it.emReais()}") }
        note.nota.descontos?.let { Text("Descontos: ${it.emReais()}") }
        note.nota.acrescimos?.let { Text("Acréscimos: ${it.emReais()}") }
        note.nota.valorPago?.let { Text("Valor pago: ${it.emReais()}") }
        note.nota.troco?.let { Text("Troco: ${it.emReais()}") }
        note.nota.quantidadeItensInformada?.let { Text("Quantidade de itens informada na nota: $it") }
        Text("${note.itens.size} produtos", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(note.itens, key = { _, item -> item.id }) { index, item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. ${item.descricao}", style = MaterialTheme.typography.titleMedium)
                        Text("Quantidade: ${item.quantidade} ${item.unidade}")
                        Text("Valor unitário: ${item.valorUnitario.emReais()}")
                        Text("Valor total: ${item.valorTotal.emReais()}")
                    }
                }
            }
            if (note.pagamentos.isNotEmpty()) {
                item { Text("Pagamentos", style = MaterialTheme.typography.titleLarge) }
                itemsIndexed(note.pagamentos, key = { _, payment -> payment.id }) { index, payment ->
                    Text("${index + 1}. ${payment.forma}: ${payment.valor.emReais()}")
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao histórico") }
    }
}

@Composable
private fun ReviewScreen(state: RevisaoUiState, categories: List<CategoriaEntity>, vm: MainViewModel) {
    val note = state.nota ?: return
    Column(Modifier.fillMaxSize()) {
        Text("Revisar nota", style = MaterialTheme.typography.titleLarge)
        Text("${note.emitente} · ${note.cnpj}")
        Text("Total R$ ${note.total} · chave ${if (note.validacao.chaveValida) "válida" else "inválida"}")
        state.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.itens) { index, item ->
                var expanded by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.descricao, style = MaterialTheme.typography.titleMedium)
                        Text("${item.quantidade.toPlainString()} ${item.unidade} × R$ ${item.valorUnitario.toPlainString()} · total R$ ${item.valorTotal.toPlainString()}")
                        Row {
                            TextButton(onClick = { expanded = true }) { Text("${item.resultado.categoriaNome} · ${item.resultado.origem}") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                categories.forEach { category ->
                                    DropdownMenuItem(text = { Text(category.nome) }, onClick = { vm.escolherCategoria(index, category); expanded = false })
                                }
                            }
                        }
                        if (!item.resultado.identificado || item.resultado.ambiguo || item.resultado.origem == "MANUAL") {
                            Row {
                                Checkbox(checked = index in state.salvarAprendizado, onCheckedChange = { vm.alternarAprendizado(index, it) })
                                Text("Salvar esta correção para próximas notas", modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::limparRevisao) { Text("Cancelar") }
            Button(onClick = vm::confirmarImportacao, enabled = !state.carregando) { Text("Confirmar e salvar") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ImportSuccess(aviso: String?, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nota salva neste aparelho.", style = MaterialTheme.typography.titleLarge)
        aviso?.let { Text(it) }
        Button(onClick = onNew) { Text("Importar outra nota") }
    }
}

@Composable
private fun CategoryScreen(
    categories: List<CategoriaEntity>,
    words: List<br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity>,
    overrides: List<br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity>,
    categoryMove: CategoryMoveRequest?,
    vm: MainViewModel,
    modifier: Modifier = Modifier
) {
    var newCategory by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    var selected by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var destination by remember(categoryMove, categories) { mutableStateOf(categories.firstOrNull { it.ativa && it.id != categoryMove?.category?.id }) }
    var destinationExpanded by remember { mutableStateOf(false) }
    if (categoryMove != null) {
        AlertDialog(
            onDismissRequest = vm::cancelarDesativacao,
            title = { Text("Categoria em uso") },
            text = {
                Column {
                    Text("${categoryMove.affectedItems} produto(s) usam ${categoryMove.category.nome}. Escolha para onde transferir produtos, palavras e correções aprendidas antes de desativar.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { destinationExpanded = true }) { Text(destination?.nome ?: "Escolher destino") }
                    DropdownMenu(expanded = destinationExpanded, onDismissRequest = { destinationExpanded = false }) {
                        categories.filter { it.ativa && it.id != categoryMove.category.id }.forEach { category ->
                            DropdownMenuItem(text = { Text(category.nome) }, onClick = { destination = category; destinationExpanded = false })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { destination?.let { vm.confirmarDesativacao(it.id) } }, enabled = destination != null) { Text("Transferir e desativar") }
            },
            dismissButton = { TextButton(onClick = vm::cancelarDesativacao) { Text("Cancelar") } }
        )
    }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Gerenciar categorias", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newCategory, { newCategory = it }, label = { Text("Nova categoria") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = { vm.criarCategoria(newCategory); newCategory = "" }) { Text("Criar") }
            }
        }
        items(categories, key = { "categoria-${it.id}" }) { category ->
            var editing by remember(category.id) { mutableStateOf(category.nome) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${category.nome} · ${category.origem}")
                    if (category.origem == "PERSONALIZADA") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(editing, { editing = it }, label = { Text("Nome") }, modifier = Modifier.weight(1f), singleLine = true)
                            TextButton(onClick = { vm.renomearCategoria(category.id, editing) }) { Text("Salvar") }
                            TextButton(onClick = { if (category.ativa) vm.solicitarDesativacao(category) else vm.ativarCategoria(category, true) }) { Text(if (category.ativa) "Desativar" else "Ativar") }
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Adicionar termo", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { categoryExpanded = true }) { Text(selected?.nome ?: "Escolher categoria") }
            DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.filter { it.ativa }.forEach { category -> DropdownMenuItem(text = { Text(category.nome) }, onClick = { selected = category; categoryExpanded = false }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(word, { word = it }, label = { Text("Palavra ou frase") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = { selected?.let { vm.criarPalavra(word, it) }; word = "" }, enabled = selected != null) { Text("Adicionar") }
            }
        }
        item { Text("Palavras-chave", style = MaterialTheme.typography.titleMedium) }
        items(words, key = { "palavra-${it.id}" }) { item ->
            val categoryName = categories.firstOrNull { it.id == item.categoriaId }?.nome ?: "Categoria removida"
            var term by remember(item.id, item.termo) { mutableStateOf(item.termo) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(term, { term = it }, label = { Text("$categoryName · ${item.tipoCorrespondencia}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Column {
                    if (item.origem == "USUARIO") TextButton(onClick = { vm.atualizarPalavra(item, term) }) { Text("Salvar") }
                    if (item.ativa) TextButton(onClick = { vm.desativarPalavra(item) }) { Text("Desativar") }
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Correções aprendidas", style = MaterialTheme.typography.titleMedium)
        }
        items(overrides, key = { "override-${it.id}" }) { override ->
            val categoryName = categories.firstOrNull { it.id == override.categoriaId }?.nome ?: "Categoria indisponível"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${override.descricaoOriginal} → $categoryName")
                TextButton(onClick = { vm.excluirOverride(override.id) }) { Text("Excluir") }
            }
        }
    }
}
