package com.shivangi.gullak.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import java.time.LocalDate

@Entity(tableName = "goals")
internal data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "target_paise") val targetPaise: Long,
    @ColumnInfo(name = "target_date") val targetDate: LocalDate,
    @ColumnInfo(name = "date_is_fixed") val dateIsFixed: Boolean,
    @ColumnInfo(name = "created_on") val createdOn: LocalDate,
    val destination: Destination,
    @ColumnInfo(name = "language_tag") val languageTag: String,
    /** Null for one-time goals; Room reads it as null when every schedule column is null. */
    @Embedded(prefix = "schedule_") val schedule: ScheduleColumns?,
)

internal data class ScheduleColumns(
    val frequency: Frequency,
    @ColumnInfo(name = "base_amount_paise") val baseAmountPaise: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,
    @ColumnInfo(name = "mandate_id") val mandateId: String,
    @ColumnInfo(name = "extra_debits") val extraDebits: List<ScheduledDebit>,
    @ColumnInfo(name = "excused_dates") val excusedDates: Set<LocalDate>,
)

@Entity(
    tableName = "debits",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goal_id")],
)
internal data class DebitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val date: LocalDate,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val status: DebitStatus,
    val kind: DebitKind,
)
