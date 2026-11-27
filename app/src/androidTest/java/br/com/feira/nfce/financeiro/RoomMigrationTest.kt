package br.com.feira.nfce.financeiro

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import br.com.feira.nfce.categorias.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @Test fun versionTwoNfceDataSurvivesUnifiedSchemaMigrations() {
        val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),AppDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
        val name="nfce-migration-${System.nanoTime()}"
        helper.createDatabase(name,2).apply {
            execSQL("INSERT INTO categorias(id,nome,nomeNormalizado,descricao,origem,ativa) VALUES(7,'Mercado','MERCADO',NULL,'PERSONALIZADA',1)")
            execSQL("INSERT INTO notas_fiscais(id,chave,cnpj,emitente,numero,serie,dataEmissao,total,somaItensConfere,somaItens,qtdItensConfere,chaveValida,importadaEm,totalProdutos,descontos,acrescimos,valorPago,troco,quantidadeItensInformada) VALUES(9,'chave-antiga','12345678000190','Loja','1','1',NULL,'12.50',1,'12.50',1,1,10,'12.50','0','0','12.50','0',1)")
            close()
        }
        val migrated=helper.runMigrationsAndValidate(name,4,true,AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4)
        migrated.query("SELECT userId,nome FROM categorias WHERE id=7").use { assertEquals(true,it.moveToFirst());assertEquals(1L,it.getLong(0));assertEquals("Mercado",it.getString(1)) }
        migrated.query("SELECT userId,total FROM notas_fiscais WHERE id=9").use { assertEquals(true,it.moveToFirst());assertEquals(1L,it.getLong(0));assertEquals("12.50",it.getString(1)) }
        migrated.query("SELECT systemAccount FROM users WHERE id=1").use { assertEquals(true,it.moveToFirst());assertEquals(1L,it.getLong(0)) }
        migrated.close()
    }
}
