package br.com.feira.nfce.financeiro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.financeiro.data.UserEntity
import br.com.feira.nfce.financeiro.domain.FinanceRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    private lateinit var db:AppDatabase
    private lateinit var repo:FinanceRepository
    private var userA=0L;private var userB=0L;private var accountA=0L;private var accountB=0L;private var categoryA=0L
    @Before fun setup()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build();repo=FinanceRepository(db)
        userA=db.userDao().insert(UserEntity(name="A",email="a@test.local",passwordHash="test"));userB=db.userDao().insert(UserEntity(name="B",email="b@test.local",passwordHash="test"))
        accountA=repo.createAccount(userA,"Carteira","carteira",100_00);accountB=repo.createAccount(userA,"Banco","corrente",0)
        repo.createAccount(userB,"Carteira","carteira",900_00)
        categoryA=db.categoriaDao().inserir(CategoriaEntity(userId=userA,nome="Alimentação",origem="PERSONALIZADA"))
    }
    @After fun close(){db.close()}

    @Test fun transactionsAndTransfersKeepBalancesScopedAndExact()=runBlocking {
        repo.createTransaction(userA,"despesa","Mercado",12_34,categoryA,null,accountA,"2026-11-20")
        repo.transfer(userA,accountA,accountB,10_00,"2026-11-21",null)
        assertEquals(77_66L,db.financeDao().accountBalanceCents(userA,accountA))
        assertEquals(10_00L,db.financeDao().accountBalanceCents(userA,accountB))
        assertEquals(87_66L,db.financeDao().totalBalanceCents(userA))
        assertEquals(900_00L,db.financeDao().totalBalanceCents(userB))
        assertEquals(1,repo.transactions(userA).first().size)
    }

    @Test fun salaryAndFixedPaymentAreIdempotentPerMonth()=runBlocking {
        repo.saveSalary(userA,250_000,LocalDate.now().dayOfMonth,accountB,true)
        assertTrue(repo.applySalary(userA,LocalDate.now()))
        assertFalse(repo.applySalary(userA,LocalDate.now()))
        val fixed=repo.addFixed(userA,"Aluguel",120_000,categoryA,accountA,1)
        assertTrue(repo.payFixed(userA,fixed,LocalDate.now()))
        assertFalse(repo.payFixed(userA,fixed,LocalDate.now()))
        assertEquals(2,db.financeDao().transactionsOnce(userA).count{it.origin!=null})
    }

    @Test fun planningAndReportsStoreCentPrecision()=runBlocking {
        repo.savePlan(userA,1_000_00,1250)
        assertEquals(875_00L,db.financeDao().budget(userA,"*")!!.amountCents)
        repo.createTransaction(userA,"receita","Salário",200_00,categoryA,null,accountA,"${YearMonth.now()}-01")
        val report=repo.report(userA,YearMonth.now().year,YearMonth.now().monthValue,"receita")
        assertEquals(200_00L,report.incomeCents)
        assertEquals(200_00L,report.byCategory["Alimentação"])
    }
}

