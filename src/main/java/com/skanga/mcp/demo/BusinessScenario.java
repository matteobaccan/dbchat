package com.skanga.mcp.demo;

import java.util.List;
import java.util.Map;

/**
 * Represents a business scenario with predefined demo data and analysis workflows.
 * Each scenario includes table structures, sample data, and guided analysis paths.
 */
public record BusinessScenario(String name, String description, String businessContext, List<String> protagonists,
                               String deadline, Map<String, String> tableDefinitions, List<AnalysisStep> analysisSteps,
                               String expectedInsight) {

    /**
     * Represents a step in the guided analysis workflow
     */
    public record AnalysisStep(String title, String description, String suggestedQuery, List<String> multipleChoiceOptions, String expectedResult) {}

    /**
     * Creates a retail business scenario with customers, products, and orders
     */
    public static BusinessScenario createRetailScenario() {
        return new BusinessScenario(
                "retail-analysis",
                "E-commerce store performance analysis",
                "You're working for TechnoMart, a growing online electronics retailer. The company has been operating for 2 years and needs insights into customer behavior, product performance, and sales trends to plan their next quarter.",
                List.of("Sarah Chen (CEO)", "Mike Rodriguez (Sales Director)", "Jennifer Kim (Marketing Manager)"),
                "End of this week - Board meeting on Friday",
                Map.of(
                        "customers", "Customer information including demographics and registration dates",
                        "products", "Product catalog with categories, prices, and inventory levels",
                        "orders", "Order history with customer relationships and order details"
                ),
                List.of(
                        new AnalysisStep(
                                "Customer Base Overview",
                                "Let's start by understanding our customer base",
                                "SELECT COUNT(*) as total_customers, AVG(DATEDIFF(CURDATE(), registration_date)) as avg_days_since_registration FROM customers",
                                List.of(
                                        "A) Analyze customer demographics and registration patterns",
                                        "B) Look at total sales and revenue trends",
                                        "C) Examine product inventory levels"
                                ),
                                "We should see total customer count and average customer tenure"
                        ),
                        new AnalysisStep(
                                "Sales Performance Analysis",
                                "Now let's examine sales patterns and trends",
                                "SELECT DATE_FORMAT(order_date, '%Y-%m') as month, COUNT(*) as orders, SUM(total_amount) as revenue FROM orders GROUP BY DATE_FORMAT(order_date, '%Y-%m') ORDER BY month",
                                List.of(
                                        "A) Analyze monthly sales trends and seasonality",
                                        "B) Identify top-performing products by revenue",
                                        "C) Examine customer purchasing behavior patterns"
                                ),
                                "We should see monthly sales trends that reveal business growth patterns"
                        ),
                        new AnalysisStep(
                                "Product Performance Deep Dive",
                                "Let's identify which products are driving our success",
                                "SELECT p.name, p.category, COUNT(oi.product_id) as units_sold, SUM(oi.quantity * oi.price) as revenue FROM products p JOIN order_items oi ON p.id = oi.product_id GROUP BY p.id ORDER BY revenue DESC LIMIT 10",
                                List.of(
                                        "A) Focus on top revenue-generating products",
                                        "B) Analyze product categories and market segments",
                                        "C) Examine customer lifetime value patterns"
                                ),
                                "We should identify our best-selling products and categories"
                        )
                ),
                "TechnoMart shows strong growth potential with increasing customer acquisition and steady revenue growth. The data reveals seasonal patterns and top-performing product categories that should guide inventory and marketing decisions."
        );
    }

    /**
     * Creates a finance business scenario with accounts, transactions, and customers
     */
    public static BusinessScenario createFinanceScenario() {
        return new BusinessScenario(
                "finance-analysis",
                "Banking customer behavior and risk analysis",
                "You're working for SecureBank, a regional bank that needs to analyze customer transaction patterns, account performance, and identify potential risks or opportunities for new financial products.",
                List.of("David Park (Risk Manager)", "Lisa Thompson (Customer Analytics)", "Robert Chen (Branch Manager)"),
                "Regulatory review next Monday",
                Map.of(
                        "customers", "Customer profiles with demographics and account opening dates",
                        "accounts", "Account details including types, balances, and status",
                        "transactions", "Transaction history with amounts, types, and timestamps"
                ),
                List.of(
                        new AnalysisStep(
                                "Account Portfolio Overview",
                                "Let's understand our account distribution and balances",
                                "SELECT account_type, COUNT(*) as account_count, AVG(balance) as avg_balance, SUM(balance) as total_balance FROM accounts WHERE status = 'ACTIVE' GROUP BY account_type",
                                List.of(
                                        "A) Analyze account types and balance distributions",
                                        "B) Examine transaction patterns and frequency",
                                        "C) Look at customer demographics and segments"
                                ),
                                "We should see the distribution of different account types and their typical balances"
                        ),
                        new AnalysisStep(
                                "Transaction Pattern Analysis",
                                "Now let's examine how customers use their accounts",
                                "SELECT transaction_type, COUNT(*) as transaction_count, AVG(amount) as avg_amount, SUM(amount) as total_amount FROM transactions WHERE transaction_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY transaction_type",
                                List.of(
                                        "A) Focus on transaction types and volumes",
                                        "B) Identify high-value transaction patterns",
                                        "C) Analyze customer activity levels and engagement"
                                ),
                                "We should identify the most common transaction types and their patterns"
                        ),
                        new AnalysisStep(
                                "Customer Behavior Insights",
                                "Let's identify customer segments and behaviors",
                                "SELECT c.age_group, AVG(a.balance) as avg_balance, COUNT(t.id) as transaction_count FROM customers c JOIN accounts a ON c.id = a.customer_id LEFT JOIN transactions t ON a.id = t.account_id GROUP BY c.age_group",
                                List.of(
                                        "A) Segment customers by age and behavior patterns",
                                        "B) Identify high-value customers and their characteristics",
                                        "C) Analyze risk indicators and account health"
                                ),
                                "We should see how different age groups use banking services differently"
                        )
                ),
                "SecureBank has a diverse customer base with varying engagement levels. The analysis reveals opportunities for targeted financial products and identifies customer segments that may benefit from additional services."
        );
    }
}