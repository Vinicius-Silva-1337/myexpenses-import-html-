package br.com.feira.nfce.financeiro.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.financeiro.data.*
import br.com.feira.nfce.financeiro.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

class FinanceViewModel(application:Application):AndroidViewModel(application) {
    private val db=AppDatabase.get(application)
    private val repository=FinanceRepository(db)
    private val session=br.com.feira.nfce.auth.SessionManager(application)
    private val owner=MutableStateFlow(session.userId()?:0L)
    private val month=MutableStateFlow(YearMonth.now())
    private val _message=MutableStateFlow<String?>(null)
    val message:StateFlow<String?> = _message.asStateFlow()
    val accounts=owner.flatMapLatest { if(it>0) repository.accounts(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val transactions=owner.flatMapLatest { if(it>0) repository.transactions(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val categories=owner.flatMapLatest { if(it>0) db.categoriaDao().observarTodas(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val subcategories=owner.flatMapLatest { if(it>0) repository.subcategories(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val budgets=owner.flatMapLatest { if(it>0) repository.budgets(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val fixed=owner.flatMapLatest { if(it>0) repository.fixedExpenses(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val transfers=owner.flatMapLatest { if(it>0) db.financeDao().transfers(it) else flowOf(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _summary=MutableStateFlow(MonthlySummary(0,0,0,0, emptyMap()))
    val summary:StateFlow<MonthlySummary> = _summary.asStateFlow()
    private val _accountBalances=MutableStateFlow<Map<Long,Long>>(emptyMap())
    val accountBalances:StateFlow<Map<Long,Long>> = _accountBalances.asStateFlow()
    private val _planning=MutableStateFlow<PlanningSettingsEntity?>(null)
    val planning:StateFlow<PlanningSettingsEntity?> = _planning.asStateFlow()
    private val _salary=MutableStateFlow<SalarySettingsEntity?>(null)
    val salary:StateFlow<SalarySettingsEntity?> = _salary.asStateFlow()
    private val _report=MutableStateFlow<ReportSummary?>(null)
    val report:StateFlow<ReportSummary?> = _report.asStateFlow()
    private val _migrationReport=MutableStateFlow<String?>(null)
    val migrationReport:StateFlow<String?> = _migrationReport.asStateFlow()

    fun setUser(id:Long) { owner.value=id; if(id>0) refresh() }
    fun dismissMessage(){_message.value=null}
    fun importLegacy(uri:android.net.Uri) { viewModelScope.launch(Dispatchers.IO) { try { val report=LegacyDatabaseImporter(getApplication(),db).import(uri);_migrationReport.value=report.asText();refresh() } catch(e:Exception){emit(e)} } }
    fun refresh(month:YearMonth=YearMonth.now()) {
        this.month.value=month
        val id=owner.value; if(id<=0)return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.summary(id,month) }.onSuccess { _summary.value=it }
            _accountBalances.value=repository.accountsOnce(id).associate { it.id to db.financeDao().accountBalanceCents(id,it.id) }
            _planning.value=repository.planning(id); _salary.value=repository.salary(id)
        }
    }
    fun navigateMonth(delta:Long) { refresh(month.value.plusMonths(delta)) }
    fun loadReport(year:Int,month:Int?,type:String) { val id=owner.value; viewModelScope.launch(Dispatchers.IO) { runCatching { repository.report(id,year,month,type) }.onSuccess {_report.value=it}.onFailure { emit(it) } } }
    private fun emit(error:Throwable){ _message.value=error.message?:"Não foi possível concluir a operação." }
    private fun action(block:suspend (Long)->Unit) { val id=owner.value; if(id<=0)return; viewModelScope.launch(Dispatchers.IO) { try { block(id); refresh() } catch(e:Exception){ emit(e) } } }

    fun createAccount(name:String,type:String,balance:String)=action { id->repository.createAccount(id,name,type,Money.parseSignedCents(balance)?:error("Informe um saldo válido.")) }
    fun updateAccount(account:AccountEntity,name:String,type:String,balance:String)=action { id->repository.updateAccount(id,account.id,name,type,Money.parseSignedCents(balance)?:error("Informe um saldo válido.")) }
    fun deleteAccount(account:AccountEntity)=action { id->repository.deleteAccount(id,account.id) }
    fun createTransaction(type:String,description:String,amount:String,categoryId:Long?,subId:Long?,accountId:Long?,date:String,observation:String?)=action { id->
        repository.createTransaction(id,type,description,Money.parseCents(amount)?:error("Informe um valor maior que zero."),categoryId?:error("Selecione uma categoria."),subId,accountId?:error("Selecione uma conta."),date,observation)
    }
    fun updateTransaction(item:TransactionEntity,type:String,description:String,amount:String,categoryId:Long?,subId:Long?,accountId:Long?,date:String,observation:String?)=action { id->repository.updateTransaction(id,item.id,type,description,Money.parseCents(amount)?:error("Informe um valor maior que zero."),categoryId?:error("Selecione uma categoria."),subId,accountId?:error("Selecione uma conta."),date,observation) }
    fun deleteTransaction(item:TransactionEntity)=action {id->repository.deleteTransaction(id,item.id)}
    fun transfer(source:Long?,destination:Long?,amount:String,date:String,note:String?)=action {id->repository.transfer(id,source?:error("Escolha a conta de origem."),destination?:error("Escolha a conta de destino."),Money.parseCents(amount)?:error("Informe um valor válido."),date,note)}
    fun addCategory(name:String)=action {id->repository.addCategory(id,name)}
    fun renameCategory(item:CategoriaEntity,name:String)=action {id->repository.renameCategory(id,item.id,name)}
    fun addSubcategory(category:Long?,name:String)=action {id->repository.addSubcategory(id,category?:error("Selecione a categoria."),name)}
    fun deleteSubcategory(id:Long)=action {ownerId->db.financeDao().deleteSubcategory(id,ownerId)}
    fun savePlan(income:String,percent:String)=action { id->
        val p=runCatching { BigDecimal(percent.replace(',','.')).multiply(BigDecimal(100)).setScale(0,RoundingMode.HALF_UP).intValueExact() }.getOrNull()?:error("Informe percentual entre 0 e 99,99.")
        repository.savePlan(id,Money.parseCents(income)?:error("Informe a renda mensal."),p)
    }
    fun saveBudget(categoryId:Long?,amount:String)=action { id->repository.saveBudget(id,categoryId,Money.parseCents(amount)?:error("Informe o limite.")) }
    fun deleteBudget(item:BudgetEntity)=action {id->db.financeDao().deleteBudget(item.id,id)}
    fun createFixed(desc:String,amount:String,category:Long?,account:Long?,day:String)=action {id->repository.addFixed(id,desc,Money.parseCents(amount)?:error("Informe o valor."),category?:error("Selecione uma categoria."),account?:error("Selecione uma conta."),day.toIntOrNull()?:error("Informe dia entre 1 e 31."))}
    fun updateFixed(item:FixedExpenseEntity,desc:String,amount:String,category:Long?,account:Long?,day:String)=action {id->repository.updateFixed(id,item,desc,Money.parseCents(amount)?:error("Informe o valor."),category?:error("Selecione uma categoria."),account?:error("Selecione uma conta."),day.toIntOrNull()?:error("Informe dia entre 1 e 31."))}
    fun payFixed(item:FixedExpenseEntity)=action {id->if(!repository.payFixed(id,item.id)) error("Esta despesa já foi paga neste mês.")}
    fun deleteFixed(item:FixedExpenseEntity)=action {id->db.financeDao().deleteFixed(item.id,id)}
    fun saveSalary(amount:String,day:String,account:Long?,active:Boolean)=action {id->repository.saveSalary(id,Money.parseCents(amount)?:error("Informe o valor."),day.toIntOrNull()?:error("Dia inválido."),account?:error("Selecione a conta."),active)}
    fun removeSalary()=action {id->db.financeDao().deleteSalary(id)}
    fun createInvoiceExpense(invoiceId:Long,categoryId:Long?,accountId:Long?,date:String)=action {id->repository.registerInvoiceAsExpense(id,invoiceId,categoryId?:error("Selecione uma categoria."),accountId?:error("Selecione uma conta."),date)}
    fun updateCategoryActive(category:CategoriaEntity,active:Boolean)=action {id->db.categoriaDao().definirAtiva(category.id,id,active)}
    fun editTransfer(item:TransferEntity,source:Long?,destination:Long?,amount:String,date:String,note:String?)=action { id->
        val from=repository.account(id,source?:0L)?:error("Conta de origem inválida")
        val to=repository.account(id,destination?:0L)?:error("Conta de destino inválida")
        require(from.id!=to.id && (Money.parseCents(amount)?:0L)>0)
        db.financeDao().updateTransfer(item.id,id,from.id,to.id,Money.parseCents(amount)!!,date,note)
    }
    fun deleteTransfer(item:TransferEntity)=action {id->db.financeDao().deleteTransfer(item.id,id)}
}
