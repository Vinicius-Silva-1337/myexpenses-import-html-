package br.com.feira.nfce.auth

import android.content.Context
import androidx.room.withTransaction
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.financeiro.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.SCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("my-expenses-session", Context.MODE_PRIVATE)
    fun userId(): Long? = prefs.getLong("user_id", -1L).takeIf { it > 0L }
    fun set(userId: Long) { prefs.edit().putLong("user_id", userId).apply() }
    fun clear() { prefs.edit().remove("user_id").apply() }
}

sealed interface AuthResult {
    data class Success(val user: UserEntity) : AuthResult
    data class Failure(val message: String) : AuthResult
}

class AuthRepository(private val db: AppDatabase, private val session: SessionManager) {
    suspend fun register(name: String, emailRaw: String, password: CharArray): AuthResult = withContext(Dispatchers.Default) {
        try {
            val email = emailRaw.trim().lowercase()
            if (name.trim().isEmpty() || !EMAIL.matches(email) || password.size < 8) return@withContext AuthResult.Failure("Informe nome, e-mail válido e senha com pelo menos 8 caracteres.")
            if (db.userDao().byEmail(email) != null) return@withContext AuthResult.Failure("Este e-mail já está cadastrado.")
            val user = db.withTransaction {
                val hasUsers = db.userDao().realUserCount() > 0
                val id = db.userDao().insert(UserEntity(name = name.trim(), email = email, passwordHash = PasswordHasher.hash(password)))
                if (!hasUsers) claimUnownedData(id)
                db.userDao().byId(id)!!
            }
            session.set(user.id)
            AuthResult.Success(user)
        } catch (_: Exception) { AuthResult.Failure("Não foi possível criar a conta. Confira se o e-mail já está em uso.") }
        finally { password.fill('\u0000') }
    }

    suspend fun login(emailRaw: String, password: CharArray): AuthResult = withContext(Dispatchers.Default) {
        val user = db.userDao().byEmail(emailRaw.trim())
            ?: run { password.fill('\u0000'); return@withContext AuthResult.Failure("E-mail ou senha inválidos.") }
        if (!PasswordHasher.verify(password, user.passwordHash)) {
            password.fill('\u0000')
            return@withContext AuthResult.Failure("E-mail ou senha inválidos.")
        }
        // Legacy Werkzeug hashes are upgraded immediately after a successful first login.
        if (user.passwordHash.startsWith("scrypt:") || user.passwordHash.startsWith("pbkdf2:")) {
            val replacement = PasswordHasher.hash(password)
            db.userDao().updatePassword(user.id, replacement)
        }
        password.fill('\u0000')
        session.set(user.id)
        AuthResult.Success(user.copy(passwordHash = ""))
    }

    suspend fun currentUser(): UserEntity? = session.userId()?.let { db.userDao().byId(it) }
    fun logout() = session.clear()

    private suspend fun claimUnownedData(userId: Long) {
        val old = db.userDao().unclaimedId() ?: return
        db.openHelper.writableDatabase.execSQL("UPDATE categorias SET userId=? WHERE userId=?", arrayOf(userId, old))
        db.openHelper.writableDatabase.execSQL("UPDATE palavras_chave SET userId=? WHERE userId=?", arrayOf(userId, old))
        db.openHelper.writableDatabase.execSQL("UPDATE product_overrides SET userId=? WHERE userId=?", arrayOf(userId, old))
        db.openHelper.writableDatabase.execSQL("UPDATE notas_fiscais SET userId=? WHERE userId=?", arrayOf(userId, old))
    }

    companion object { private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }
}

object PasswordHasher {
    private const val ITERATIONS = 310_000
    private const val KEY_BYTES = 32

    fun hash(password: CharArray): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BYTES * 8)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return "pbkdf2-sha256\$$ITERATIONS\$${hex(salt)}\$${hex(derived)}"
    }

    fun verify(password: CharArray, encoded: String): Boolean = try {
        when {
            encoded.startsWith("pbkdf2-sha256$") -> {
                val p = encoded.split('$'); val iterations = p[1].toInt()
                if (iterations !in 1..2_000_000) return false
                val salt = unhex(p[2]); val expected = unhex(p[3])
                val spec = PBEKeySpec(password, salt, iterations, expected.size * 8)
                val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                spec.clearPassword(); MessageDigest.isEqual(expected, actual)
            }
            encoded.startsWith("scrypt:") -> verifyWerkzeugScrypt(password, encoded)
            encoded.startsWith("pbkdf2:sha256:") -> verifyWerkzeugPbkdf2(password, encoded)
            else -> false
        }
    } catch (_: Exception) { false }

    private fun verifyWerkzeugScrypt(password: CharArray, encoded: String): Boolean {
        val p = encoded.split('$'); val params = p[0].removePrefix("scrypt:").split(':')
        val n = params[0].toInt(); val r = params[1].toInt(); val parallel = params[2].toInt()
        require(n in 2..262_144 && r in 1..32 && parallel in 1..16)
        val expected = unhex(p[2]); val actual = SCrypt.generate(String(password).toByteArray(Charsets.UTF_8), p[1].toByteArray(Charsets.UTF_8), n, r, parallel, expected.size)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun verifyWerkzeugPbkdf2(password: CharArray, encoded: String): Boolean {
        val p = encoded.split('$'); val params = p[0].split(':'); val iterations = params.last().toInt()
        if (iterations !in 1..2_000_000) return false
        val algorithm = if (params.getOrNull(1)?.lowercase() == "sha256") "PBKDF2WithHmacSHA256" else return false
        val expected = unhex(p[2]); val spec = PBEKeySpec(password, p[1].toCharArray().map(Char::code).map(Int::toByte).toByteArray(), iterations, expected.size * 8)
        val actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        spec.clearPassword(); return MessageDigest.isEqual(expected, actual)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
