package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryScope { PREDEFINED, CUSTOM }

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,       // "INCOME" | "EXPENSE" | "BOTH"
    val scope: CategoryScope = CategoryScope.PREDEFINED,
)

val PREDEFINED_CATEGORIES: List<Category> = listOf(
    // Expenses
    Category("cat_food",       "Comida",          "🍔", "#FF6B35", "EXPENSE"),
    Category("cat_transport",  "Transporte",      "🚗", "#4ECDC4", "EXPENSE"),
    Category("cat_health",     "Salud",           "💊", "#45B7D1", "EXPENSE"),
    Category("cat_education",  "Educación",       "📚", "#96CEB4", "EXPENSE"),
    Category("cat_entertain",  "Entretenimiento", "🎮", "#DDA0DD", "EXPENSE"),
    Category("cat_services",   "Servicios",       "💡", "#1E90FF", "EXPENSE"),
    Category("cat_housing",    "Vivienda",        "🏠", "#F0E68C", "EXPENSE"),
    Category("cat_clothing",   "Ropa",            "👗", "#FFB6C1", "EXPENSE"),
    Category("cat_tech",       "Tecnología",      "💻", "#87CEEB", "EXPENSE"),
    Category("cat_other_exp",  "Otros",           "📦", "#D3D3D3", "EXPENSE"),
    // Incomes
    Category("cat_salary",     "Salario",         "💼", "#90EE90", "INCOME"),
    Category("cat_freelance",  "Freelance",       "🖥️", "#98FB98", "INCOME"),
    Category("cat_rent",       "Arriendo recibido","🏘️","#8FBC8F", "INCOME"),
    Category("cat_invest_inc", "Inversiones",     "📈", "#3CB371", "INCOME"),
    Category("cat_other_inc",  "Otros ingresos",  "💰", "#2E8B57", "INCOME"),
)
