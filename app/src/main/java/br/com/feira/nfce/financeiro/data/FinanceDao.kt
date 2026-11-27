package br.com.feira.nfce.financeiro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE lower(email) = lower(:email) AND systemAccount = 0 LIMIT 1") suspend fun byEmail(email: String): UserEntity?
    @Query("SELECT * FROM users WHERE id = :id AND systemAccount = 0") suspend fun byId(id: Long): UserEntity?
    @Query("SELECT COUNT(*) FROM users WHERE systemAccount = 0") suspend fun realUserCount(): Int
    @Query("SELECT id FROM users WHERE systemAccount = 1 LIMIT 1") suspend fun unclaimedId(): Long?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(user: UserEntity): Long
    @Query("UPDATE users SET passwordHash = :hash WHERE id = :id") suspend fun updatePassword(id: Long, hash: String): Int
    @Query("SELECT * FROM users WHERE legacyId=:legacyId LIMIT 1") suspend fun byLegacyId(legacyId:Long):UserEntity?
    @Query("UPDATE users SET name=:name,passwordHash=:passwordHash WHERE id=:id") suspend fun updateLegacyUser(id:Long,name:String,passwordHash:String):Int
}

@Dao interface LegacyDao {
    @Query("SELECT * FROM legacy_imports WHERE fingerprint=:fingerprint LIMIT 1") suspend fun byFingerprint(fingerprint:String):LegacyImportEntity?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertImport(record:LegacyImportEntity):Long
    @Query("SELECT * FROM legacy_imports ORDER BY importedAt DESC") suspend fun reports():List<LegacyImportEntity>
}

@Dao
interface FinanceDao {
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertSubcategory(subcategory:SubcategoryEntity):Long
    @Query("SELECT * FROM subcategories WHERE id=:id AND userId=:userId") suspend fun subcategory(id:Long,userId:Long):SubcategoryEntity?
    @Query("SELECT * FROM subcategories WHERE categoryId=:categoryId AND userId=:userId ORDER BY name COLLATE NOCASE") suspend fun subcategoriesForCategory(categoryId:Long,userId:Long):List<SubcategoryEntity>
    @Query("DELETE FROM subcategories WHERE id=:id AND userId=:userId") suspend fun deleteSubcategory(id:Long,userId:Long):Int
    @Query("UPDATE transactions SET categoryName=:name WHERE userId=:userId AND categoryId=:categoryId") suspend fun renameTransactionCategory(userId:Long,categoryId:Long,name:String):Int
    @Query("UPDATE fixed_expenses SET categoryName=:name WHERE userId=:userId AND categoryId=:categoryId") suspend fun renameFixedCategory(userId:Long,categoryId:Long,name:String):Int
    @Query("UPDATE budgets SET categoryKey=:newKey,legacyCategoryName=:name WHERE userId=:userId AND categoryId=:categoryId") suspend fun renameBudgetCategory(userId:Long,categoryId:Long,newKey:String,name:String):Int
    @Query("UPDATE transactions SET accountName=:name WHERE userId=:userId AND accountId=:accountId") suspend fun renameTransactionAccount(userId:Long,accountId:Long,name:String):Int
    @Query("UPDATE fixed_expenses SET accountName=:name WHERE userId=:userId AND accountId=:accountId") suspend fun renameFixedAccount(userId:Long,accountId:Long,name:String):Int
    @Query("UPDATE salary_settings SET accountName=:name WHERE userId=:userId AND accountId=:accountId") suspend fun renameSalaryAccount(userId:Long,accountId:Long,name:String):Int
    @Query("SELECT COUNT(*) FROM salary_settings WHERE userId=:userId AND accountId=:accountId") suspend fun countSalaryForAccount(userId:Long,accountId:Long):Int
    @Query("SELECT (SELECT openingBalanceCents FROM accounts WHERE id=:accountId AND userId=:userId) + COALESCE((SELECT SUM(CASE type WHEN 'receita' THEN amountCents WHEN 'despesa' THEN -amountCents ELSE 0 END) FROM transactions WHERE userId=:userId AND accountId=:accountId),0) + COALESCE((SELECT SUM(amountCents) FROM transfers WHERE userId=:userId AND destinationAccountId=:accountId),0) - COALESCE((SELECT SUM(amountCents) FROM transfers WHERE userId=:userId AND sourceAccountId=:accountId),0)") suspend fun accountBalanceCents(userId:Long,accountId:Long):Long
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name COLLATE NOCASE") fun accounts(userId: Long): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name COLLATE NOCASE") suspend fun accountsOnce(userId: Long): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId") suspend fun account(id: Long, userId: Long): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(account: AccountEntity): Long
    @Query("UPDATE accounts SET name = :name, nameNormalized = :normalized, type = :type, openingBalanceCents = :opening WHERE id = :id AND userId = :userId") suspend fun updateAccount(id: Long, userId: Long, name: String, normalized: String, type: String, opening: Long): Int
    @Query("DELETE FROM accounts WHERE id = :id AND userId = :userId") suspend fun deleteAccount(id: Long, userId: Long): Int
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC, id DESC") fun transactions(userId: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC, id DESC") suspend fun transactionsOnce(userId: Long): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId") suspend fun transaction(id: Long, userId: Long): TransactionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransaction(transaction: TransactionEntity): Long
    @Query("UPDATE transactions SET type=:type, description=:description, amountCents=:amount, categoryId=:categoryId, categoryName=:categoryName, subcategoryId=:subcategoryId, subcategoryName=:subcategoryName, accountId=:accountId, accountName=:accountName, date=:date, observation=:observation WHERE id=:id AND userId=:userId") suspend fun updateTransaction(id: Long, userId: Long, type: String, description: String, amount: Long, categoryId: Long?, categoryName: String, subcategoryId: Long?, subcategoryName: String?, accountId: Long?, accountName: String?, date: String, observation: String?): Int
    @Query("DELETE FROM transactions WHERE id=:id AND userId=:userId") suspend fun deleteTransaction(id: Long, userId: Long): Int
    @Query("SELECT * FROM transfers WHERE userId=:userId ORDER BY date DESC, id DESC") fun transfers(userId: Long): Flow<List<TransferEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransfer(transfer: TransferEntity): Long
    @Query("UPDATE transfers SET sourceAccountId=:source, destinationAccountId=:destination, amountCents=:amount, date=:date, observation=:observation WHERE id=:id AND userId=:userId") suspend fun updateTransfer(id: Long, userId: Long, source: Long, destination: Long, amount: Long, date: String, observation: String?): Int
    @Query("DELETE FROM transfers WHERE id=:id AND userId=:userId") suspend fun deleteTransfer(id: Long, userId: Long): Int
    @Query("SELECT * FROM subcategories WHERE userId=:userId ORDER BY name COLLATE NOCASE") fun subcategories(userId: Long): Flow<List<SubcategoryEntity>>
    @Query("SELECT * FROM budgets WHERE userId=:userId ORDER BY categoryKey") fun budgets(userId: Long): Flow<List<BudgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBudget(budget: BudgetEntity): Long
    @Query("DELETE FROM budgets WHERE id=:id AND userId=:userId") suspend fun deleteBudget(id: Long, userId: Long): Int
    @Query("SELECT * FROM fixed_expenses WHERE userId=:userId ORDER BY dueDay, description") fun fixedExpenses(userId: Long): Flow<List<FixedExpenseEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFixed(expense: FixedExpenseEntity): Long
    @Query("UPDATE fixed_expenses SET description=:description,amountCents=:amount,categoryId=:categoryId,categoryName=:categoryName,accountId=:accountId,accountName=:accountName,dueDay=:day WHERE id=:id AND userId=:userId") suspend fun updateFixed(id: Long,userId: Long,description:String,amount:Long,categoryId:Long?,categoryName:String,accountId:Long?,accountName:String,day:Int):Int
    @Query("DELETE FROM fixed_expenses WHERE id=:id AND userId=:userId") suspend fun deleteFixed(id: Long,userId: Long):Int
    @Query("SELECT * FROM salary_settings WHERE userId=:userId LIMIT 1") suspend fun salary(userId: Long): SalarySettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSalary(salary: SalarySettingsEntity)
    @Query("DELETE FROM salary_settings WHERE userId=:userId") suspend fun deleteSalary(userId: Long):Int
    @Query("SELECT * FROM planning_settings WHERE userId=:userId LIMIT 1") suspend fun planning(userId: Long): PlanningSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlanning(settings: PlanningSettingsEntity)
    @Query("SELECT COALESCE((SELECT SUM(openingBalanceCents) FROM accounts WHERE userId=:userId),0) + COALESCE((SELECT SUM(CASE type WHEN 'receita' THEN amountCents WHEN 'despesa' THEN -amountCents ELSE 0 END) FROM transactions WHERE userId=:userId),0) + COALESCE((SELECT SUM(t.amountCents) FROM transfers t JOIN accounts a ON a.id=t.destinationAccountId WHERE t.userId=:userId AND a.userId=:userId),0) - COALESCE((SELECT SUM(t.amountCents) FROM transfers t JOIN accounts a ON a.id=t.sourceAccountId WHERE t.userId=:userId AND a.userId=:userId),0)") suspend fun totalBalanceCents(userId: Long): Long
    @Query("SELECT COUNT(*) FROM transactions WHERE userId=:userId AND origin=:origin") suspend fun countOrigin(userId: Long, origin: String): Int
    @Query("SELECT COUNT(*) FROM transfers WHERE userId=:userId AND (sourceAccountId=:accountId OR destinationAccountId=:accountId)") suspend fun countTransfersForAccount(userId: Long, accountId: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE userId=:userId AND accountId=:accountId") suspend fun countTransactionsForAccount(userId: Long, accountId: Long): Int
    @Query("SELECT COUNT(*) FROM fixed_expenses WHERE userId=:userId AND accountId=:accountId") suspend fun countFixedForAccount(userId: Long, accountId: Long): Int
    @Query("SELECT * FROM budgets WHERE userId=:userId AND categoryKey=:key LIMIT 1") suspend fun budget(userId: Long,key:String):BudgetEntity?
    @Query("SELECT * FROM categorias WHERE userId=:userId AND ativa=1 ORDER BY nome COLLATE NOCASE") suspend fun activeCategories(userId: Long):List<br.com.feira.nfce.categorias.data.entities.CategoriaEntity>
    @Query("SELECT id FROM transactions WHERE userId=:userId AND sourceInvoiceId=:invoiceId LIMIT 1") suspend fun transactionForInvoice(userId:Long,invoiceId:Long):Long?
}
