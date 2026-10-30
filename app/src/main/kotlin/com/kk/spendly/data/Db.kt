package com.kk.spendly.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kk.spendly.parser.Categorizer
import com.kk.spendly.parser.ParsedTxn
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "txn", indices = [Index(value = ["dedupeKey"], unique = true)])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val amountPaise: Long,
    val isDebit: Boolean,
    val merchant: String,
    val category: String,
    val account: String?,
    val timestamp: Long,
)

/** Rows the user typed in themselves (the + button) carry this prefix instead of an SMS fingerprint. */
const val MANUAL_PREFIX = "manual-"

val TxnEntity.isManual: Boolean get() = dedupeKey.startsWith(MANUAL_PREFIX)

@Entity(tableName = "merchant_rule")
data class MerchantRule(@PrimaryKey val merchant: String, val category: String)

@Entity(tableName = "custom_category")
data class CustomCategory(@PrimaryKey val name: String, val colorArgb: Long, val iconKey: String)

@Entity(tableName = "budget")
data class Budget(@PrimaryKey val category: String, val limitPaise: Long)

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TxnEntity): Long

    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<TxnEntity>>

    // Edits and deletes are limited to manual rows in SQL itself, so a bug elsewhere can never alter a transaction parsed from an SMS.
    @Query("UPDATE txn SET amountPaise = :amountPaise, isDebit = :isDebit, merchant = :merchant, category = :category, timestamp = :timestamp WHERE id = :id AND dedupeKey LIKE 'manual-%'")
    suspend fun updateManual(id: Long, amountPaise: Long, isDebit: Boolean, merchant: String, category: String, timestamp: Long): Int

    @Query("DELETE FROM txn WHERE id = :id AND dedupeKey LIKE 'manual-%'")
    suspend fun deleteManual(id: Long): Int

    @Query("UPDATE txn SET category = :category WHERE id = :id")
    suspend fun setCategoryById(id: Long, category: String)

    @Query("UPDATE txn SET category = :category WHERE merchant = :merchant AND isDebit = 1")
    suspend fun recategorize(merchant: String, category: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: MerchantRule)

    @Query("SELECT merchant FROM txn WHERE dedupeKey = :key")
    suspend fun merchantOf(key: String): String?

    // Only the category the app picked by default ("Other") follows a better name; anything the user chose is kept.
    @Query("UPDATE txn SET merchant = :merchant, category = CASE WHEN category = 'Other' THEN :category ELSE category END WHERE dedupeKey = :key AND dedupeKey NOT LIKE 'manual-%'")
    suspend fun upgradeMerchant(key: String, merchant: String, category: String)

    @Query("SELECT category FROM merchant_rule WHERE merchant = :merchant")
    suspend fun ruleFor(merchant: String): String?

    @Query("SELECT * FROM custom_category ORDER BY rowid")
    suspend fun allCustomCategories(): List<CustomCategory>

    @Query("SELECT * FROM custom_category ORDER BY rowid")
    fun customCategories(): Flow<List<CustomCategory>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategory(category: CustomCategory)

    @Query("DELETE FROM custom_category WHERE name = :name")
    suspend fun deleteCategory(name: String)

    @Query("UPDATE txn SET category = :to WHERE category = :from")
    suspend fun moveTxns(from: String, to: String)

    @Query("DELETE FROM merchant_rule WHERE category = :category")
    suspend fun deleteRulesFor(category: String)

    @Query("SELECT * FROM budget")
    suspend fun allBudgets(): List<Budget>

    @Query("SELECT * FROM budget")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE category = :category")
    suspend fun budgetFor(category: String): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: Budget)

    @Query("DELETE FROM budget WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM txn WHERE isDebit = 1 AND timestamp >= :from AND timestamp < :to")
    fun spentBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM txn WHERE isDebit = 1 AND category = :category AND timestamp >= :from AND timestamp < :to")
    suspend fun spentIn(category: String, from: Long, to: Long): Long
}

@Database(entities = [TxnEntity::class, MerchantRule::class, Budget::class, CustomCategory::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TxnDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budget` (`category` TEXT NOT NULL, `limitPaise` INTEGER NOT NULL, PRIMARY KEY(`category`))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_category` (`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, PRIMARY KEY(`name`))")
                // The default "Health" category is now "Medical"; carry existing data over.
                db.execSQL("UPDATE txn SET category = 'Medical' WHERE category = 'Health'")
                db.execSQL("UPDATE merchant_rule SET category = 'Medical' WHERE category = 'Health'")
                db.execSQL("UPDATE OR REPLACE budget SET category = 'Medical' WHERE category = 'Health'")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

object Repository {
    /** Stores a parsed SMS; returns its category, or null when it was a duplicate. */
    suspend fun save(dao: TxnDao, p: ParsedTxn): String? {
        // Remembered merchant choices are about spending; money received is always Income unless the user changes that one row.
        val remembered = if (p.type == com.kk.spendly.parser.TxnType.DEBIT) dao.ruleFor(p.merchant) else null
        val category = remembered ?: Categorizer.categorize(p.merchant, p.type)
        val id = dao.insert(
            TxnEntity(
                dedupeKey = p.dedupeKey,
                amountPaise = p.amountPaise,
                isDebit = p.type == com.kk.spendly.parser.TxnType.DEBIT,
                merchant = p.merchant,
                category = category,
                account = p.account,
                timestamp = p.timestamp,
            ),
        )
        if (id == -1L) {
            // Already stored. Messages are re-read on every launch, so a better reading of the same message, such as a name
            // where there used to be "Unknown", replaces the older one.
            val old = dao.merchantOf(p.dedupeKey)
            if (old != null && isBetterName(old, p.merchant)) dao.upgradeMerchant(p.dedupeKey, p.merchant, category)
            return null
        }
        return category
    }

    /** A real name beats a kind of payment, which beats "Unknown"; a reference number mistaken for a name is also replaced. */
    fun isBetterName(old: String, new: String): Boolean {
        if (new == old) return false
        fun rank(m: String) = when {
            m.equals(com.kk.spendly.parser.SmsParser.UNKNOWN_MERCHANT, ignoreCase = true) -> 0
            com.kk.spendly.parser.SmsParser.isGeneric(m) -> 1
            else -> 2
        }
        val looksLikeReference = ('@' !in old && Regex("\\d{6,}").containsMatchIn(old)) || Regex("(?i)\\bref(?:no)?\\b").containsMatchIn(old)
        return rank(new) > rank(old) || (rank(new) == 2 && rank(old) == 2 && looksLikeReference)
    }

    /** Removes a custom category; its transactions fall back to Other and its rules and budget are dropped. */
    suspend fun removeCategory(db: AppDatabase, name: String) = db.withTransaction {
        val dao = db.dao()
        dao.moveTxns(name, Categorizer.OTHER)
        dao.deleteRulesFor(name)
        dao.deleteBudget(name)
        dao.deleteCategory(name)
    }

    /** A named merchant is worth remembering; a blank name, "Unknown" or a kind of payment ("UPI payment") is not. */
    fun remembersMerchant(merchant: String): Boolean =
        merchant.isNotBlank() && !com.kk.spendly.parser.SmsParser.isGeneric(merchant)

    /**
     * Changes the category of a transaction parsed from SMS. The tapped row always changes, spent or received. For spending at a
     * named merchant the choice is also remembered, so that merchant's other payments and future ones follow it.
     */
    suspend fun setCategory(dao: TxnDao, txn: TxnEntity, category: String) {
        dao.setCategoryById(txn.id, category)
        if (txn.isDebit && remembersMerchant(txn.merchant)) {
            dao.saveRule(MerchantRule(txn.merchant, category))
            dao.recategorize(txn.merchant, category)
        }
    }
}
