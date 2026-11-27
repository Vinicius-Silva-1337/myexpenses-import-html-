package br.com.feira.nfce.categorias.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.feira.nfce.categorias.data.dao.CategoriaDao
import br.com.feira.nfce.categorias.data.dao.PalavraChaveDao
import br.com.feira.nfce.categorias.data.dao.ProductOverrideDao
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity
import br.com.feira.nfce.financeiro.data.*
import br.com.feira.nfce.notas.data.ItemNotaEntity
import br.com.feira.nfce.notas.data.NotaDao
import br.com.feira.nfce.notas.data.NotaFiscalEntity
import br.com.feira.nfce.notas.data.PagamentoEntity

@Database(
    entities = [CategoriaEntity::class, PalavraChaveEntity::class, ProductOverrideEntity::class, NotaFiscalEntity::class, ItemNotaEntity::class, PagamentoEntity::class,
        UserEntity::class, AccountEntity::class, SubcategoryEntity::class, TransactionEntity::class, TransferEntity::class, BudgetEntity::class,
        FixedExpenseEntity::class, SalarySettingsEntity::class, PlanningSettingsEntity::class, LegacyImportEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoriaDao(): CategoriaDao
    abstract fun palavraChaveDao(): PalavraChaveDao
    abstract fun productOverrideDao(): ProductOverrideDao
    abstract fun notaDao(): NotaDao
    abstract fun userDao(): UserDao
    abstract fun financeDao(): FinanceDao
    abstract fun legacyDao(): LegacyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN totalProdutos TEXT")
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN descontos TEXT")
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN acrescimos TEXT")
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN valorPago TEXT")
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN troco TEXT")
                database.execSQL("ALTER TABLE notas_fiscais ADD COLUMN quantidadeItensInformada INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,email TEXT NOT NULL,passwordHash TEXT NOT NULL,createdAt INTEGER NOT NULL,systemAccount INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)")
                db.execSQL("INSERT INTO users (id,name,email,passwordHash,createdAt,systemAccount) VALUES (1,'Dados locais importados','legacy-unclaimed@myexpenses.invalid','UNCLAIMED',0,1)")
                db.execSQL("ALTER TABLE categorias ADD COLUMN userId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP INDEX index_categorias_nomeNormalizado")
                db.execSQL("CREATE UNIQUE INDEX index_categorias_userId_nomeNormalizado ON categorias(userId,nomeNormalizado)")
                db.execSQL("ALTER TABLE palavras_chave ADD COLUMN userId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE product_overrides ADD COLUMN userId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP INDEX index_product_overrides_descricaoNormalizada")
                db.execSQL("CREATE UNIQUE INDEX index_product_overrides_userId_descricaoNormalizada ON product_overrides(userId,descricaoNormalizada)")
                db.execSQL("ALTER TABLE notas_fiscais ADD COLUMN userId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP INDEX index_notas_fiscais_chave")
                db.execSQL("CREATE UNIQUE INDEX index_notas_fiscais_userId_chave ON notas_fiscais(userId,chave)")

                db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,name TEXT NOT NULL,nameNormalized TEXT NOT NULL,type TEXT NOT NULL,openingBalanceCents INTEGER NOT NULL,legacyId INTEGER,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX index_accounts_userId_nameNormalized ON accounts(userId,nameNormalized)")
                db.execSQL("CREATE INDEX index_accounts_userId ON accounts(userId)")
                db.execSQL("CREATE TABLE subcategories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,categoryId INTEGER NOT NULL,name TEXT NOT NULL,legacyId INTEGER,FOREIGN KEY(categoryId) REFERENCES categorias(id) ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX index_subcategories_categoryId_name ON subcategories(categoryId,name)")
                db.execSQL("CREATE INDEX index_subcategories_categoryId ON subcategories(categoryId)")
                db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,type TEXT NOT NULL,description TEXT NOT NULL,amountCents INTEGER NOT NULL,categoryId INTEGER,categoryName TEXT NOT NULL,subcategoryId INTEGER,subcategoryName TEXT,accountId INTEGER,accountName TEXT,date TEXT NOT NULL,origin TEXT,observation TEXT,sourceInvoiceId INTEGER,legacyId INTEGER,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE SET NULL,FOREIGN KEY(categoryId) REFERENCES categorias(id) ON DELETE SET NULL,FOREIGN KEY(subcategoryId) REFERENCES subcategories(id) ON DELETE SET NULL,FOREIGN KEY(sourceInvoiceId) REFERENCES notas_fiscais(id) ON DELETE SET NULL)")
                listOf("userId", "accountId", "categoryId", "subcategoryId", "date").forEach { db.execSQL("CREATE INDEX index_transactions_$it ON transactions($it)") }
                db.execSQL("CREATE UNIQUE INDEX index_transactions_sourceInvoiceId ON transactions(sourceInvoiceId)")
                db.execSQL("CREATE TABLE transfers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,sourceAccountId INTEGER NOT NULL,destinationAccountId INTEGER NOT NULL,amountCents INTEGER NOT NULL,date TEXT NOT NULL,observation TEXT,legacySourceName TEXT,legacyDestinationName TEXT,legacyId INTEGER,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,FOREIGN KEY(sourceAccountId) REFERENCES accounts(id) ON DELETE RESTRICT,FOREIGN KEY(destinationAccountId) REFERENCES accounts(id) ON DELETE RESTRICT)")
                listOf("userId", "sourceAccountId", "destinationAccountId", "date").forEach { db.execSQL("CREATE INDEX index_transfers_$it ON transfers($it)") }
                db.execSQL("CREATE TABLE budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,categoryId INTEGER,categoryKey TEXT NOT NULL,amountCents INTEGER NOT NULL,legacyCategoryName TEXT,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,FOREIGN KEY(categoryId) REFERENCES categorias(id) ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_budgets_userId_categoryKey ON budgets(userId,categoryKey)")
                db.execSQL("CREATE INDEX index_budgets_categoryId ON budgets(categoryId)")
                db.execSQL("CREATE TABLE fixed_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,userId INTEGER NOT NULL,description TEXT NOT NULL,amountCents INTEGER NOT NULL,categoryId INTEGER,categoryName TEXT NOT NULL,accountId INTEGER,accountName TEXT NOT NULL,dueDay INTEGER NOT NULL,legacyId INTEGER,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE SET NULL,FOREIGN KEY(categoryId) REFERENCES categorias(id) ON DELETE SET NULL)")
                listOf("userId", "accountId", "categoryId").forEach { db.execSQL("CREATE INDEX index_fixed_expenses_$it ON fixed_expenses($it)") }
                db.execSQL("CREATE TABLE salary_settings (userId INTEGER NOT NULL PRIMARY KEY,amountCents INTEGER NOT NULL,dueDay INTEGER NOT NULL,accountId INTEGER,accountName TEXT NOT NULL,active INTEGER NOT NULL,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_salary_settings_userId ON salary_settings(userId)")
                db.execSQL("CREATE TABLE planning_settings (userId INTEGER NOT NULL PRIMARY KEY,incomeCents INTEGER NOT NULL,savingsPercent INTEGER NOT NULL,FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN legacyId INTEGER")
                db.execSQL("ALTER TABLE categorias ADD COLUMN legacyId INTEGER")
                db.execSQL("CREATE TABLE legacy_imports (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,fingerprint TEXT NOT NULL,importedAt INTEGER NOT NULL,reportJson TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_legacy_imports_fingerprint ON legacy_imports(fingerprint)")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "feira_nfce.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
