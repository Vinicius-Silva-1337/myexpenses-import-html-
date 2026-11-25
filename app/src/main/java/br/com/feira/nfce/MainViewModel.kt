package br.com.feira.nfce

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity
import br.com.feira.nfce.categorias.data.seed.CategoriaSeeder
import br.com.feira.nfce.categorias.domain.Categorizador
import br.com.feira.nfce.categorias.domain.Normalizador
import br.com.feira.nfce.categorias.domain.ResultadoCategorizacao
import br.com.feira.nfce.notas.data.ItemNotaEntity
import br.com.feira.nfce.notas.data.NotaComItens
import br.com.feira.nfce.notas.data.NotaRepository
import br.com.feira.nfce.notas.importacao.ImportadorNota
import br.com.feira.nfce.notas.model.NotaFiscal
import br.com.feira.nfce.notas.parser.ParserNfceException
import br.com.feira.nfce.notas.data.ItemEmRevisao
import br.com.feira.nfce.notas.consulta.NfceQrUrlException
import br.com.feira.nfce.notas.consulta.NfceQrUrlValidator
import br.com.feira.nfce.notas.consulta.SefazNfceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import java.math.BigDecimal

data class RevisaoUiState(
    val nota: NotaFiscal? = null,
    val itens: List<ItemEmRevisao> = emptyList(),
    val salvarAprendizado: Set<Int> = emptySet(),
    val carregando: Boolean = false,
    val erro: String? = null,
    val aviso: String? = null,
    val salvo: Boolean = false
)

data class CategoryMoveRequest(val category: CategoriaEntity, val affectedItems: Int)

data class QrTestUiState(
    val qrLido: Boolean = false,
    val nfceAmReconhecida: Boolean = false,
    val usaHttps: Boolean? = null,
    val carregando: Boolean = false,
    val statusHttp: Int? = null,
    val tipoConteudo: String? = null,
    val bytesRecebidos: Int? = null,
    val redirecionada: Boolean = false,
    val redirecionamentoBloqueado: Boolean = false,
    val html: Boolean? = null,
    val estruturaNfce: Boolean? = null,
    val produtosIdentificados: Int? = null,
    val produtosComCamposCompletos: Int? = null,
    val parserAceitouHtml: Boolean? = null,
    val mensagem: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val database = AppDatabase.get(application)
    private val categorizador = Categorizador(database.categoriaDao(), database.palavraChaveDao(), database.productOverrideDao())
    private val repository = NotaRepository(database, categorizador)
    private val _state = MutableStateFlow(RevisaoUiState())
    private val _categoryMove = MutableStateFlow<CategoryMoveRequest?>(null)
    private val _qrTest = MutableStateFlow(QrTestUiState())
    private val sefazClient = SefazNfceClient()
    val categoryMove: StateFlow<CategoryMoveRequest?> = _categoryMove.asStateFlow()
    val qrTest: StateFlow<QrTestUiState> = _qrTest.asStateFlow()
    private val seedComplete = CompletableDeferred<Unit>()
    val state: StateFlow<RevisaoUiState> = _state.asStateFlow()
    val categorias = database.categoriaDao().observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notas = database.notaDao().observarNotas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val keywords = database.palavraChaveDao().observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overrides = database.productOverrideDao().observarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { CategoriaSeeder(application, database).inicializar() }
            catch (_: Exception) { /* Parser remains usable if local category seeding fails. */ }
            finally { seedComplete.complete(Unit) }
        }
    }

    fun importar(uri: Uri, resolver: android.content.ContentResolver) {
        viewModelScope.launch {
            _state.value = RevisaoUiState(carregando = true)
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    val data = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Não foi possível abrir o arquivo selecionado.")
                    data to displayName
                }
                val note = withContext(Dispatchers.Default) { ImportadorNota.parse(bytes, name) }
                val alreadyExists = withContext(Dispatchers.IO) { database.notaDao().porChave(note.chave) != null }
                if (alreadyExists) {
                    _state.value = RevisaoUiState(erro = "Esta NFC-e já foi importada (chave duplicada).")
                    return@launch
                }
                seedComplete.await()
                val items = withContext(Dispatchers.IO) { repository.categorizar(note) }
                val totalDifference = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.valorTotal }.subtract(note.total).abs()
                _state.value = RevisaoUiState(
                    nota = note,
                    itens = items,
                    aviso = buildList {
                        if (!note.validacao.chaveValida) add("A chave de acesso não passou na validação do dígito verificador.")
                        if (!note.validacao.somaItensConfereComTotal) add("A soma dos itens diverge do total da nota (diferença ${totalDifference.toPlainString()}).")
                        if (note.validacao.qtdItensConfere == false) add("A quantidade informada de itens diverge das linhas encontradas.")
                    }.joinToString(" ").ifBlank { null }
                )
            } catch (error: Exception) {
                val message = if (error is ParserNfceException) error.message else "Não foi possível ler este arquivo. Confira se é uma NFC-e HTML ou MHTML válida."
                _state.value = RevisaoUiState(erro = message)
            }
        }
    }

    fun escolherCategoria(index: Int, categoria: CategoriaEntity) {
        val current = _state.value
        val item = current.itens.getOrNull(index) ?: return
        val updated = item.copy(resultado = ResultadoCategorizacao(categoria.id, categoria.nome, true, "MANUAL"))
        _state.value = current.copy(itens = current.itens.toMutableList().also { it[index] = updated })
    }

    fun alternarAprendizado(index: Int, save: Boolean) {
        val updated = _state.value.salvarAprendizado.toMutableSet()
        if (save) updated += index else updated -= index
        _state.value = _state.value.copy(salvarAprendizado = updated)
    }

    fun confirmarImportacao() {
        val current = _state.value
        val note = current.nota ?: return
        viewModelScope.launch {
            try {
                val inserted = withContext(Dispatchers.IO) { repository.salvar(note, current.itens, current.salvarAprendizado) }
                _state.value = current.copy(salvo = inserted, erro = if (inserted) null else "Esta NFC-e já foi importada.")
            } catch (_: Exception) {
                _state.value = current.copy(erro = "Não foi possível salvar a nota. Nenhum dado foi descartado da tela.")
            }
        }
    }

    fun limparRevisao() { _state.value = RevisaoUiState() }

    fun scannerQrFalhou() {
        _qrTest.value = QrTestUiState(mensagem = "Não foi possível iniciar o leitor de QR Code. Verifique o Google Play Services e tente novamente.")
    }

    fun consultarQrCode(rawValue: String?) {
        if (rawValue.isNullOrBlank()) {
            _qrTest.value = QrTestUiState(qrLido = true, mensagem = "O QR Code não contém texto legível.")
            return
        }
        val validated = try {
            NfceQrUrlValidator.validate(rawValue)
        } catch (error: NfceQrUrlException) {
            _qrTest.value = QrTestUiState(qrLido = true, mensagem = error.message)
            return
        }
        _qrTest.value = QrTestUiState(
            qrLido = true,
            nfceAmReconhecida = true,
            usaHttps = validated.usesHttps,
            carregando = true,
            mensagem = "NFC-e do Amazonas reconhecida. Consultando a SEFAZ…"
        )
        viewModelScope.launch {
            try {
                val result = sefazClient.fetch(validated)
                val inspection = result.htmlInspection
                val message = when {
                    result.redirectBlocked -> "A SEFAZ redirecionou para um endereço não autorizado; o redirecionamento foi bloqueado."
                    result.statusCode !in 200..299 -> "A SEFAZ respondeu com erro HTTP."
                    result.responseBytes == 0 -> "A resposta da SEFAZ veio vazia."
                    inspection == null || !inspection.isHtml -> "A resposta recebida não é HTML."
                    inspection.parserAccepted -> "HTML de NFC-e reconhecido; o parser AM identificou ${inspection.productCount} produto(s)."
                    inspection.hasNfceStructure -> "A página contém estrutura de NFC-e, mas o parser AM não conseguiu interpretá-la por completo."
                    else -> "O HTML recebido não corresponde à estrutura esperada de uma NFC-e do Amazonas."
                }
                _qrTest.value = QrTestUiState(
                    qrLido = true,
                    nfceAmReconhecida = true,
                    usaHttps = validated.usesHttps,
                    statusHttp = result.statusCode,
                    tipoConteudo = result.contentType,
                    bytesRecebidos = result.responseBytes,
                    redirecionada = result.redirected,
                    redirecionamentoBloqueado = result.redirectBlocked,
                    html = inspection?.isHtml,
                    estruturaNfce = inspection?.hasNfceStructure,
                    produtosIdentificados = inspection?.productCount,
                    produtosComCamposCompletos = inspection?.completeProductCount,
                    parserAceitouHtml = inspection?.parserAccepted,
                    mensagem = message
                )
            } catch (_: Exception) {
                _qrTest.value = QrTestUiState(
                    qrLido = true,
                    nfceAmReconhecida = true,
                    mensagem = "Não foi possível consultar a SEFAZ. Verifique a conexão e tente novamente."
                )
            }
        }
    }

    fun excluirNota(notaId: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.excluirNota(notaId)
    }

    fun criarCategoria(nome: String) = viewModelScope.launch(Dispatchers.IO) {
        if (nome.isBlank()) return@launch
        if (database.categoriaDao().porNome(nome.trim()) == null) {
            database.categoriaDao().inserir(CategoriaEntity(nome = nome.trim(), origem = "PERSONALIZADA"))
        }
    }

    fun renomearCategoria(id: Long, nome: String) = viewModelScope.launch(Dispatchers.IO) {
        if (nome.isNotBlank()) {
            val duplicate = database.categoriaDao().porNome(nome.trim())
            if (duplicate == null || duplicate.id == id) {
                database.categoriaDao().renomear(id, nome.trim(), nome.trim().uppercase(java.util.Locale.ROOT), null)
            }
        }
    }

    fun ativarCategoria(category: CategoriaEntity, ativa: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        database.categoriaDao().definirAtiva(category.id, ativa)
    }

    fun solicitarDesativacao(category: CategoriaEntity) = viewModelScope.launch(Dispatchers.IO) {
        val count = database.notaDao().contarItensDaCategoria(category.id)
        if (count == 0) database.categoriaDao().definirAtiva(category.id, false)
        else _categoryMove.value = CategoryMoveRequest(category, count)
    }

    fun cancelarDesativacao() { _categoryMove.value = null }

    fun confirmarDesativacao(destinationId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val request = _categoryMove.value ?: return@launch
        if (destinationId == request.category.id) return@launch
        database.withTransaction {
            database.notaDao().moverCategoria(request.category.id, destinationId)
            database.palavraChaveDao().moverCategoria(request.category.id, destinationId)
            database.productOverrideDao().moverCategoria(request.category.id, destinationId)
            database.categoriaDao().definirAtiva(request.category.id, false)
        }
        _categoryMove.value = null
    }

    fun criarPalavra(termo: String, category: CategoriaEntity, tipo: String = if (termo.trim().contains(' ')) "FRASE" else "PALAVRA") = viewModelScope.launch(Dispatchers.IO) {
        val original = termo.trim()
        if (original.isBlank()) return@launch
        database.palavraChaveDao().inserir(PalavraChaveEntity(termo = original, termoNormalizado = Normalizador.normalizar(original), categoriaId = category.id, prioridade = if (tipo == "FRASE") 20 else 10, tipoCorrespondencia = tipo, origem = "USUARIO"))
    }

    fun desativarPalavra(word: PalavraChaveEntity) = viewModelScope.launch(Dispatchers.IO) {
        database.palavraChaveDao().definirAtiva(word.id, false)
    }

    fun atualizarPalavra(word: PalavraChaveEntity, termo: String) = viewModelScope.launch(Dispatchers.IO) {
        val cleaned = termo.trim()
        if (cleaned.isNotBlank()) database.palavraChaveDao().atualizar(
            id = word.id,
            termo = cleaned,
            normalizado = Normalizador.normalizar(cleaned),
            prioridade = word.prioridade,
            tipo = if (' ' in cleaned.trim()) "FRASE" else "PALAVRA"
        )
    }

    fun excluirOverride(id: Long) = viewModelScope.launch(Dispatchers.IO) { database.productOverrideDao().excluir(id) }

    fun exportar(nota: NotaComItens): String {
        val safe = nota.itens.map { item ->
            mapOf(
                "descricao" to item.descricao,
                "codigo" to item.codigo,
                "quantidade" to item.quantidade,
                "unidade" to item.unidade,
                "valorUnitario" to item.valorUnitario,
                "valorTotal" to item.valorTotal,
                "categoria" to categorias.value.firstOrNull { it.id == item.categoriaId }?.nome
            )
        }
        return com.google.gson.Gson().toJson(mapOf(
            "chave" to nota.nota.chave,
            "cnpjEmitente" to nota.nota.cnpj,
            "emitente" to nota.nota.emitente,
            "dataEmissao" to nota.nota.dataEmissao,
            "total" to nota.nota.total,
            "itens" to safe
        ))
    }
}
