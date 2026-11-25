package br.com.feira.nfce

import android.content.Intent
import android.os.Bundle
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.domain.Normalizador
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                val categories by viewModel.categorias.collectAsState()
                val notes by viewModel.notas.collectAsState()
                val words by viewModel.keywords.collectAsState()
                val overrides by viewModel.overrides.collectAsState()
                val categoryMove by viewModel.categoryMove.collectAsState()
                val qrTest by viewModel.qrTest.collectAsState()
                val scannerOptions = remember {
                    GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                }
                val scanner = remember(this) { GmsBarcodeScanning.getClient(this, scannerOptions) }
                var screen by remember { mutableStateOf("Início") }
                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.importar(uri, contentResolver)
                }
                Scaffold(topBar = { TopAppBar(title = { Text("Feira NFC-e") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Início", "Categorias", "Teste QR").forEach { tab ->
                                if (screen == tab) Button(onClick = { screen = tab }) { Text(tab) }
                                else OutlinedButton(onClick = { screen = tab }) { Text(tab) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        when {
                            screen == "Teste QR" -> QrTestScreen(qrTest, Modifier.weight(1f)) {
                                scanner.startScan()
                                    .addOnSuccessListener { barcode -> viewModel.consultarQrCode(barcode.rawValue) }
                                    .addOnFailureListener { viewModel.scannerQrFalhou() }
                            }
                            screen == "Categorias" -> CategoryScreen(categories, words, overrides, categoryMove, viewModel, Modifier.weight(1f))
                            state.nota != null && state.sucesso() -> ImportSuccess(state.aviso, onNew = { viewModel.limparRevisao() })
                            state.nota != null -> ReviewScreen(state, categories.filter { it.ativa }, viewModel)
                            else -> HomeScreen(
                                state = state,
                                notes = notes,
                                onImport = { filePicker.launch(arrayOf("text/html", "text/*", "application/octet-stream", "*/*")) },
                                onExport = { note -> share(viewModel.exportar(note)) },
                                onDelete = viewModel::excluirNota,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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
private fun QrTestScreen(state: QrTestUiState, modifier: Modifier = Modifier, onScan: () -> Unit) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Teste de consulta NFC-e", style = MaterialTheme.typography.titleLarge)
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

private fun RevisaoUiState.sucesso() = salvo

@Composable
private fun HomeScreen(
    state: RevisaoUiState,
    notes: List<br.com.feira.nfce.notas.data.NotaComItens>,
    onImport: () -> Unit,
    onExport: (br.com.feira.nfce.notas.data.NotaComItens) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteToDelete by remember { mutableStateOf<br.com.feira.nfce.notas.data.NotaComItens?>(null) }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onExport(note) }) { Text("Exportar JSON") }
                            TextButton(onClick = { noteToDelete = note }) { Text("Excluir nota") }
                        }
                    }
                }
            }
        }
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
