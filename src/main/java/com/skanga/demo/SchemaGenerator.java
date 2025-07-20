package com.skanga.demo;

import net.datafaker.Faker;

import java.io.File;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

class Config {
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;
    public final String dbDriver;
    public final String schema;
    public final int employees;
    public final int customers;
    public final int orders;
    public final int warehouses;
    public final int suppliers;

    public Config(String dbUrl, String dbDriver, String dbUser, String dbPassword, String schema,
                  int employees, int customers, int orders, int warehouses, int suppliers) {
        this.dbUrl = dbUrl;
        this.dbDriver = dbDriver;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.schema = schema;
        this.employees = employees;
        this.customers = customers;
        this.orders = orders;
        this.warehouses = warehouses;
        this.suppliers = suppliers;
    }
}

// Simple database helper
class DatabaseHelper {
    private final Connection connection;
    private final boolean supportsForeignKeys;

    public DatabaseHelper(Connection connection) throws SQLException {
        this.connection = connection;
        String dbName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        this.supportsForeignKeys = !dbName.contains("sqlite");
    }

    public void executeSQL(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Executed: " + getDescription(sql));
        }
    }

    public void executeBatch(String sql, List<Object[]> data) throws SQLException {
        if (data.isEmpty()) return;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Object[] row : data) {
                for (int i = 0; i < row.length; i++) {
                    stmt.setObject(i + 1, row[i]);
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public int getCount(String table) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public boolean columnExists(String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    public String fk(String constraint) {
        return supportsForeignKeys ? ",\n    " + constraint : "";
    }

    private String getDescription(String sql) {
        sql = sql.trim().toUpperCase();
        if (sql.startsWith("CREATE TABLE")) {
            return "CREATE TABLE " + sql.split("\\s+")[2];
        } else if (sql.startsWith("DROP TABLE")) {
            return "DROP TABLE " + (sql.contains("IF EXISTS") ? sql.split("\\s+")[4] : sql.split("\\s+")[2]);
        }
        return "SQL statement";
    }
}

// Main generator class
public class SchemaGenerator {
    private final Connection connection;
    private final DatabaseHelper db;
    private final Faker faker;
    private final Config config;
    private final Random random = new Random();

    public SchemaGenerator(Config config) throws SQLException, ClassNotFoundException {
        this.config = config;
        Class.forName(config.dbDriver);
        this.connection = DriverManager.getConnection(config.dbUrl, config.dbUser, config.dbPassword);
        this.db = new DatabaseHelper(connection);
        this.faker = new Faker();

        System.out.println("Connected to: " + connection.getMetaData().getDatabaseProductName());
    }

    public void generate() throws SQLException {
        switch (config.schema.toLowerCase()) {
            case "hr" -> generateHR();
            case "sales" -> generateSales();
            case "inventory" -> generateInventory();
            case "combined" -> generateCombined();
            default -> throw new IllegalArgumentException("Unknown schema: " + config.schema);
        }
        showResults();
    }

    // HR Schema Generation
    private void generateHR() throws SQLException {
        System.out.println("=== Generating HR Schema ===");

        dropTables("dependents", "employees", "departments", "jobs");

        db.executeSQL("""
                CREATE TABLE jobs (
                    job_id INT PRIMARY KEY,
                    job_title VARCHAR(100) NOT NULL,
                    min_salary DECIMAL(10,2),
                    max_salary DECIMAL(10,2)
                )""");

        db.executeSQL("""
                CREATE TABLE departments (
                    department_id INT PRIMARY KEY,
                    department_name VARCHAR(100) NOT NULL,
                    manager_name VARCHAR(100)
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE employees (
                            employee_id INT PRIMARY KEY,
                            first_name VARCHAR(50) NOT NULL,
                            last_name VARCHAR(50) NOT NULL,
                            email VARCHAR(100) UNIQUE NOT NULL,
                            phone VARCHAR(20),
                            hire_date DATE NOT NULL,
                            job_id INT%s,
                            salary DECIMAL(10,2),
                            manager_id INT,
                            department_id INT%s%s
                        )""",
                db.fk("FOREIGN KEY (job_id) REFERENCES jobs(job_id)"),
                db.fk("FOREIGN KEY (manager_id) REFERENCES employees(employee_id)"),
                db.fk("FOREIGN KEY (department_id) REFERENCES departments(department_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE dependents (
                            dependent_id INT PRIMARY KEY,
                            first_name VARCHAR(50) NOT NULL,
                            last_name VARCHAR(50) NOT NULL,
                            relationship VARCHAR(50) NOT NULL,
                            employee_id INT%s
                        )""",
                db.fk("FOREIGN KEY (employee_id) REFERENCES employees(employee_id)")
        ));

        populateJobs();
        populateDepartments();
        populateEmployees();
        populateDependents();
    }

    // Sales Schema Generation
    private void generateSales() throws SQLException {
        System.out.println("=== Generating Sales Schema ===");

        dropTables("order_items", "orders", "customers", "products", "categories");

        db.executeSQL("""
                CREATE TABLE categories (
                    category_id INT PRIMARY KEY,
                    category_name VARCHAR(100) NOT NULL
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE products (
                            product_id INT PRIMARY KEY,
                            product_name VARCHAR(200) NOT NULL,
                            category_id INT%s,
                            unit_price DECIMAL(10,2) NOT NULL,
                            units_in_stock INT DEFAULT 0
                        )""",
                db.fk("FOREIGN KEY (category_id) REFERENCES categories(category_id)")
        ));

        db.executeSQL("""
                CREATE TABLE customers (
                    customer_id INT PRIMARY KEY,
                    company_name VARCHAR(200),
                    contact_name VARCHAR(100),
                    email VARCHAR(100),
                    phone VARCHAR(20),
                    address VARCHAR(255),
                    city VARCHAR(100),
                    state VARCHAR(100),
                    postal_code VARCHAR(20),
                    country VARCHAR(100)
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE orders (
                            order_id INT PRIMARY KEY,
                            customer_id INT NOT NULL%s,
                            order_date DATE NOT NULL,
                            total_amount DECIMAL(12,2),
                            status VARCHAR(20) DEFAULT 'Pending'
                        )""",
                db.fk("FOREIGN KEY (customer_id) REFERENCES customers(customer_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE order_items (
                            order_item_id INT PRIMARY KEY,
                            order_id INT NOT NULL%s,
                            product_id INT NOT NULL%s,
                            quantity INT NOT NULL,
                            unit_price DECIMAL(10,2) NOT NULL
                        )""",
                db.fk("FOREIGN KEY (order_id) REFERENCES orders(order_id)"),
                db.fk("FOREIGN KEY (product_id) REFERENCES products(product_id)")
        ));

        populateCategories();
        populateProducts();
        populateCustomers();
        populateOrders();
        populateOrderItems();
    }

    // Inventory Schema Generation
    private void generateInventory() throws SQLException {
        System.out.println("=== Generating Inventory Schema ===");

        dropTables("inventory", "warehouses", "suppliers", "categories", "products");

        db.executeSQL("""
                CREATE TABLE categories (
                    category_id INT PRIMARY KEY,
                    category_name VARCHAR(100) NOT NULL
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE products (
                            product_id INT PRIMARY KEY,
                            product_name VARCHAR(200) NOT NULL,
                            category_id INT%s,
                            unit_price DECIMAL(10,2) NOT NULL
                        )""",
                db.fk("FOREIGN KEY (category_id) REFERENCES categories(category_id)")
        ));

        db.executeSQL("""
                CREATE TABLE warehouses (
                    warehouse_id INT PRIMARY KEY,
                    warehouse_name VARCHAR(200) NOT NULL,
                    address VARCHAR(255),
                    city VARCHAR(100),
                    state VARCHAR(100),
                    country VARCHAR(100),
                    manager_name VARCHAR(100)
                )""");

        db.executeSQL("""
                CREATE TABLE suppliers (
                    supplier_id INT PRIMARY KEY,
                    supplier_name VARCHAR(200) NOT NULL,
                    contact_name VARCHAR(100),
                    email VARCHAR(100),
                    phone VARCHAR(20),
                    address VARCHAR(255),
                    city VARCHAR(100),
                    country VARCHAR(100)
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE inventory (
                            inventory_id INT PRIMARY KEY,
                            product_id INT NOT NULL%s,
                            warehouse_id INT NOT NULL%s,
                            quantity_on_hand INT NOT NULL DEFAULT 0,
                            reorder_level INT DEFAULT 10
                        )""",
                db.fk("FOREIGN KEY (product_id) REFERENCES products(product_id)"),
                db.fk("FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id)")
        ));

        populateCategories();
        populateProducts();
        populateWarehouses();
        populateSuppliers();
        populateInventory();
    }

    // Combined Schema Generation
    private void generateCombined() throws SQLException {
        System.out.println("=== Generating Combined Schema ===");

        dropTables("inventory", "order_items", "orders", "dependents", "employees",
                "customers", "products", "categories", "warehouses", "suppliers",
                "departments", "jobs");

        // Create all tables with consistent structure
        db.executeSQL("""
                CREATE TABLE jobs (
                    job_id INT PRIMARY KEY,
                    job_title VARCHAR(100) NOT NULL,
                    min_salary DECIMAL(10,2),
                    max_salary DECIMAL(10,2)
                )""");

        db.executeSQL("""
                CREATE TABLE departments (
                    department_id INT PRIMARY KEY,
                    department_name VARCHAR(100) NOT NULL
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE employees (
                            employee_id INT PRIMARY KEY,
                            first_name VARCHAR(50) NOT NULL,
                            last_name VARCHAR(50) NOT NULL,
                            email VARCHAR(100) UNIQUE NOT NULL,
                            phone VARCHAR(20),
                            hire_date DATE NOT NULL,
                            job_id INT%s,
                            salary DECIMAL(10,2),
                            department_id INT%s
                        )""",
                db.fk("FOREIGN KEY (job_id) REFERENCES jobs(job_id)"),
                db.fk("FOREIGN KEY (department_id) REFERENCES departments(department_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE dependents (
                            dependent_id INT PRIMARY KEY,
                            first_name VARCHAR(50) NOT NULL,
                            last_name VARCHAR(50) NOT NULL,
                            relationship VARCHAR(50) NOT NULL,
                            employee_id INT%s
                        )""",
                db.fk("FOREIGN KEY (employee_id) REFERENCES employees(employee_id)")
        ));

        db.executeSQL("""
                CREATE TABLE categories (
                    category_id INT PRIMARY KEY,
                    category_name VARCHAR(100) NOT NULL
                )""");

        db.executeSQL(String.format("""
                        CREATE TABLE products (
                            product_id INT PRIMARY KEY,
                            product_name VARCHAR(200) NOT NULL,
                            category_id INT%s,
                            unit_price DECIMAL(10,2) NOT NULL,
                            units_in_stock INT DEFAULT 0
                        )""",
                db.fk("FOREIGN KEY (category_id) REFERENCES categories(category_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE customers (
                            customer_id INT PRIMARY KEY,
                            company_name VARCHAR(200),
                            contact_name VARCHAR(100),
                            email VARCHAR(100),
                            phone VARCHAR(20),
                            address VARCHAR(255),
                            city VARCHAR(100),
                            state VARCHAR(100),
                            postal_code VARCHAR(20),
                            country VARCHAR(100),
                            account_rep_id INT%s
                        )""",
                db.fk("FOREIGN KEY (account_rep_id) REFERENCES employees(employee_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE orders (
                            order_id INT PRIMARY KEY,
                            customer_id INT NOT NULL%s,
                            employee_id INT%s,
                            order_date DATE NOT NULL,
                            total_amount DECIMAL(12,2),
                            status VARCHAR(20) DEFAULT 'Pending'
                        )""",
                db.fk("FOREIGN KEY (customer_id) REFERENCES customers(customer_id)"),
                db.fk("FOREIGN KEY (employee_id) REFERENCES employees(employee_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE order_items (
                            order_item_id INT PRIMARY KEY,
                            order_id INT NOT NULL%s,
                            product_id INT NOT NULL%s,
                            quantity INT NOT NULL,
                            unit_price DECIMAL(10,2) NOT NULL
                        )""",
                db.fk("FOREIGN KEY (order_id) REFERENCES orders(order_id)"),
                db.fk("FOREIGN KEY (product_id) REFERENCES products(product_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE warehouses (
                            warehouse_id INT PRIMARY KEY,
                            warehouse_name VARCHAR(200) NOT NULL,
                            address VARCHAR(255),
                            city VARCHAR(100),
                            state VARCHAR(100),
                            country VARCHAR(100),
                            manager_id INT%s
                        )""",
                db.fk("FOREIGN KEY (manager_id) REFERENCES employees(employee_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE suppliers (
                            supplier_id INT PRIMARY KEY,
                            supplier_name VARCHAR(200) NOT NULL,
                            contact_name VARCHAR(100),
                            email VARCHAR(100),
                            phone VARCHAR(20),
                            address VARCHAR(255),
                            city VARCHAR(100),
                            country VARCHAR(100),
                            account_manager_id INT%s
                        )""",
                db.fk("FOREIGN KEY (account_manager_id) REFERENCES employees(employee_id)")
        ));

        db.executeSQL(String.format("""
                        CREATE TABLE inventory (
                            inventory_id INT PRIMARY KEY,
                            product_id INT NOT NULL%s,
                            warehouse_id INT NOT NULL%s,
                            quantity_on_hand INT NOT NULL DEFAULT 0,
                            reorder_level INT DEFAULT 10
                        )""",
                db.fk("FOREIGN KEY (product_id) REFERENCES products(product_id)"),
                db.fk("FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id)")
        ));

        // Populate in correct order
        populateJobs();
        populateDepartments();
        populateEmployees();
        populateDependents();
        populateCategories();
        populateProducts();
        populateCustomersWithReps();
        populateOrdersWithReps();
        populateOrderItems();
        populateWarehousesWithManagers();
        populateSuppliersWithManagers();
        populateInventory();
    }

    // Data Population Methods
    private void populateJobs() throws SQLException {
        String[][] jobs = {
                {"Software Engineer", "50000", "120000"},
                {"Manager", "70000", "150000"},
                {"Sales Representative", "40000", "80000"},
                {"HR Specialist", "45000", "75000"},
                {"Marketing Coordinator", "42000", "70000"},
                {"Accountant", "48000", "85000"},
                {"Operations Manager", "65000", "130000"},
                {"Customer Service Rep", "35000", "55000"}
        };

        List<Object[]> data = new ArrayList<>();
        for (int i = 0; i < jobs.length; i++) {
            data.add(new Object[]{
                    i + 1, jobs[i][0],
                    new BigDecimal(jobs[i][1]), new BigDecimal(jobs[i][2])
            });
        }
        db.executeBatch("INSERT INTO jobs (job_id, job_title, min_salary, max_salary) VALUES (?, ?, ?, ?)", data);
    }

    private void populateDepartments() throws SQLException {
        String[] depts = {"Engineering", "Sales", "Marketing", "HR", "Finance", "Operations", "Customer Service"};

        List<Object[]> data = new ArrayList<>();

        // Check if manager_name column exists
        boolean hasManagerName = db.columnExists("departments", "manager_name");

        if (hasManagerName) {
            // HR/Sales/Inventory schema - has manager_name column
            for (int i = 0; i < depts.length; i++) {
                data.add(new Object[]{i + 1, depts[i], faker.name().fullName()});
            }
            db.executeBatch("INSERT INTO departments (department_id, department_name, manager_name) VALUES (?, ?, ?)", data);
        } else {
            // Combined schema - no manager_name column
            for (int i = 0; i < depts.length; i++) {
                data.add(new Object[]{i + 1, depts[i]});
            }
            db.executeBatch("INSERT INTO departments (department_id, department_name) VALUES (?, ?)", data);
        }
    }

    private void populateEmployees() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 1; i <= config.employees; i++) {
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String email = generateUniqueEmail(firstName, lastName, usedEmails);

            BigDecimal salary = new BigDecimal(40000 + random.nextInt(80000));
            Date hireDate = Date.valueOf(LocalDate.now().minusDays(random.nextInt(3650)));

            data.add(new Object[]{
                    i, firstName, lastName, email,
                    faker.phoneNumber().phoneNumber(),
                    hireDate, random.nextInt(8) + 1, salary, random.nextInt(7) + 1
            });
        }
        db.executeBatch("INSERT INTO employees (employee_id, first_name, last_name, email, phone, hire_date, job_id, salary, department_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateDependents() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        String[] relationships = {"Spouse", "Child", "Parent"};
        int dependentId = 1;

        for (int employeeId = 1; employeeId <= config.employees; employeeId++) {
            if (random.nextDouble() < 0.4) { // 40% chance of having dependents
                int numDependents = random.nextInt(3) + 1;
                for (int j = 0; j < numDependents; j++) {
                    data.add(new Object[]{
                            dependentId++, faker.name().firstName(), faker.name().lastName(),
                            relationships[random.nextInt(relationships.length)], employeeId
                    });
                }
            }
        }

        if (!data.isEmpty()) {
            db.executeBatch("INSERT INTO dependents (dependent_id, first_name, last_name, relationship, employee_id) VALUES (?, ?, ?, ?, ?)", data);
        }
    }

    private void populateCategories() throws SQLException {
        String[] categories = {"Electronics", "Computers", "Office Supplies", "Furniture", "Books", "Clothing"};

        List<Object[]> data = new ArrayList<>();
        for (int i = 0; i < categories.length; i++) {
            data.add(new Object[]{i + 1, categories[i]});
        }
        db.executeBatch("INSERT INTO categories (category_id, category_name) VALUES (?, ?)", data);
    }

    private void populateProducts() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        boolean hasStock = db.columnExists("products", "units_in_stock");

        for (int i = 1; i <= 20; i++) {
            if (hasStock) {
                // Sales/Combined schema - has units_in_stock
                data.add(new Object[]{
                        i, faker.commerce().productName(),
                        random.nextInt(6) + 1,
                        BigDecimal.valueOf(10 + random.nextDouble() * 990),
                        random.nextInt(100) + 10
                });
            } else {
                // Inventory schema - no units_in_stock
                data.add(new Object[]{
                        i, faker.commerce().productName(),
                        random.nextInt(6) + 1,
                        BigDecimal.valueOf(10 + random.nextDouble() * 990)
                });
            }
        }

        if (hasStock) {
            db.executeBatch("INSERT INTO products (product_id, product_name, category_id, unit_price, units_in_stock) VALUES (?, ?, ?, ?, ?)", data);
        } else {
            db.executeBatch("INSERT INTO products (product_id, product_name, category_id, unit_price) VALUES (?, ?, ?, ?)", data);
        }
    }

    private void populateCustomers() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 1; i <= config.customers; i++) {
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String email = generateUniqueEmail(firstName, lastName, usedEmails);

            data.add(new Object[]{
                    i, faker.company().name(),
                    firstName + " " + lastName,
                    email, faker.phoneNumber().phoneNumber(),
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().state(),
                    faker.address().zipCode(),
                    faker.address().country()
            });
        }
        db.executeBatch("INSERT INTO customers (customer_id, company_name, contact_name, email, phone, address, city, state, postal_code, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateCustomersWithReps() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 1; i <= config.customers; i++) {
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String email = generateUniqueEmail(firstName, lastName, usedEmails);
            Integer repId = random.nextDouble() < 0.8 ? random.nextInt(config.employees) + 1 : null;

            data.add(new Object[]{
                    i, faker.company().name(),
                    firstName + " " + lastName,
                    email, faker.phoneNumber().phoneNumber(),
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().state(),
                    faker.address().zipCode(),
                    faker.address().country(), repId
            });
        }
        db.executeBatch("INSERT INTO customers (customer_id, company_name, contact_name, email, phone, address, city, state, postal_code, country, account_rep_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateOrders() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        String[] statuses = {"Pending", "Processing", "Shipped", "Delivered"};

        for (int i = 1; i <= config.orders; i++) {
            Date orderDate = Date.valueOf(LocalDate.now().minusDays(random.nextInt(365)));

            data.add(new Object[]{
                    i, random.nextInt(config.customers) + 1, orderDate,
                    BigDecimal.valueOf(50 + random.nextDouble() * 4950),
                    statuses[random.nextInt(statuses.length)]
            });
        }
        db.executeBatch("INSERT INTO orders (order_id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)", data);
    }

    private void populateOrdersWithReps() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        String[] statuses = {"Pending", "Processing", "Shipped", "Delivered"};

        for (int i = 1; i <= config.orders; i++) {
            Date orderDate = Date.valueOf(LocalDate.now().minusDays(random.nextInt(365)));
            Integer empId = random.nextDouble() < 0.7 ? random.nextInt(config.employees) + 1 : null;

            data.add(new Object[]{
                    i, random.nextInt(config.customers) + 1, empId, orderDate,
                    BigDecimal.valueOf(50 + random.nextDouble() * 4950),
                    statuses[random.nextInt(statuses.length)]
            });
        }
        db.executeBatch("INSERT INTO orders (order_id, customer_id, employee_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?, ?)", data);
    }

    private void populateOrderItems() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        int itemId = 1;

        for (int orderId = 1; orderId <= config.orders; orderId++) {
            int numItems = random.nextInt(5) + 1;
            Set<Integer> usedProducts = new HashSet<>();

            for (int i = 0; i < numItems; i++) {
                int productId;
                do {
                    productId = random.nextInt(20) + 1;
                } while (usedProducts.contains(productId));
                usedProducts.add(productId);

                data.add(new Object[]{
                        itemId++, orderId, productId, random.nextInt(10) + 1,
                        BigDecimal.valueOf(10 + random.nextDouble() * 490)
                });
            }
        }
        db.executeBatch("INSERT INTO order_items (order_item_id, order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?)", data);
    }

    private void populateWarehouses() throws SQLException {
        List<Object[]> data = new ArrayList<>();

        for (int i = 1; i <= config.warehouses; i++) {
            data.add(new Object[]{
                    i, "Warehouse " + i,
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().state(),
                    faker.address().country(),
                    faker.name().fullName()
            });
        }
        db.executeBatch("INSERT INTO warehouses (warehouse_id, warehouse_name, address, city, state, country, manager_name) VALUES (?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateWarehousesWithManagers() throws SQLException {
        List<Object[]> data = new ArrayList<>();

        for (int i = 1; i <= config.warehouses; i++) {
            Integer managerId = random.nextDouble() < 0.8 ? random.nextInt(config.employees) + 1 : null;
            data.add(new Object[]{
                    i, "Warehouse " + i,
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().state(),
                    faker.address().country(),
                    managerId
            });
        }
        db.executeBatch("INSERT INTO warehouses (warehouse_id, warehouse_name, address, city, state, country, manager_id) VALUES (?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateSuppliers() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 1; i <= config.suppliers; i++) {
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String email = generateUniqueEmail(firstName, lastName, usedEmails);

            data.add(new Object[]{
                    i, faker.company().name(),
                    firstName + " " + lastName,
                    email, faker.phoneNumber().phoneNumber(),
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().country()
            });
        }
        db.executeBatch("INSERT INTO suppliers (supplier_id, supplier_name, contact_name, email, phone, address, city, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateSuppliersWithManagers() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 1; i <= config.suppliers; i++) {
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String email = generateUniqueEmail(firstName, lastName, usedEmails);
            Integer managerId = random.nextDouble() < 0.6 ? random.nextInt(config.employees) + 1 : null;

            data.add(new Object[]{
                    i, faker.company().name(),
                    firstName + " " + lastName,
                    email, faker.phoneNumber().phoneNumber(),
                    faker.address().fullAddress(),
                    faker.address().city(),
                    faker.address().country(), managerId
            });
        }
        db.executeBatch("INSERT INTO suppliers (supplier_id, supplier_name, contact_name, email, phone, address, city, country, account_manager_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", data);
    }

    private void populateInventory() throws SQLException {
        List<Object[]> data = new ArrayList<>();
        int inventoryId = 1;

        for (int warehouseId = 1; warehouseId <= config.warehouses; warehouseId++) {
            for (int productId = 1; productId <= 20; productId++) {
                if (random.nextDouble() < 0.7) {
                    data.add(new Object[]{
                            inventoryId++, productId, warehouseId,
                            random.nextInt(1000) + 10, random.nextInt(50) + 5
                    });
                }
            }
        }
        db.executeBatch("INSERT INTO inventory (inventory_id, product_id, warehouse_id, quantity_on_hand, reorder_level) VALUES (?, ?, ?, ?, ?)", data);
    }

    // Helper methods
    private void dropTables(String... tables) {
        for (String table : tables) {
            try {
                db.executeSQL("DROP TABLE IF EXISTS " + table);
            } catch (SQLException e) {
                // Ignore if table doesn't exist
            }
        }
    }

    private String generateUniqueEmail(String firstName, String lastName, Set<String> used) {
        String baseEmail = (firstName + "." + lastName + "@company.com").toLowerCase().replaceAll("[^a-z0-9.@]", "");
        if (!used.contains(baseEmail)) {
            used.add(baseEmail);
            return baseEmail;
        }

        int counter = 1;
        String email;
        do {
            email = (firstName + "." + lastName + counter + "@company.com").toLowerCase().replaceAll("[^a-z0-9.@]", "");
            counter++;
        } while (used.contains(email));

        used.add(email);
        return email;
    }

    private void showResults() {
        System.out.println("\n=== Table Record Counts ===");
        String[] tables = {"jobs", "departments", "employees", "dependents", "categories",
                "products", "customers", "orders", "order_items", "warehouses",
                "suppliers", "inventory"};

        for (String table : tables) {
            try {
                int count = db.getCount(table);
                if (count > 0) {
                    System.out.printf("%-15s: %d records%n", table, count);
                }
            } catch (SQLException e) {
                // Table doesn't exist, skip
            }
        }
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // Main method
    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);

            SchemaGenerator generator = new SchemaGenerator(config);
            generator.generate();
            System.out.println("\nDatabase generation completed successfully!");
            generateClaudeConfig(config);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateClaudeConfig(Config config) {
        String jsonTemplate = String.format("""
                            "%s": {
                              "command": "%s",
                              "args": [
                                "-jar",
                                "%s"
                              ],
                              "env": {
                                "DB_URL": "%s",
                                "DB_USER": "%s",
                                "DB_PASSWORD": "%s",
                                "DB_DRIVER": "%s"
                              }
                            }""",
                config.schema, getJava(), getJar(), config.dbUrl, config.dbUser, config.dbPassword, config.dbDriver);
        System.out.println(jsonTemplate);
    }

    private static String getJar() {
        try {
            CodeSource codeSource = SchemaGenerator.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                File jarFile = new File(codeSource.getLocation().toURI().getPath());
                return jarFile.getAbsolutePath();
            } else {
                return ("CANNOT LOCATE JAR FILE - PLEASE LOCATE MANUALLY");
            }
        } catch (URISyntaxException e) {
            System.out.println(e.getMessage());
        }
        return ("CANNOT LOCATE JAR FILE - PLEASE LOCATE MANUALLY");
    }

    private static String getJava() {
        Optional<String> javaCommand = ProcessHandle.current().info().command();
        return javaCommand.orElse("CANNOT LOCATE JAVA - PLEASE LOCATE MANUALLY");
    }

    private static Config parseArgs(String[] args) {
        String dbUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        String dbDriver = "org.h2.Driver";
        String dbUser = "sa";
        String dbPassword = "";
        String schema = "combined";
        int employees = 50;
        int customers = 100;
        int orders = 200;
        int warehouses = 5;
        int suppliers = 10;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--schema" -> {
                    if (i + 1 < args.length) schema = args[++i];
                }
                case "--db-url" -> {
                    if (i + 1 < args.length) dbUrl = args[++i];
                }
                case "--db-driver" -> {
                    if (i + 1 < args.length) dbDriver = args[++i];
                }
                case "--db-user" -> {
                    if (i + 1 < args.length) dbUser = args[++i];
                }
                case "--db-password" -> {
                    if (i + 1 < args.length) dbPassword = args[++i];
                }
                case "--employees" -> {
                    if (i + 1 < args.length) employees = Integer.parseInt(args[++i]);
                }
                case "--customers" -> {
                    if (i + 1 < args.length) customers = Integer.parseInt(args[++i]);
                }
                case "--orders" -> {
                    if (i + 1 < args.length) orders = Integer.parseInt(args[++i]);
                }
                case "--warehouses" -> {
                    if (i + 1 < args.length) warehouses = Integer.parseInt(args[++i]);
                }
                case "--suppliers" -> {
                    if (i + 1 < args.length) suppliers = Integer.parseInt(args[++i]);
                }
                case "--help" -> {
                    printUsage();
                    System.exit(0);
                }
            }
        }

        return new Config(dbUrl, dbDriver, dbUser, dbPassword, schema, employees, customers, orders, warehouses, suppliers);
    }

    private static void printUsage() {
        System.out.println("Usage: java SimpleDBGenerator [options]");
        System.out.println("Options:");
        System.out.println("  --schema <hr|sales|inventory|combined>  Schema to generate (default: combined)");
        System.out.println("  --db-url <url>                          Database URL");
        System.out.println("  --db-user <user>                        Database user (default: sa)");
        System.out.println("  --db-password <password>                Database password");
        System.out.println("  --employees <number>                    Number of employees (default: 50)");
        System.out.println("  --customers <number>                    Number of customers (default: 100)");
        System.out.println("  --orders <number>                       Number of orders (default: 200)");
        System.out.println("  --warehouses <number>                   Number of warehouses (default: 5)");
        System.out.println("  --suppliers <number>                    Number of suppliers (default: 10)");
        System.out.println("  --help                                  Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java SimpleDBGenerator --schema hr --employees 100");
        System.out.println("  java SimpleDBGenerator --schema sales --customers 150 --orders 300");
        System.out.println("  java SimpleDBGenerator --db-url jdbc:sqlite:test.db --schema combined");
        System.out.println("  java SimpleDBGenerator --db-url jdbc:mysql://localhost:3306/testdb --db-user admin --db-password secret");
    }
}
