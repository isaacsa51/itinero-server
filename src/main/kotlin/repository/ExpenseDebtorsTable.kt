package com.serranoie.server.repository

import org.jetbrains.exposed.sql.Table

object ExpenseDebtors : Table("expense_debtors") {
    val id = integer("id").autoIncrement()
    val expenseId = integer("expense_id").references(Expenses.id)
    val userId = integer("user_id").references(Users.id)
    val amount = decimal("amount", 10, 2)
    val splitValue = double("split_value")

    override val primaryKey = PrimaryKey(id)
}
