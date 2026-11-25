package br.com.feira.nfce.categorias.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import br.com.feira.nfce.categorias.data.dao.CategoriaDao
import br.com.feira.nfce.categorias.data.dao.PalavraChaveDao
import br.com.feira.nfce.categorias.data.dao.ProductOverrideDao
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity
import br.com.feira.nfce.notas.data.ItemNotaEntity
import br.com.feira.nfce.notas.data.NotaDao
import br.com.feira.nfce.notas.data.NotaFiscalEntity
import br.com.feira.nfce.notas.data.PagamentoEntity

@Database(
    entities = [CategoriaEntity::class, PalavraChaveEntity::class, ProductOverrideEntity::class, NotaFiscalEntity::class, ItemNotaEntity::class, PagamentoEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoriaDao(): CategoriaDao
    abstract fun palavraChaveDao(): PalavraChaveDao
    abstract fun productOverrideDao(): ProductOverrideDao
    abstract fun notaDao(): NotaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "feira_nfce.db")
                .build().also { instance = it }
        }
    }
}
