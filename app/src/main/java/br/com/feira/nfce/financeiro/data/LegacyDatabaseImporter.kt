package br.com.feira.nfce.financeiro.data

import android.content.Context
import android.net.Uri
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.financeiro.domain.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant

data class LegacyImportReport(val fingerprint:String,val imported:Map<String,Int>,val skipped:Map<String,Int>,val conflicts:List<String>,val roundedValues:Int) {
    fun asText():String=buildString {
        appendLine("Banco: ${fingerprint.take(16)}…")
        imported.forEach{(table,count)->appendLine("$table: $count importados")}
        skipped.forEach{(table,count)->if(count>0)appendLine("$table: $count ignorados")}
        appendLine("Valores arredondados para centavos: $roundedValues")
        if(conflicts.isNotEmpty()){appendLine("Conflitos/avisos:");conflicts.take(30).forEach{appendLine("• $it")}}
    }
}

/** Reads the selected legacy SQLite file read-only. It never updates or removes that file. */
class LegacyDatabaseImporter(private val context:Context,private val appDb:AppDatabase) {
    suspend fun import(uri:Uri):LegacyImportReport=withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não foi possível ler o banco selecionado.")
        require(bytes.size in 1..250_000_000) { "O arquivo não parece ser um banco SQLite válido ou excede o limite de 250 MB." }
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        appDb.legacyDao().byFingerprint(fingerprint)?.let { return@withContext com.google.gson.Gson().fromJson(it.reportJson,LegacyImportReport::class.java) }
        val file=File(context.cacheDir,"legacy-$fingerprint.db");file.writeBytes(bytes)
        try {
            val source=SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READONLY)
            try { importSource(source,fingerprint) } finally { source.close() }
        } finally { file.delete() }
    }

    private suspend fun importSource(source:SQLiteDatabase,fingerprint:String):LegacyImportReport {
        val integrity=source.rawQuery("PRAGMA integrity_check",null).use { if(it.moveToFirst())it.getString(0) else "" }
        require(integrity=="ok") { "O banco selecionado não passou na verificação de integridade." }
        val tables=source.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null).use { c->buildSet{while(c.moveToNext())add(c.getString(0))} }
        require("usuarios" in tables && "transacoes" in tables) { "O banco não tem o esquema esperado do Meus Gastos." }
        val imported=linkedMapOf<String,Int>();val skipped=linkedMapOf<String,Int>();val conflicts=mutableListOf<String>();var rounded=0
        fun rows(table:String):List<Map<String,Any?>> = if(table !in tables) emptyList() else source.rawQuery("SELECT * FROM \"$table\"",null).use { c->buildList{while(c.moveToNext())add(c.row())} }
        fun money(value:Any?):Long? {
            val raw=when(value){null->return null;is Number->BigDecimal.valueOf(value.toDouble());else->runCatching{BigDecimal(value.toString())}.getOrNull()?:return null}
            if(!raw.toDouble().isFinite())return null
            val cents=raw.movePointRight(2);if(cents.stripTrailingZeros().scale()>0)rounded++
            return cents.setScale(0,RoundingMode.HALF_UP).longValueExact()
        }
        val users=rows("usuarios");val accountRows=rows("contas");val categoryRows=rows("categorias");val subRows=rows("subcategorias");val transactionRows=rows("transacoes");val transferRows=rows("transferencias");val fixedRows=rows("despesas_fixas");val limits=rows("limites");val salaries=rows("salario");val plans=rows("plano")
        val userMap=mutableMapOf<Long,Long>();val categoryMap=mutableMapOf<Pair<Long,Long>,Long>()
        appDb.withTransaction {
            for(row in users) {
                val oldId=row.long("id");val email=row.string("email").trim().lowercase();val display=row.string("nome").ifBlank { email }
                if(email.isBlank()){skipped["usuarios"]=(skipped["usuarios"]?:0)+1;conflicts+="Usuário legado $oldId sem e-mail válido.";continue}
                val existing=appDb.userDao().byEmail(email)
                val id=if(existing!=null) { conflicts+="E-mail já existente: dados legados associados à conta local existente.";existing.id }
                    else {
                        val hash=row.string("senha_hash")
                        if(!hash.startsWith("scrypt:")&&!hash.startsWith("pbkdf2:sha256:"))conflicts+="Hash legado não reconhecido para $email; será necessário redefinir a senha."
                        appDb.userDao().insert(UserEntity(name=display,email=email,passwordHash=hash,legacyId=oldId))
                    }
                userMap[oldId]=id;imported["usuarios"]=(imported["usuarios"]?:0)+1
            }
            val finance=appDb.financeDao()
            suspend fun category(userId:Long,name:String,legacyId:Long?=null):CategoriaEntity {
                val existing=appDb.categoriaDao().ativas(userId)
                existing.firstOrNull{it.nome==name}?.let{return it}
                existing.firstOrNull{it.origem=="LEGADA"&&it.nome.startsWith("$name · legado ",true)}?.let{return it}
                var preservedName=name
                if(existing.any{it.nome.equals(name,true)}) {
                    preservedName="$name · legado ${legacyId?:"referência"}"
                    conflicts+="Colisão de categoria por capitalização: '$name' preservada como '$preservedName'."
                }
                var id=appDb.categoriaDao().inserir(CategoriaEntity(userId=userId,nome=preservedName,origem="LEGADA",legacyId=legacyId))
                if(id<0) {
                    preservedName="$name · legado ${legacyId?:System.nanoTime()}"
                    id=appDb.categoriaDao().inserir(CategoriaEntity(userId=userId,nome=preservedName,origem="LEGADA",legacyId=legacyId))
                }
                return appDb.categoriaDao().porId(id,userId)?:error("Não foi possível criar categoria legada")
            }
            for(row in categoryRows) {
                val owner=userMap[row.long("usuario_id")]?:continue;val oldId=row.long("id");val name=row.string("nome").trim()
                if(name.isBlank()){skipped["categorias"]=(skipped["categorias"]?:0)+1;continue}
                val value=category(owner,name,oldId);categoryMap[owner to oldId]=value.id;imported["categorias"]=(imported["categorias"]?:0)+1
            }
            for(row in accountRows) {
                val owner=userMap[row.long("usuario_id")]?:continue;val oldId=row.long("id");val name=row.string("nome").trim()
                if(name.isBlank()){skipped["contas"]=(skipped["contas"]?:0)+1;continue}
                val existing=finance.accountsOnce(owner).firstOrNull { it.name==name }
                val id=existing?.id?:finance.insertAccount(AccountEntity(userId=owner,name=name,nameNormalized=name,type=row.string("tipo").ifBlank{"corrente"},openingBalanceCents=money(row["saldo_inicial"])?:0,legacyId=oldId))
                if(existing!=null)conflicts+="Conta '$name' associada à conta já existente; saldo inicial legado não foi somado."
                imported["contas"]=(imported["contas"]?:0)+1
            }
            for(row in subRows) {
                val parentId=row.long("categoria_id");val parentRow=categoryRows.firstOrNull{it.long("id")==parentId}?:continue;val owner=userMap[parentRow.long("usuario_id")]?:continue
                val cat=categoryMap[owner to parentId]?:continue;val name=row.string("nome").trim();if(name.isBlank())continue
                val id=finance.insertSubcategory(SubcategoryEntity(userId=owner,categoryId=cat,name=name,legacyId=row.long("id")))
                imported["subcategorias"]=(imported["subcategorias"]?:0)+1
            }
            for(row in transactionRows) {
                val owner=userMap[row.long("usuario_id")]?:continue;val value=money(row["valor"])
                if(value==null||value<=0){skipped["transacoes"]=(skipped["transacoes"]?:0)+1;conflicts+="Transação ${row.long("id")} com valor inválido foi ignorada.";continue}
                val catName=row.string("categoria").ifBlank{"Sem categoria"};val cat=category(owner,catName)
                val accountName=row.nullableString("conta");val account=accountName?.let{name->finance.accountsOnce(owner).firstOrNull{it.name==name}}
                if(accountName!=null&&account==null)conflicts+="Transação ${row.long("id")} mantém a conta textual '$accountName', sem vínculo normalizado."
                val oldSubName=row.nullableString("subcategoria");val sub=if(oldSubName==null)null else finance.subcategoriesForCategory(cat.id,owner).firstOrNull{it.name==oldSubName}
                if(oldSubName!=null&&sub==null)conflicts+="Transação ${row.long("id")} mantém subcategoria textual '$oldSubName', sem correspondência."
                finance.insertTransaction(TransactionEntity(userId=owner,type=if(row.string("tipo")=="receita")"receita" else "despesa",description=row.string("descricao").take(80).ifBlank{"Lançamento legado"},amountCents=value,categoryId=cat.id,categoryName=catName,subcategoryId=sub?.id,subcategoryName=oldSubName,accountId=account?.id,accountName=accountName,date=row.string("data").take(10),origin=row.nullableString("origem"),observation=row.nullableString("observacao"),legacyId=row.long("id")))
                imported["transacoes"]=(imported["transacoes"]?:0)+1
            }
            for(row in transferRows) {
                val owner=userMap[row.long("usuario_id")]?:continue;val cents=money(row["valor"])
                if(cents==null||cents<=0){skipped["transferencias"]=(skipped["transferencias"]?:0)+1;continue}
                suspend fun account(name:String):AccountEntity { finance.accountsOnce(owner).firstOrNull{it.name==name}?.let{return it};val id=finance.insertAccount(AccountEntity(userId=owner,name=name,nameNormalized=name,type="corrente"));conflicts+="Conta '$name' criada para satisfazer referência de transferência.";return finance.account(owner,id)!! }
                val sourceName=row.string("origem");val destinationName=row.string("destino");val from=account(sourceName);val to=account(destinationName)
                if(from.id==to.id){skipped["transferencias"]=(skipped["transferencias"]?:0)+1;conflicts+="Transferência ${row.long("id")} tem a mesma conta de origem e destino.";continue}
                finance.insertTransfer(TransferEntity(userId=owner,sourceAccountId=from.id,destinationAccountId=to.id,amountCents=cents,date=row.string("data").take(10),observation=row.nullableString("observacao"),legacySourceName=sourceName,legacyDestinationName=destinationName,legacyId=row.long("id")))
                imported["transferencias"]=(imported["transferencias"]?:0)+1
            }
            for(row in fixedRows) {
                val owner=userMap[row.long("usuario_id")]?:continue;val cents=money(row["valor"]);if(cents==null||cents<=0){skipped["despesas_fixas"]=(skipped["despesas_fixas"]?:0)+1;continue}
                val cName=row.string("categoria");val cat=category(owner,cName);val aName=row.string("conta");var account=finance.accountsOnce(owner).firstOrNull{it.name==aName}
                if(account==null){val id=finance.insertAccount(AccountEntity(userId=owner,name=aName,nameNormalized=aName,type="corrente"));account=finance.account(owner,id);conflicts+="Conta '$aName' criada para despesa fixa órfã."}
                finance.insertFixed(FixedExpenseEntity(userId=owner,description=row.string("descricao").take(60),amountCents=cents,categoryId=cat.id,categoryName=cName,accountId=account!!.id,accountName=aName,dueDay=row.long("dia").toInt().coerceIn(1,31),legacyId=row.long("id")))
                imported["despesas_fixas"]=(imported["despesas_fixas"]?:0)+1
            }
            for(row in limits){val owner=userMap[row.long("usuario_id")]?:continue;val name=row.string("categoria");val cents=money(row["valor"]);if(cents==null||cents<=0){skipped["limites"]=(skipped["limites"]?:0)+1;continue};val cat=if(name=="*")null else category(owner,name);finance.saveBudget(BudgetEntity(userId=owner,categoryId=cat?.id,categoryKey=cat?.id?.toString()?:"*",amountCents=cents,legacyCategoryName=name.takeIf{it!="*"}));imported["limites"]=(imported["limites"]?:0)+1}
            for(row in salaries){val owner=userMap[row.long("usuario_id")]?:continue;val cents=money(row["valor"])?:continue;val name=row.string("conta");val acc=finance.accountsOnce(owner).firstOrNull{it.name==name};finance.saveSalary(SalarySettingsEntity(owner,cents,row.long("dia").toInt().coerceIn(1,31),acc?.id,name,row.long("ativo")!=0L));imported["salario"]=(imported["salario"]?:0)+1}
            for(row in plans){val owner=userMap[row.long("usuario_id")]?:continue;val income=money(row["renda"])?:continue;val percent=runCatching{BigDecimal.valueOf((row["economia_pct"] as Number).toDouble()).multiply(BigDecimal(100)).setScale(0,RoundingMode.HALF_UP).toInt()}.getOrDefault(0).coerceIn(0,9999);finance.savePlanning(PlanningSettingsEntity(owner,income,percent));imported["plano"]=(imported["plano"]?:0)+1}
            val totalRows=mapOf("usuarios" to users,"contas" to accountRows,"categorias" to categoryRows,"subcategorias" to subRows,"transacoes" to transactionRows,"transferencias" to transferRows,"despesas_fixas" to fixedRows,"limites" to limits,"salario" to salaries,"plano" to plans)
            totalRows.forEach{(table,rows)->skipped[table]=((rows.size-(imported[table]?:0)).coerceAtLeast(0))}
            val report=LegacyImportReport(fingerprint,imported,skipped,conflicts,rounded)
            appDb.legacyDao().insertImport(LegacyImportEntity(fingerprint=fingerprint,reportJson=com.google.gson.Gson().toJson(report)))
        }
        return LegacyImportReport(fingerprint,imported,skipped,conflicts,rounded)
    }
}

private fun Cursor.row():Map<String,Any?> = buildMap { for(i in 0 until columnCount) put(getColumnName(i),when(getType(i)){Cursor.FIELD_TYPE_NULL->null;Cursor.FIELD_TYPE_INTEGER->getLong(i);Cursor.FIELD_TYPE_FLOAT->getDouble(i);else->getString(i)}) }
private fun Map<String,Any?>.string(key:String)=this[key]?.toString().orEmpty()
private fun Map<String,Any?>.nullableString(key:String)=this[key]?.toString()
private fun Map<String,Any?>.long(key:String)=(this[key] as? Number)?.toLong()?:0L
