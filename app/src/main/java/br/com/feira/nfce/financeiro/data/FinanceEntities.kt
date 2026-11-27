package br.com.feira.nfce.financeiro.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.notas.data.NotaFiscalEntity

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val systemAccount: Boolean = false,
    val legacyId: Long? = null
)

@Entity(tableName="legacy_imports",indices=[Index(value=["fingerprint"],unique=true)])
data class LegacyImportEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val fingerprint:String,val importedAt:Long=System.currentTimeMillis(),val reportJson:String)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["userId", "nameNormalized"], unique = true), Index("userId")]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val nameNormalized: String = name.trim(),
    val type: String = "corrente",
    val openingBalanceCents: Long = 0,
    val legacyId: Long? = null
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["categoryId", "name"], unique = true), Index("categoryId")]
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val categoryId: Long,
    val name: String,
    val legacyId: Long? = null
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SubcategoryEntity::class, parentColumns = ["id"], childColumns = ["subcategoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = NotaFiscalEntity::class, parentColumns = ["id"], childColumns = ["sourceInvoiceId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("userId"), Index("accountId"), Index("categoryId"), Index("subcategoryId"), Index("date"), Index(value = ["sourceInvoiceId"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val type: String,
    val description: String,
    val amountCents: Long,
    val categoryId: Long?,
    val categoryName: String,
    val subcategoryId: Long? = null,
    val subcategoryName: String? = null,
    val accountId: Long?,
    val accountName: String?,
    val date: String,
    val origin: String? = null,
    val observation: String? = null,
    val sourceInvoiceId: Long? = null,
    val legacyId: Long? = null
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["sourceAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("userId"), Index("sourceAccountId"), Index("destinationAccountId"), Index("date")]
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val amountCents: Long,
    val date: String,
    val observation: String? = null,
    val legacySourceName: String? = null,
    val legacyDestinationName: String? = null,
    val legacyId: Long? = null
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["userId", "categoryKey"], unique = true), Index("categoryId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val categoryId: Long?,
    val categoryKey: String,
    val amountCents: Long,
    val legacyCategoryName: String? = null
)

@Entity(
    tableName = "fixed_expenses",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("userId"), Index("accountId"), Index("categoryId")]
)
data class FixedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val description: String,
    val amountCents: Long,
    val categoryId: Long?,
    val categoryName: String,
    val accountId: Long?,
    val accountName: String,
    val dueDay: Int,
    val legacyId: Long? = null
)

@Entity(tableName = "salary_settings", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
data class SalarySettingsEntity(
    @PrimaryKey val userId: Long,
    val amountCents: Long,
    val dueDay: Int,
    val accountId: Long?,
    val accountName: String,
    val active: Boolean
)

@Entity(tableName = "planning_settings", foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)])
data class PlanningSettingsEntity(
    @PrimaryKey val userId: Long,
    val incomeCents: Long,
    /** Percentage in hundredths: 12.50% is stored as 1250. */
    val savingsPercent: Int
)
