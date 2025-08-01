package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: Int = 0,
    val tripId: Int,
    val name: String,
    val amount: Double,
    val date: String,
    val category: String,
    val paidByUserId: Int,
    val paymentMethod: String,
    val splitType: SplitType,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val debtors: List<ExpenseDebtor> = emptyList(),
    val paidBy: UserBasic? = null
)

@Serializable
enum class SplitType {
    EQUAL, PERCENTAGE, CUSTOM
}

@Serializable
data class ExpenseDebtor(
    val id: Int = 0,
    val userId: Int,
    val amount: Double,
    val splitValue: Double,
    val hasPaid: Boolean = false,
    val user: UserBasic? = null
)

@Serializable
data class UserBasic(
    val id: Int,
    val name: String,
    val surname: String
)

@Serializable
data class CreateExpenseRequest(
    val name: String,
    val amount: Double,
    val date: String,
    val category: String,
    val paidByUserId: Int,
    val paymentMethod: String,
    val splitType: SplitType,
    val notes: String? = null,
    val debtors: List<CreateDebtorRequest>
)

@Serializable
data class CreateDebtorRequest(
    val userId: Int,
    val splitValue: Double
)

@Serializable
data class UpdateExpenseRequest(
    val name: String? = null,
    val amount: Double? = null,
    val date: String? = null,
    val category: String? = null,
    val paidByUserId: Int? = null,
    val paymentMethod: String? = null,
    val splitType: SplitType? = null,
    val notes: String? = null,
    val debtors: List<CreateDebtorRequest>? = null
)

@Serializable
data class ExpenseSummary(
    val totalExpenses: Double,
    val totalOwed: Double,
    val totalPaid: Double,
    val balances: List<UserBalance>
)

@Serializable
data class UserBalance(
    val userId: Int,
    val name: String,
    val balance: Double
)

@Serializable
data class UserExpenseSummary(
    val totalTripExpenses: Double,
    val userAmountOwed: Double,
    val userAmountToReceive: Double,
    val userBalance: Double,
    val expenses: List<Expense>
)