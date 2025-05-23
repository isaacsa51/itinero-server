package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.abs

object Expenses : Table() {
    val id = integer("id").autoIncrement()
    val tripId = integer("trip_id").references(Trips.id)
    val name = varchar("name", 100)
    val amount = decimal("amount", 10, 2)
    val date = varchar("date", 10)  // ISO 8601 format: YYYY-MM-DD
    val category = varchar("category", 50)
    val paidByUserId = integer("paid_by_user_id").references(Users.id)
    val paymentMethod = varchar("payment_method", 50)
    val splitType = varchar("split_type", 20)  // EQUAL, PERCENTAGE, CUSTOM
    val notes = text("notes").nullable()
    val isCompleted = bool("is_completed").default(false)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

fun createExpense(expense: CreateExpenseRequest, creatorId: Int): Expense = transaction {
    val tripMembers = getTripMembers(expense.tripId).filter { it.isAccepted }.map { it.id }

    if (!tripMembers.contains(expense.paidByUserId) || expense.debtors.any { !tripMembers.contains(it.userId) }) {
        throw IllegalArgumentException("All users must be trip members")
    }

    val debtorAmounts = when (expense.splitType) {
        SplitType.EQUAL -> {
            val perPerson = expense.amount / expense.debtors.size
            expense.debtors.associate { it.userId to perPerson }
        }

        SplitType.PERCENTAGE -> {
            // Validate percentages add up to 100%
            val totalPercentage = expense.debtors.sumOf { it.splitValue }
            if (abs(totalPercentage - 100.0) > 0.01) {
                throw IllegalArgumentException("Percentages must add up to 100%")
            }

            expense.debtors.associate {
                it.userId to (expense.amount * it.splitValue / 100.0)
            }
        }

        SplitType.CUSTOM -> {
            // For custom, validate that amounts add up to total
            val totalCustom = expense.debtors.sumOf { it.splitValue }
            if (abs(totalCustom - expense.amount) > 0.01) {
                throw IllegalArgumentException("Custom amounts must add up to total")
            }

            expense.debtors.associate {
                it.userId to it.splitValue
            }
        }
    }

    val expenseId = Expenses.insert {
        it[tripId] = expense.tripId
        it[name] = expense.name
        it[amount] = expense.amount.toBigDecimal()
        it[date] = expense.date
        it[category] = expense.category
        it[paidByUserId] = expense.paidByUserId
        it[paymentMethod] = expense.paymentMethod
        it[splitType] = expense.splitType.name
        it[notes] = expense.notes
        it[isCompleted] = false
        it[createdAt] = System.currentTimeMillis()
    } get Expenses.id

    val debtors = expense.debtors.map { debtor ->
        val debtorAmount = debtorAmounts[debtor.userId] ?: 0.0
        val debtorId = ExpenseDebtors.insert {
            it[ExpenseDebtors.expenseId] = expenseId
            it[userId] = debtor.userId
            it[amount] = debtorAmount.toBigDecimal()
            it[splitValue] = debtor.splitValue
        } get ExpenseDebtors.id

        ExpenseDebtor(
            id = debtorId, userId = debtor.userId, amount = debtorAmount, splitValue = debtor.splitValue
        )
    }

    return@transaction Expense(
        id = expenseId,
        tripId = expense.tripId,
        name = expense.name,
        amount = expense.amount,
        date = expense.date,
        category = expense.category,
        paidByUserId = expense.paidByUserId,
        paymentMethod = expense.paymentMethod,
        splitType = expense.splitType,
        notes = expense.notes,
        debtors = debtors
    )
}

fun getTripExpenses(tripId: Int): List<Expense> = transaction {
    (Expenses innerJoin Users).selectAll().where { Expenses.tripId eq tripId }
        .orderBy(Expenses.date to SortOrder.DESC, Expenses.id to SortOrder.DESC).map { expenseRow ->
            val expenseId = expenseRow[Expenses.id]

            val debtors = (ExpenseDebtors innerJoin Users).selectAll().where { ExpenseDebtors.expenseId eq expenseId }
                .map { debtorRow ->
                    ExpenseDebtor(
                        id = debtorRow[ExpenseDebtors.id],
                        userId = debtorRow[ExpenseDebtors.userId],
                        amount = debtorRow[ExpenseDebtors.amount].toDouble(),
                        splitValue = debtorRow[ExpenseDebtors.splitValue],
                        user = UserBasic(
                            id = debtorRow[Users.id], name = debtorRow[Users.name], surname = debtorRow[Users.surname]
                        )
                    )
                }

            Expense(
                id = expenseId,
                tripId = expenseRow[Expenses.tripId],
                name = expenseRow[Expenses.name],
                amount = expenseRow[Expenses.amount].toDouble(),
                date = expenseRow[Expenses.date],
                category = expenseRow[Expenses.category],
                paidByUserId = expenseRow[Expenses.paidByUserId],
                paymentMethod = expenseRow[Expenses.paymentMethod],
                splitType = SplitType.valueOf(expenseRow[Expenses.splitType]),
                notes = expenseRow[Expenses.notes],
                isCompleted = expenseRow[Expenses.isCompleted],
                debtors = debtors,
                paidBy = UserBasic(
                    id = expenseRow[Users.id], name = expenseRow[Users.name], surname = expenseRow[Users.surname]
                )
            )
        }
}

fun markExpenseAsCompleted(expenseId: Int, userId: Int): Boolean = transaction {
    val expense = Expenses.selectAll().where { Expenses.id eq expenseId }.singleOrNull() ?: return@transaction false

    if (expense[Expenses.paidByUserId] != userId) {
        return@transaction false
    }

    Expenses.update({ Expenses.id eq expenseId }) {
        it[isCompleted] = true
    } > 0
}

fun deleteExpense(expenseId: Int, userId: Int): Boolean = transaction {
    val expense = Expenses.selectAll().where { Expenses.id eq expenseId }.singleOrNull() ?: return@transaction false

    val tripId = expense[Expenses.tripId]

    if (expense[Expenses.paidByUserId] != userId && !isUserTripOwner(userId, tripId)) {
        return@transaction false
    }

    ExpenseDebtors.deleteWhere { ExpenseDebtors.expenseId eq expenseId }

    Expenses.deleteWhere { Expenses.id eq expenseId } > 0
}

fun getExpenseSummary(tripId: Int): ExpenseSummary = transaction {
    val expenses = getTripExpenses(tripId)
    val members = getTripMembers(tripId).filter { it.isAccepted }

    val amountPaid = expenses.groupBy { it.paidByUserId }.mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    val amountOwed = expenses.flatMap { expense ->
        expense.debtors.map { debtor ->
            debtor.userId to debtor.amount
        }
    }.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() }

    val balances = members.map { member ->
        val paid = amountPaid[member.id] ?: 0.0
        val owed = amountOwed[member.id] ?: 0.0
        val balance = paid - owed

        UserBalance(
            userId = member.id, name = "${member.name}", balance = balance
        )
    }

    ExpenseSummary(
        totalExpenses = expenses.sumOf { it.amount },
        totalOwed = amountOwed.values.sum(),
        totalPaid = amountPaid.values.sum(),
        balances = balances
    )
}