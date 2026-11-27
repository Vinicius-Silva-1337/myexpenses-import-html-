package br.com.feira.nfce.financeiro

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.financeiro.data.UserEntity
import br.com.feira.nfce.financeiro.data.LegacyDatabaseImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacyDatabaseImporterTest {
    @Test fun importsReadOnlyLegacyRowsAndReportsRoundingIdempotently() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val sourceFile=File(context.cacheDir,"legacy-import-test-${System.nanoTime()}.db")
        val source=SQLiteDatabase.openOrCreateDatabase(sourceFile,null)
        source.execSQL("CREATE TABLE usuarios(id INTEGER PRIMARY KEY,nome TEXT,email TEXT,senha_hash TEXT)")
        source.execSQL("CREATE TABLE transacoes(id INTEGER PRIMARY KEY,usuario_id INTEGER,tipo TEXT,descricao TEXT,valor REAL,categoria TEXT,subcategoria TEXT,conta TEXT,data TEXT,origem TEXT,observacao TEXT)")
        source.execSQL("INSERT INTO usuarios VALUES(7,'Teste','legado@example.test','pbkdf2:sha256:1\$salt\$hash')")
        source.execSQL("INSERT INTO transacoes VALUES(31,7,'despesa','Compra',10.125,'Mercado',NULL,NULL,'2025-01-02',NULL,NULL)")
        source.close()
        val originalBytes=sourceFile.readBytes()
        try {
            val importer=LegacyDatabaseImporter(context,db)
            val report=importer.import(Uri.fromFile(sourceFile))
            assertEquals(1,report.imported["usuarios"])
            assertEquals(1,report.imported["transacoes"])
            assertEquals(1,report.roundedValues)
            val user=db.userDao().byEmail("legado@example.test")
            assertNotNull(user)
            val tx=db.financeDao().transactionsOnce(user!!.id).single()
            assertEquals(1013L,tx.amountCents)
            assertEquals(31L,tx.legacyId)
            assertEquals(report,importer.import(Uri.fromFile(sourceFile)))
            assertEquals(1,db.financeDao().transactionsOnce(user.id).size)
            assertEquals(true,originalBytes.contentEquals(sourceFile.readBytes()))
        } finally {
            sourceFile.delete()
            db.close()
        }
    }
}
