package br.com.feira.nfce.financeiro

import br.com.feira.nfce.auth.PasswordHasher
import br.com.feira.nfce.financeiro.domain.Money
import org.bouncycastle.crypto.generators.SCrypt
import org.junit.Assert.*
import org.junit.Test

class MoneyAndPasswordTest {
    @Test fun parsesBrazilianAndDotDecimalValuesAsCents() {
        assertEquals(123456L, Money.parseCents("R$ 1.234,56"))
        assertEquals(1234L, Money.parseCents("12.34"))
        assertEquals(100L, Money.parseCents("1"))
        assertNull(Money.parseCents("NaN"))
        assertNull(Money.parseCents("0"))
    }

    @Test fun newPasswordHashesAreSaltedAndVerified() {
        val first=PasswordHasher.hash("senha-segura".toCharArray())
        val second=PasswordHasher.hash("senha-segura".toCharArray())
        assertNotEquals(first,second)
        assertTrue(PasswordHasher.verify("senha-segura".toCharArray(),first))
        assertFalse(PasswordHasher.verify("outra-senha".toCharArray(),first))
    }

    @Test fun supportsWerkzeugScryptHashesForFirstLoginUpgrade() {
        val salt="sal-legado".toByteArray()
        val digest=SCrypt.generate("senha-legada".toByteArray(),salt,16_384,8,1,32)
        val encoded="scrypt:16384:8:1\$sal-legado\$"+digest.joinToString(""){"%02x".format(it)}
        assertTrue(PasswordHasher.verify("senha-legada".toCharArray(),encoded))
        assertFalse(PasswordHasher.verify("incorreta".toCharArray(),encoded))
    }
}
