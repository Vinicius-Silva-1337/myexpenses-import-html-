package br.com.feira.nfce.financeiro.domain

import androidx.room.withTransaction
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.financeiro.data.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.first

data class MonthlySummary(val incomeCents:Long,val expenseCents:Long,val transactionBalanceCents:Long,val accountBalanceCents:Long,val categoryExpenses:Map<String,Long>)
data class ReportSummary(val incomeCents:Long,val expenseCents:Long,val byCategory:Map<String,Long>,val bySubcategory:Map<String,Long>)

class FinanceRepository(private val db:AppDatabase) {
    private val dao=db.financeDao()
    fun accounts(userId:Long):Flow<List<AccountEntity>> = dao.accounts(userId)
    suspend fun accountsOnce(userId:Long)=dao.accountsOnce(userId)
    fun transactions(userId:Long):Flow<List<TransactionEntity>> = dao.transactions(userId)
    fun fixedExpenses(userId:Long):Flow<List<FixedExpenseEntity>> = dao.fixedExpenses(userId)
    fun budgets(userId:Long):Flow<List<BudgetEntity>> = dao.budgets(userId)
    fun subcategories(userId:Long):Flow<List<SubcategoryEntity>> = dao.subcategories(userId)
    suspend fun account(userId:Long,id:Long)=dao.account(id,userId)

    suspend fun summary(userId:Long,month:YearMonth):MonthlySummary {
        val tx=dao.transactionsOnce(userId).filter { it.date.startsWith(month.toString()) }
        val income=tx.filter { it.type=="receita" }.sumOf { it.amountCents }
        val expenses=tx.filter { it.type=="despesa" }.sumOf { it.amountCents }
        return MonthlySummary(income,expenses,income-expenses,dao.totalBalanceCents(userId),tx.filter { it.type=="despesa" }.groupBy { it.categoryName }.mapValues { (_,v)->v.sumOf { it.amountCents } })
    }

    suspend fun report(userId:Long,year:Int,month:Int?=null,type:String):ReportSummary {
        val period=dao.transactionsOnce(userId).filter { it.date.startsWith(if(month==null) year.toString() else "%04d-%02d".format(year,month)) }
        val tx=period.filter { it.type==type }
        return ReportSummary(
            incomeCents=period.filter { it.type=="receita" }.sumOf { it.amountCents },
            expenseCents=period.filter { it.type=="despesa" }.sumOf { it.amountCents },
            byCategory=tx.groupBy { it.categoryName }.mapValues { (_,v)->v.sumOf { it.amountCents } },
            bySubcategory=tx.groupBy { it.subcategoryName?:"Sem subcategoria" }.mapValues { (_,v)->v.sumOf { it.amountCents } }
        )
    }

    suspend fun createAccount(userId:Long,name:String,type:String,openingCents:Long):Long {
        require(name.trim().length in 1..40 && type in ACCOUNT_TYPES)
        return dao.insertAccount(AccountEntity(userId=userId,name=name.trim(),nameNormalized=name.trim(),type=type,openingBalanceCents=openingCents))
    }
    suspend fun updateAccount(userId:Long,id:Long,name:String,type:String,openingCents:Long) = db.withTransaction {
        val old=dao.account(id,userId)?:error("Conta não encontrada")
        require(name.trim().length in 1..40 && type in ACCOUNT_TYPES)
        dao.updateAccount(id,userId,name.trim(),name.trim(),type,openingCents)
        if(old.name!=name.trim()) {
            dao.renameTransactionAccount(userId,id,name.trim()); dao.renameFixedAccount(userId,id,name.trim()); dao.renameSalaryAccount(userId,id,name.trim())
        }
    }
    suspend fun deleteAccount(userId:Long,id:Long):Boolean {
        require(dao.account(id,userId)!=null)
        require(dao.countTransactionsForAccount(userId,id)==0 && dao.countTransfersForAccount(userId,id)==0 && dao.countFixedForAccount(userId,id)==0 && dao.countSalaryForAccount(userId,id)==0) { "A conta ainda está vinculada a lançamentos." }
        return dao.deleteAccount(id,userId)>0
    }

    suspend fun createTransaction(userId:Long,type:String,description:String,amountCents:Long,categoryId:Long,subcategoryId:Long?,accountId:Long,date:String,observation:String?=null):Long = db.withTransaction {
        require(type in setOf("receita","despesa") && description.trim().length in 1..80 && amountCents>0)
        val category=db.categoriaDao().porId(categoryId,userId)?:error("Categoria inválida")
        val account=dao.account(accountId,userId)?:error("Conta inválida")
        val sub= if(subcategoryId==null) null else dao.subcategory(subcategoryId,userId)?.also { require(it.categoryId==category.id) }?:error("Subcategoria inválida")
        LocalDate.parse(date)
        dao.insertTransaction(TransactionEntity(userId=userId,type=type,description=description.trim(),amountCents=amountCents,categoryId=category.id,categoryName=category.nome,subcategoryId=sub?.id,subcategoryName=sub?.name,accountId=account.id,accountName=account.name,date=date,observation=observation?.trim()?.take(200)?.ifBlank { null }))
    }
    suspend fun updateTransaction(userId:Long,id:Long,type:String,description:String,amountCents:Long,categoryId:Long,subcategoryId:Long?,accountId:Long,date:String,observation:String?)=db.withTransaction {
        require(dao.transaction(id,userId)!=null)
        require(type in setOf("receita","despesa") && description.trim().length in 1..80 && amountCents>0)
        val category=db.categoriaDao().porId(categoryId,userId)?:error("Categoria inválida")
        val account=dao.account(accountId,userId)?:error("Conta inválida")
        val sub=if(subcategoryId==null)null else dao.subcategory(subcategoryId,userId)?.also { require(it.categoryId==category.id) }?:error("Subcategoria inválida")
        LocalDate.parse(date)
        dao.updateTransaction(id,userId,type,description.trim(),amountCents,category.id,category.nome,sub?.id,sub?.name,account.id,account.name,date,observation?.trim()?.take(200)?.ifBlank { null })
    }
    suspend fun deleteTransaction(userId:Long,id:Long)=dao.deleteTransaction(id,userId)>0
    suspend fun registerInvoiceAsExpense(userId:Long,invoiceId:Long,categoryId:Long,accountId:Long,date:String):Boolean=db.withTransaction {
        val invoice=db.notaDao().invoiceForUser(invoiceId,userId)?:error("Nota fiscal não encontrada")
        require(dao.transactionForInvoice(userId,invoiceId)==null) { "Esta nota já foi registrada como despesa." }
        val category=db.categoriaDao().porId(categoryId,userId)?:error("Categoria inválida")
        val account=dao.account(accountId,userId)?:error("Conta inválida")
        val cents=BigDecimal(invoice.total).setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        require(cents>0)
        dao.insertTransaction(TransactionEntity(userId=userId,type="despesa",description="NFC-e · ${invoice.emitente.take(60)}",amountCents=cents,categoryId=category.id,categoryName=category.nome,accountId=account.id,accountName=account.name,date=date,origin="nfce:$invoiceId",sourceInvoiceId=invoiceId))>0
    }

    suspend fun transfer(userId:Long,sourceId:Long,destinationId:Long,amountCents:Long,date:String,observation:String?) {
        require(sourceId!=destinationId && amountCents>0); LocalDate.parse(date)
        db.withTransaction {
            val source=dao.account(sourceId,userId)?:error("Conta de origem inválida")
            val destination=dao.account(destinationId,userId)?:error("Conta de destino inválida")
            dao.insertTransfer(TransferEntity(userId=userId,sourceAccountId=source.id,destinationAccountId=destination.id,amountCents=amountCents,date=date,observation=observation?.take(200)))
        }
    }

    suspend fun addCategory(userId:Long,name:String):Long {
        require(name.trim().length in 1..30)
        return db.categoriaDao().inserir(CategoriaEntity(userId=userId,nome=name.trim(),origem="PERSONALIZADA")).also { require(it>0) { "Já existe uma categoria com esse nome." } }
    }
    suspend fun addSubcategory(userId:Long,categoryId:Long,name:String):Long {
        require(db.categoriaDao().porId(categoryId,userId)!=null && name.trim().length in 1..30)
        return dao.insertSubcategory(SubcategoryEntity(userId=userId,categoryId=categoryId,name=name.trim()))
    }
    suspend fun renameCategory(userId:Long,id:Long,name:String) = db.withTransaction {
        require(name.trim().length in 1..30)
        require(db.categoriaDao().renomear(id,userId,name.trim(),name.trim().uppercase(),null)>0) { "Categoria padrão não pode ser renomeada." }
        dao.renameTransactionCategory(userId,id,name.trim()); dao.renameFixedCategory(userId,id,name.trim()); dao.renameBudgetCategory(userId,id,name.trim().lowercase(),name.trim())
    }

    suspend fun savePlan(userId:Long,incomeCents:Long,savingsPercent:Int) {
        require(incomeCents>0 && savingsPercent in 0..9999)
        db.withTransaction {
            dao.savePlanning(PlanningSettingsEntity(userId,incomeCents,savingsPercent))
            val budget=BigDecimal.valueOf(incomeCents).multiply(BigDecimal.valueOf((10_000-savingsPercent).toLong())).divide(BigDecimal.valueOf(10_000),0,RoundingMode.HALF_EVEN).longValueExact()
            dao.saveBudget(BudgetEntity(userId=userId,categoryId=null,categoryKey="*",amountCents=budget))
        }
    }
    suspend fun saveBudget(userId:Long,categoryId:Long?,amountCents:Long) {
        require(amountCents>0)
        val key=if(categoryId==null) "*" else db.categoriaDao().porId(categoryId,userId)?.let { normalize(it.nome) }?:error("Categoria inválida")
        val existing=dao.budgets(userId).first()
        if(categoryId==null) {
            val month=YearMonth.now().toString();val spent=dao.transactionsOnce(userId).filter{it.type=="despesa"&&it.date.startsWith(month)}.sumOf{it.amountCents}
            require(amountCents<=dao.totalBalanceCents(userId)+spent){"O limite geral não pode passar do valor disponível para o mês."}
        } else {
            val total=existing.firstOrNull{it.categoryKey=="*"}?.amountCents?:Long.MAX_VALUE
            val used=existing.filter{it.categoryId!=null&&it.categoryId!=categoryId}.sumOf{it.amountCents}
            require(amountCents+used<=total){"A soma dos limites por categoria passa do limite geral."}
        }
        dao.saveBudget(BudgetEntity(userId=userId,categoryId=categoryId,categoryKey=key,amountCents=amountCents))
    }
    suspend fun saveSalary(userId:Long,amountCents:Long,day:Int,accountId:Long,active:Boolean) {
        require(amountCents>0 && day in 1..31)
        val account=dao.account(accountId,userId)?:error("Conta inválida")
        dao.saveSalary(SalarySettingsEntity(userId,amountCents,day,accountId,account.name,active))
    }
    suspend fun applySalary(userId:Long,today:LocalDate=LocalDate.now()):Boolean {
        val salary=dao.salary(userId)?.takeIf { it.active }?:return false
        val date=YearMonth.from(today).atDay(minOf(salary.dueDay,YearMonth.from(today).lengthOfMonth()))
        val origin="salario:${YearMonth.from(today)}"
        if(today.isBefore(date)||dao.countOrigin(userId,origin)>0)return false
        val category=db.categoriaDao().porNome("Salário",userId) ?: db.categoriaDao().ativas(userId).firstOrNull() ?: return false
        return db.withTransaction { dao.insertTransaction(TransactionEntity(userId=userId,type="receita",description="Salário",amountCents=salary.amountCents,categoryId=category.id,categoryName=category.nome,accountId=salary.accountId,accountName=salary.accountName,date=date.toString(),origin=origin))>0 }
    }
    suspend fun addFixed(userId:Long,description:String,amountCents:Long,categoryId:Long,accountId:Long,dueDay:Int):Long {
        require(description.trim().length in 1..60 && amountCents>0 && dueDay in 1..31)
        val c=db.categoriaDao().porId(categoryId,userId)?:error("Categoria inválida"); val a=dao.account(accountId,userId)?:error("Conta inválida")
        return dao.insertFixed(FixedExpenseEntity(userId=userId,description=description.trim(),amountCents=amountCents,categoryId=c.id,categoryName=c.nome,accountId=a.id,accountName=a.name,dueDay=dueDay))
    }
    suspend fun updateFixed(userId:Long,item:FixedExpenseEntity,description:String,amountCents:Long,categoryId:Long,accountId:Long,dueDay:Int) {
        require(description.trim().length in 1..60&&amountCents>0&&dueDay in 1..31)
        val c=db.categoriaDao().porId(categoryId,userId)?:error("Categoria inválida");val a=dao.account(accountId,userId)?:error("Conta inválida")
        require(dao.updateFixed(item.id,userId,description.trim(),amountCents,c.id,c.nome,a.id,a.name,dueDay)>0){"Despesa fixa não encontrada."}
    }
    suspend fun payFixed(userId:Long,id:Long,today:LocalDate=LocalDate.now()):Boolean = db.withTransaction {
        val item=db.openHelper.readableDatabase.query("SELECT * FROM fixed_expenses WHERE id=? AND userId=?",arrayOf(id,userId)).use { c -> if(!c.moveToFirst())null else FixedExpenseEntity(id=id,userId=userId,description=c.getString(c.getColumnIndexOrThrow("description")),amountCents=c.getLong(c.getColumnIndexOrThrow("amountCents")),categoryId=c.getLong(c.getColumnIndexOrThrow("categoryId")).takeIf { !c.isNull(c.getColumnIndexOrThrow("categoryId")) },categoryName=c.getString(c.getColumnIndexOrThrow("categoryName")),accountId=c.getLong(c.getColumnIndexOrThrow("accountId")).takeIf { !c.isNull(c.getColumnIndexOrThrow("accountId")) },accountName=c.getString(c.getColumnIndexOrThrow("accountName")),dueDay=c.getInt(c.getColumnIndexOrThrow("dueDay"))) }?:return@withTransaction false
        val month=YearMonth.from(today); val origin="fixa:$id:$month"
        if(dao.countOrigin(userId,origin)>0)return@withTransaction false
        dao.insertTransaction(TransactionEntity(userId=userId,type="despesa",description=item.description,amountCents=item.amountCents,categoryId=item.categoryId,categoryName=item.categoryName,accountId=item.accountId,accountName=item.accountName,date=today.toString(),origin=origin))>0
    }
    suspend fun planning(userId:Long)=dao.planning(userId)
    suspend fun salary(userId:Long)=dao.salary(userId)

    private fun normalize(value:String)=value.trim().lowercase().replace(Regex("\\s+")," ")
    companion object { val ACCOUNT_TYPES=setOf("corrente","poupanca","carteira","investimento") }
}
