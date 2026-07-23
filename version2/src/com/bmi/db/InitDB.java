package com.bmi.db;

import java.sql.*;

/**
 * 数据库初始化工具
 * 自动创建 health_db 数据库和所有表, 插入80种食物数据
 * 用法: java -cp .;postgresql-42.7.3.jar InitDB
 */
public class InitDB {
    // 先连默认的 postgres 数据库 (用于创建新数据库)
    static final String ADMIN_URL = "jdbc:postgresql://localhost:5433/postgres";
    static final String DB_USER = "postgres";
    static final String DB_PASS = "12345678";
    static final String DB_NAME = "health_db";

    public static void main(String[] args) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC 驱动未找到! 请确保 postgresql-42.7.3.jar 在当前目录");
            return;
        }

        // 第一步: 连接 postgres 数据库, 创建 health_db (如果不存在)
        System.out.println("正在连接 PostgreSQL 服务器...");
        try (Connection conn = DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASS)) {
            System.out.println("连接成功!");

            // 检查 health_db 是否已存在
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getCatalogs();
            boolean dbExists = false;
            while (rs.next()) {
                if (DB_NAME.equals(rs.getString(1))) {
                    dbExists = true;
                    break;
                }
            }
            rs.close();

            if (dbExists) {
                System.out.println("数据库 " + DB_NAME + " 已存在, 跳过创建");
            } else {
                // PostgreSQL 不允许在事务中创建数据库, 需要关闭自动提交
                conn.setAutoCommit(true);
                String sql = "CREATE DATABASE " + DB_NAME;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    System.out.println("数据库 " + DB_NAME + " 创建成功!");
                }
            }
        } catch (SQLException e) {
            System.out.println("连接失败: " + e.getMessage());
            return;
        }

        // 第二步: 连接 health_db, 创建所有表
        String healthUrl = "jdbc:postgresql://localhost:5433/" + DB_NAME;
        System.out.println("\n正在连接 " + DB_NAME + " 并创建表...");
        try (Connection conn = DriverManager.getConnection(healthUrl, DB_USER, DB_PASS)) {
            conn.setAutoCommit(true);

            // 用户表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) UNIQUE NOT NULL," +
                "  password VARCHAR(255) NOT NULL," +
                "  salt VARCHAR(64) NOT NULL DEFAULT ''," +
                "  gender VARCHAR(10)," +
                "  age INT CHECK (age > 0 AND age < 150)," +
                "  height DECIMAL(5,2) CHECK (height > 0)," +
                "  weight DECIMAL(5,2)," +
                "  waist DECIMAL(5,2)," +
                "  activity_level VARCHAR(20) DEFAULT '久坐'," +
                "  allergies VARCHAR(255)," +
                "  chronic_diseases VARCHAR(255)," +
                "  created_at TIMESTAMP DEFAULT NOW()" +
                ")");
            System.out.println("  [OK] users 表");

            // 兼容已存在库：补 weight / waist 列（注册时录入的基础属性）
            try {
                execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS weight DECIMAL(5,2)");
                execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS waist DECIMAL(5,2)");
                execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS allergies VARCHAR(255)");
                execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS chronic_diseases VARCHAR(255)");
                System.out.println("  [OK] users 表 weight/waist/allergies/chronic 字段已确认");
            } catch (SQLException e) {
                System.out.println("  [INFO] users 表 weight/waist 字段检查: " + e.getMessage());
            }

            // 健康记录表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS health_records (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  record_date DATE DEFAULT CURRENT_DATE," +
                "  weight DECIMAL(5,2) CHECK (weight > 0 AND weight < 500)," +
                "  body_fat DECIMAL(4,2) CHECK (body_fat >= 0 AND body_fat <= 100)," +
                "  water_rate DECIMAL(4,2) CHECK (water_rate >= 0 AND water_rate <= 100)," +
                "  protein_rate DECIMAL(4,2) CHECK (protein_rate >= 0 AND protein_rate <= 100)," +
                "  muscle_rate DECIMAL(4,2) CHECK (muscle_rate >= 0 AND muscle_rate <= 100)," +
                "  visceral_fat INT CHECK (visceral_fat >= 0 AND visceral_fat <= 30)," +
                "  bone_muscle DECIMAL(5,2)," +
                "  bone_mass DECIMAL(5,2)," +
                "  bmr DECIMAL(6,2)," +
                "  tdee DECIMAL(6,2)," +
                "  bmi DECIMAL(4,2)," +
                "  body_age INT," +
                "  waist DECIMAL(5,2)," +
                "  body_type VARCHAR(20)," +
                "  created_at TIMESTAMP DEFAULT NOW()" +
                ")");
            System.out.println("  [OK] health_records 表");

            // 兼容旧表: 如果没有 body_type 字段则添加
            try {
                execUpdate(conn, "ALTER TABLE health_records ADD COLUMN IF NOT EXISTS body_type VARCHAR(20)");
                System.out.println("  [OK] body_type 字段已确认");
            } catch (SQLException e) {
                System.out.println("  [INFO] body_type 字段检查: " + e.getMessage());
            }

            // 兼容旧表: 补 protein_rate / bone_mass（仪器测量属性）
            try {
                execUpdate(conn, "ALTER TABLE health_records ADD COLUMN IF NOT EXISTS protein_rate DECIMAL(4,2)");
                execUpdate(conn, "ALTER TABLE health_records ADD COLUMN IF NOT EXISTS bone_mass DECIMAL(5,2)");
                System.out.println("  [OK] protein_rate / bone_mass 字段已确认");
            } catch (SQLException e) {
                System.out.println("  [INFO] protein_rate / bone_mass 字段检查: " + e.getMessage());
            }

            // 运动记录表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS exercise_records (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  record_date DATE DEFAULT CURRENT_DATE," +
                "  exercise_type VARCHAR(50)," +
                "  duration INT CHECK (duration > 0)," +
                "  calories_burned INT CHECK (calories_burned >= 0)," +
                "  intensity VARCHAR(10) CHECK (intensity IN ('低','中','高'))" +
                ")");
            System.out.println("  [OK] exercise_records 表");

            // 目标表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS goals (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  goal_type VARCHAR(20)," +
                "  target_value DECIMAL(5,2)," +
                "  start_date DATE DEFAULT CURRENT_DATE," +
                "  end_date DATE" +
                ")");
            System.out.println("  [OK] goals 表");

            // 兼容旧库: 补 current_stage (目标分阶段进度)
            try {
                execUpdate(conn, "ALTER TABLE goals ADD COLUMN IF NOT EXISTS current_stage INT DEFAULT 1");
                System.out.println("  [OK] goals 表 current_stage 字段已确认");
            } catch (SQLException e) {
                System.out.println("  [INFO] goals 表 current_stage 字段检查: " + e.getMessage());
            }

            // 饮食记录表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS diet_records (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  record_date DATE DEFAULT CURRENT_DATE," +
                "  meal_type VARCHAR(10) CHECK (meal_type IN ('早餐','午餐','晚餐','加餐'))," +
                "  food_name VARCHAR(100)," +
                "  calories INT," +
                "  protein DECIMAL(5,2)," +
                "  carbs DECIMAL(5,2)," +
                "  fat DECIMAL(5,2)" +
                ")");
            System.out.println("  [OK] diet_records 表");

            // 成就表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS achievements (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  badge_name VARCHAR(50)," +
                "  achieved_date DATE DEFAULT CURRENT_DATE" +
                ")");
            System.out.println("  [OK] achievements 表");

            // AI报告表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS ai_reports (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  report_type VARCHAR(20)," +
                "  report_content TEXT," +
                "  created_at TIMESTAMP DEFAULT NOW()" +
                ")");
            System.out.println("  [OK] ai_reports 表");

            // 食物数据库表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS foods (" +
                "  id SERIAL PRIMARY KEY," +
                "  food_name VARCHAR(100)," +
                "  calories_per_100g INT," +
                "  protein DECIMAL(5,2)," +
                "  carbs DECIMAL(5,2)," +
                "  fat DECIMAL(5,2)," +
                "  default_grams INT DEFAULT 100" +
                ")");
            System.out.println("  [OK] foods 表");

            // 兼容已存在库：补 图片 / 感知哈希 / 状态(草稿) 列
            try { execUpdate(conn, "ALTER TABLE foods ADD COLUMN IF NOT EXISTS food_image BYTEA"); } catch (SQLException ignore) {}
            try { execUpdate(conn, "ALTER TABLE foods ADD COLUMN IF NOT EXISTS food_phash BIGINT"); } catch (SQLException ignore) {}
            try { execUpdate(conn, "ALTER TABLE foods ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '已发布'"); } catch (SQLException ignore) {}
            try { execUpdate(conn, "UPDATE foods SET status='已发布' WHERE status IS NULL"); } catch (SQLException ignore) {}
            System.out.println("  [OK] foods 图片/哈希/状态列");

            // 饮水记录表
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS water_records (" +
                "  id SERIAL PRIMARY KEY," +
                "  username VARCHAR(50) REFERENCES users(username)," +
                "  record_date DATE DEFAULT CURRENT_DATE," +
                "  amount_ml INT CHECK (amount_ml > 0 AND amount_ml <= 3000)," +
                "  note VARCHAR(100)," +
                "  created_at TIMESTAMP DEFAULT NOW()" +
                ")");
            execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_water_username_date ON water_records(username, record_date)");
            System.out.println("  [OK] water_records 表");

            // 每日饮水目标表（用户自定义目标，覆盖按体重估算值）
            execUpdate(conn,
                "CREATE TABLE IF NOT EXISTS water_goals (" +
                "  username VARCHAR(50) PRIMARY KEY REFERENCES users(username)," +
                "  goal_ml INT CHECK (goal_ml >= 500 AND goal_ml <= 6000)" +
                ")");
            System.out.println("  [OK] water_goals 表");

            // 创建索引
            execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_hr_username_date ON health_records(username, record_date)");
            execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_dr_username_date ON diet_records(username, record_date)");
            execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_er_username_date ON exercise_records(username, record_date)");
            System.out.println("  [OK] 索引创建");

            // 管理员模块扩展表
            System.out.println("\n正在初始化管理员模块...");
            initAdminTables(conn);

            // 检查 foods 表是否已有数据
            boolean hasFoodData = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet checkRs = stmt.executeQuery("SELECT COUNT(*) FROM foods")) {
                if (checkRs.next() && checkRs.getInt(1) > 0) {
                    hasFoodData = true;
                }
            }

            if (!hasFoodData) {
                System.out.println("\n正在插入80种食物数据...");
                insertFoods(conn);
                System.out.println("  [OK] 80种食物数据插入完成");
            } else {
                System.out.println("\n食物数据已存在, 跳过插入");
            }

            System.out.println("\n========================================");
            System.out.println("数据库初始化完成! 可以启动 HealthSystem 了");
            System.out.println("运行命令: java -Dfile.encoding=UTF-8 -cp .;postgresql-42.7.3.jar HealthSystem");
            System.out.println("========================================");

        } catch (SQLException e) {
            System.out.println("建表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void execUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void initAdminTables(Connection conn) throws SQLException {
        // 用户表扩展字段
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) DEFAULT '启用'");
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS expiry_date DATE");
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP");
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE");
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS checkin_days INT DEFAULT 0");
        execUpdate(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS device_type VARCHAR(50)");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_users_status ON users(account_status)");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(deleted)");
        System.out.println("  [OK] 用户表扩展字段");

        // 管理员账号表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS admins (" +
            "  id SERIAL PRIMARY KEY," +
            "  username VARCHAR(50) UNIQUE NOT NULL," +
            "  password VARCHAR(255) NOT NULL," +
            "  salt VARCHAR(64) NOT NULL," +
            "  role VARCHAR(20) DEFAULT 'admin'," +
            "  status VARCHAR(20) DEFAULT '启用'," +
            "  expiry_date DATE," +
            "  created_at TIMESTAMP DEFAULT NOW()," +
            "  last_login TIMESTAMP" +
            ")");
        System.out.println("  [OK] admins 表");

        // 医疗机构表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS institutions (" +
            "  id SERIAL PRIMARY KEY," +
            "  org_name VARCHAR(100) NOT NULL," +
            "  org_code VARCHAR(50) UNIQUE," +
            "  password VARCHAR(255) NOT NULL DEFAULT ''," +
            "  salt VARCHAR(64) NOT NULL DEFAULT ''," +
            "  contact VARCHAR(50)," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        // 兼容旧库: 补登录字段
        try {
            execUpdate(conn, "ALTER TABLE institutions ADD COLUMN IF NOT EXISTS password VARCHAR(255) NOT NULL DEFAULT ''");
            execUpdate(conn, "ALTER TABLE institutions ADD COLUMN IF NOT EXISTS salt VARCHAR(64) NOT NULL DEFAULT ''");
            System.out.println("  [OK] institutions 表 (含登录字段)");
        } catch (SQLException e) {
            System.out.println("  [INFO] institutions 登录字段检查: " + e.getMessage());
        }

        // 机构维度病人主表 (归属某机构, 不持有登录账号 — 与个人 users 登录解耦)
        // archived=TRUE 表示已被机构「移出名单」(软删除), 其健康记录仍保留。
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS patients (" +
            "  id SERIAL PRIMARY KEY," +
            "  institution_id INT NOT NULL REFERENCES institutions(id) ON DELETE CASCADE," +
            "  patient_code VARCHAR(50) NOT NULL," +
            "  name VARCHAR(100)," +
            "  gender VARCHAR(10)," +
            "  age INT," +
            "  height DECIMAL(5,2)," +
            "  weight DECIMAL(5,2)," +
            "  waist DECIMAL(5,2)," +
            "  activity_level VARCHAR(20) DEFAULT '久坐'," +
            "  allergies VARCHAR(255)," +
            "  chronic_diseases VARCHAR(255)," +
            "  archived BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMP DEFAULT NOW()," +
            "  updated_at TIMESTAMP DEFAULT NOW()," +
            "  UNIQUE (institution_id, patient_code)" +
            ")");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_patients_inst ON patients(institution_id, archived)");
        System.out.println("  [OK] patients 表 (机构维度病人主表)");

        // health_records 增加 patient_id: 机构记录经此关联到 patients, 与个人 users 解耦。
        // 个人记录仍用 username, patient_id 为 NULL。
        try {
            execUpdate(conn, "ALTER TABLE health_records ADD COLUMN IF NOT EXISTS patient_id INT");
            // 幂等添加外键(不存在才加), 失败时忽略
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM information_schema.table_constraints WHERE table_name='health_records' AND constraint_name='fk_hr_patient'")) {
                if (!ps.executeQuery().next()) {
                    execUpdate(conn, "ALTER TABLE health_records ADD CONSTRAINT fk_hr_patient " +
                            "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL");
                }
            }
            System.out.println("  [OK] health_records.patient_id 字段/外键");
        } catch (SQLException e) {
            System.out.println("  [INFO] health_records.patient_id: " + e.getMessage());
        }

        // 机构入驻申请表 (待管理员审批)
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS institution_requests (" +
            "  id SERIAL PRIMARY KEY," +
            "  org_name VARCHAR(100) NOT NULL," +
            "  contact VARCHAR(50)," +
            "  phone VARCHAR(30)," +
            "  note TEXT," +
            "  password VARCHAR(255) NOT NULL DEFAULT ''," +   // 机构自设密码的哈希(申请时设置)
            "  salt VARCHAR(64) NOT NULL DEFAULT ''," +
            "  status VARCHAR(20) NOT NULL DEFAULT 'pending'," +
            "  reviewer VARCHAR(50)," +
            "  review_note TEXT," +
            "  created_at TIMESTAMP DEFAULT NOW()," +
            "  reviewed_at TIMESTAMP" +
            ")");
        // 兼容旧库: 补密码字段
        try {
            execUpdate(conn, "ALTER TABLE institution_requests ADD COLUMN IF NOT EXISTS password VARCHAR(255) NOT NULL DEFAULT ''");
            execUpdate(conn, "ALTER TABLE institution_requests ADD COLUMN IF NOT EXISTS salt VARCHAR(64) NOT NULL DEFAULT ''");
        } catch (SQLException e) { /* 已存在则忽略 */ }
        System.out.println("  [OK] institution_requests 表");

        // 系统配置表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS system_config (" +
            "  id SERIAL PRIMARY KEY," +
            "  config_key VARCHAR(100) UNIQUE NOT NULL," +
            "  config_value TEXT," +
            "  description VARCHAR(255)," +
            "  updated_at TIMESTAMP DEFAULT NOW()" +
            ")");
        System.out.println("  [OK] system_config 表");

        // 系统日志表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS system_logs (" +
            "  id SERIAL PRIMARY KEY," +
            "  log_type VARCHAR(50)," +
            "  operator VARCHAR(50)," +
            "  action VARCHAR(255)," +
            "  detail TEXT," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_system_logs_time ON system_logs(created_at)");
        System.out.println("  [OK] system_logs 表");

        // 运动库表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS exercise_library (" +
            "  id SERIAL PRIMARY KEY," +
            "  exercise_name VARCHAR(50) UNIQUE NOT NULL," +
            "  exercise_type VARCHAR(20)," +
            "  calories_per_hour INT," +
            "  intensity_level VARCHAR(10)," +
            "  description TEXT" +
            ")");
        System.out.println("  [OK] exercise_library 表");

        // 健康文章表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS health_articles (" +
            "  id SERIAL PRIMARY KEY," +
            "  title VARCHAR(200) NOT NULL," +
            "  content TEXT," +
            "  category VARCHAR(50)," +
            "  published_at TIMESTAMP DEFAULT NOW()," +
            "  status VARCHAR(20) DEFAULT '已发布'" +
            ")");
        System.out.println("  [OK] health_articles 表");

        // 消息模板表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS message_templates (" +
            "  id SERIAL PRIMARY KEY," +
            "  template_name VARCHAR(100) NOT NULL," +
            "  template_content TEXT NOT NULL," +
            "  type VARCHAR(20)," +
            "  variables VARCHAR(255)" +
            ")");
        System.out.println("  [OK] message_templates 表");

        // 通知消息表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS notifications (" +
            "  id SERIAL PRIMARY KEY," +
            "  sender VARCHAR(50)," +
            "  receiver VARCHAR(50)," +
            "  title VARCHAR(200)," +
            "  content TEXT," +
            "  type VARCHAR(20)," +
            "  status VARCHAR(20) DEFAULT '未读'," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_notifications_receiver ON notifications(receiver)");
        System.out.println("  [OK] notifications 表");

        // AI 配置表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS ai_api_config (" +
            "  id SERIAL PRIMARY KEY," +
            "  provider VARCHAR(50)," +
            "  api_key VARCHAR(255)," +
            "  model_name VARCHAR(100)," +
            "  endpoint VARCHAR(255)," +
            "  enabled BOOLEAN DEFAULT TRUE," +
            "  call_count INT DEFAULT 0," +
            "  cost_estimate DECIMAL(10,2) DEFAULT 0.00," +
            "  updated_at TIMESTAMP DEFAULT NOW()" +
            ")");
        System.out.println("  [OK] ai_api_config 表");
        // 兼容已存在库：补 vision_model 列（识图用视觉模型）
        try { execUpdate(conn, "ALTER TABLE ai_api_config ADD COLUMN IF NOT EXISTS vision_model VARCHAR(100) DEFAULT 'glm-4v-flash'"); } catch (SQLException ignore) {}

        // 兼容已存在库：补代理列（校园/企业网经代理访问外网）
        try { execUpdate(conn, "ALTER TABLE ai_api_config ADD COLUMN IF NOT EXISTS proxy_host VARCHAR(100)"); } catch (SQLException ignore) {}
        try { execUpdate(conn, "ALTER TABLE ai_api_config ADD COLUMN IF NOT EXISTS proxy_port VARCHAR(10)"); } catch (SQLException ignore) {}

        // AI 提示词模板表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS ai_templates (" +
            "  id SERIAL PRIMARY KEY," +
            "  template_name VARCHAR(100) NOT NULL," +
            "  prompt_text TEXT NOT NULL," +
            "  template_type VARCHAR(50)," +
            "  status VARCHAR(20) DEFAULT '有效'," +
            "  updated_at TIMESTAMP DEFAULT NOW()" +
            ")");
        System.out.println("  [OK] ai_templates 表");

        // AI 问答记录表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS ai_chat_records (" +
            "  id SERIAL PRIMARY KEY," +
            "  username VARCHAR(50) REFERENCES users(username)," +
            "  question TEXT," +
            "  answer TEXT," +
            "  status VARCHAR(20) DEFAULT '有效'," +
            "  notified BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        execUpdate(conn, "ALTER TABLE ai_chat_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '有效'");
        execUpdate(conn, "ALTER TABLE ai_chat_records ADD COLUMN IF NOT EXISTS notified BOOLEAN DEFAULT FALSE");
        execUpdate(conn, "CREATE INDEX IF NOT EXISTS idx_ai_chat_user ON ai_chat_records(username)");
        System.out.println("  [OK] ai_chat_records 表");

        // AI 饮食推荐记录表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS ai_diet_records (" +
            "  id SERIAL PRIMARY KEY," +
            "  username VARCHAR(50) REFERENCES users(username)," +
            "  query TEXT," +
            "  result TEXT," +
            "  status VARCHAR(20) DEFAULT '有效'," +
            "  notified BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        execUpdate(conn, "ALTER TABLE ai_diet_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '有效'");
        execUpdate(conn, "ALTER TABLE ai_diet_records ADD COLUMN IF NOT EXISTS notified BOOLEAN DEFAULT FALSE");
        System.out.println("  [OK] ai_diet_records 表");

        // AI 菜谱生成记录表
        execUpdate(conn,
            "CREATE TABLE IF NOT EXISTS ai_cookbook_records (" +
            "  id SERIAL PRIMARY KEY," +
            "  username VARCHAR(50) REFERENCES users(username)," +
            "  ingredients TEXT," +
            "  flavor VARCHAR(50)," +
            "  meal VARCHAR(20)," +
            "  people INT," +
            "  result TEXT," +
            "  status VARCHAR(20) DEFAULT '有效'," +
            "  notified BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMP DEFAULT NOW()" +
            ")");
        execUpdate(conn, "ALTER TABLE ai_cookbook_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '有效'");
        execUpdate(conn, "ALTER TABLE ai_cookbook_records ADD COLUMN IF NOT EXISTS notified BOOLEAN DEFAULT FALSE");
        System.out.println("  [OK] ai_cookbook_records 表");

        // 插入默认系统配置
        insertConfigIfNotExists(conn, "weight_change_threshold", "5.0", "7天体重变化阈值(kg)");
        insertConfigIfNotExists(conn, "body_fat_rise_days", "30", "体脂连续上升天数阈值");
        insertConfigIfNotExists(conn, "no_checkin_days", "7", "长期未打卡天数阈值");
        insertConfigIfNotExists(conn, "bmi_low", "18.5", "BMI 正常下限");
        insertConfigIfNotExists(conn, "bmi_high", "24.9", "BMI 正常上限");
        insertConfigIfNotExists(conn, "bmr_formula", "avg", "BMR 计算公式：mifflin/harris/avg");
        insertConfigIfNotExists(conn, "weight_loss_speed", "0.5", "减脂速度 kg/周");
        insertConfigIfNotExists(conn, "muscle_gain_speed", "0.3", "增肌速度 kg/周");
        insertConfigIfNotExists(conn, "checkin_reminder_time", "20:00", "打卡提醒时间");
        insertConfigIfNotExists(conn, "checkin_reward_rule", "连续7天打卡奖励1枚徽章", "连续打卡奖励规则");
        System.out.println("  [OK] 默认系统配置");

        // 插入默认运动库
        insertExerciseLibIfNotExists(conn, "跑步", "有氧", 600, "中", "户外或跑步机跑步");
        insertExerciseLibIfNotExists(conn, "游泳", "有氧", 700, "高", "全身性有氧运动");
        insertExerciseLibIfNotExists(conn, "力量训练", "力量", 400, "高", "器械或自重训练");
        insertExerciseLibIfNotExists(conn, "骑行", "有氧", 500, "中", "户外或动感单车");
        insertExerciseLibIfNotExists(conn, "瑜伽", "有氧", 200, "低", "柔韧性及放松训练");
        insertExerciseLibIfNotExists(conn, "跳绳", "有氧", 700, "高", "高强度间歇训练");
        insertExerciseLibIfNotExists(conn, "快走", "有氧", 300, "低", "低强度有氧");
        insertExerciseLibIfNotExists(conn, "球类", "有氧", 500, "中", "篮球、足球等");
        System.out.println("  [OK] 默认运动库");

        // 插入默认消息模板
        insertTemplateIfNotExists(conn, "系统通知", "您好 {username}，系统将于 {date} 进行维护，请提前保存数据。", "系统通知", "{username},{date}");
        insertTemplateIfNotExists(conn, "健康提醒", "您好 {username}，您已 {days} 天未打卡，记得保持健康记录！", "健康提醒", "{username},{days}");
        insertTemplateIfNotExists(conn, "活动推送", "您好 {username}，本周有健康挑战活动，欢迎参加！", "活动推送", "{username}");
        System.out.println("  [OK] 默认消息模板");

        // 插入默认 AI 模板
        insertAiTemplateIfNotExists(conn, "健康建议模板", "根据用户健康数据生成个性化健康建议...", "建议");
        insertAiTemplateIfNotExists(conn, "周报模板", "根据本周数据生成健康周报...", "周报");
        insertAiTemplateIfNotExists(conn, "饮食推荐模板", "根据用户目标推荐今日饮食...", "饮食推荐");
        System.out.println("  [OK] 默认 AI 模板");
    }

    private static void insertConfigIfNotExists(Connection conn, String key, String value, String desc) throws SQLException {
        String sql = "INSERT INTO system_config (config_key, config_value, description) VALUES (?, ?, ?) " +
                     "ON CONFLICT (config_key) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, desc);
            ps.executeUpdate();
        }
    }

    private static void insertExerciseLibIfNotExists(Connection conn, String name, String type, int cal, String intensity, String desc) throws SQLException {
        String sql = "INSERT INTO exercise_library (exercise_name, exercise_type, calories_per_hour, intensity_level, description) " +
                     "VALUES (?, ?, ?, ?, ?) ON CONFLICT (exercise_name) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, type); ps.setInt(3, cal); ps.setString(4, intensity); ps.setString(5, desc);
            ps.executeUpdate();
        }
    }

    private static void insertTemplateIfNotExists(Connection conn, String name, String content, String type, String vars) throws SQLException {
        String sql = "INSERT INTO message_templates (template_name, template_content, type, variables) " +
                     "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, content); ps.setString(3, type); ps.setString(4, vars);
            ps.executeUpdate();
        }
    }

    private static void insertAiTemplateIfNotExists(Connection conn, String name, String content, String type) throws SQLException {
        String sql = "INSERT INTO ai_templates (template_name, template_type, prompt_text) " +
                     "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, type); ps.setString(3, content);
            ps.executeUpdate();
        }
    }

    /** 每种食物的「标准一份」克数（用户选菜时克数自动带出，不必每次手改） */
    private static final java.util.Map<String, Integer> STD_PORTION_GRAMS = java.util.Map.ofEntries(
            java.util.Map.entry("米饭", 150), java.util.Map.entry("面条", 200),
            java.util.Map.entry("馒头", 100), java.util.Map.entry("包子", 100),
            java.util.Map.entry("饺子", 100), java.util.Map.entry("油条", 50),
            java.util.Map.entry("豆浆", 250), java.util.Map.entry("牛奶", 250),
            java.util.Map.entry("鸡蛋", 50), java.util.Map.entry("鸡胸肉", 100),
            java.util.Map.entry("鸡腿肉", 100), java.util.Map.entry("猪肉瘦", 100),
            java.util.Map.entry("猪肉肥", 50), java.util.Map.entry("牛肉瘦", 100),
            java.util.Map.entry("牛肉肥", 100), java.util.Map.entry("羊肉", 100),
            java.util.Map.entry("鱼肉", 100), java.util.Map.entry("虾肉", 100),
            java.util.Map.entry("豆腐", 100), java.util.Map.entry("豆腐干", 50),
            java.util.Map.entry("豆浆粉", 30), java.util.Map.entry("白菜", 100),
            java.util.Map.entry("菠菜", 100), java.util.Map.entry("西兰花", 100),
            java.util.Map.entry("菜花", 100), java.util.Map.entry("黄瓜", 100),
            java.util.Map.entry("西红柿", 100), java.util.Map.entry("茄子", 100),
            java.util.Map.entry("青椒", 100), java.util.Map.entry("土豆", 100),
            java.util.Map.entry("红薯", 150), java.util.Map.entry("山药", 100),
            java.util.Map.entry("南瓜", 100), java.util.Map.entry("冬瓜", 100),
            java.util.Map.entry("萝卜", 100), java.util.Map.entry("胡萝卜", 100),
            java.util.Map.entry("洋葱", 100), java.util.Map.entry("大蒜", 10),
            java.util.Map.entry("姜", 10), java.util.Map.entry("苹果", 200),
            java.util.Map.entry("香蕉", 100), java.util.Map.entry("橙子", 200),
            java.util.Map.entry("西瓜", 200), java.util.Map.entry("哈密瓜", 200),
            java.util.Map.entry("葡萄", 100), java.util.Map.entry("草莓", 100),
            java.util.Map.entry("蓝莓", 100), java.util.Map.entry("桃子", 200),
            java.util.Map.entry("梨", 200), java.util.Map.entry("李子", 100),
            java.util.Map.entry("樱桃", 100), java.util.Map.entry("芒果", 200),
            java.util.Map.entry("火龙果", 200), java.util.Map.entry("猕猴桃", 100),
            java.util.Map.entry("柠檬", 50), java.util.Map.entry("核桃", 30),
            java.util.Map.entry("花生", 30), java.util.Map.entry("瓜子", 30),
            java.util.Map.entry("黑芝麻", 20), java.util.Map.entry("小米", 50),
            java.util.Map.entry("玉米", 150), java.util.Map.entry("燕麦", 40),
            java.util.Map.entry("荞麦", 50), java.util.Map.entry("意面", 200),
            java.util.Map.entry("蛋糕", 100), java.util.Map.entry("面包", 100),
            java.util.Map.entry("饼干", 30), java.util.Map.entry("巧克力", 30),
            java.util.Map.entry("冰淇淋", 100), java.util.Map.entry("酸奶", 200),
            java.util.Map.entry("奶酪", 30), java.util.Map.entry("黄油", 20),
            java.util.Map.entry("植物油", 10), java.util.Map.entry("酱油", 15),
            java.util.Map.entry("醋", 15), java.util.Map.entry("蜂蜜", 20),
            java.util.Map.entry("白糖", 10), java.util.Map.entry("红糖", 10),
            java.util.Map.entry("盐", 5), java.util.Map.entry("茶叶", 5),
            java.util.Map.entry("咖啡", 200));

    private static void insertFoods(Connection conn) throws SQLException {
        String sql = "INSERT INTO foods (food_name, calories_per_100g, protein, carbs, fat, default_grams) VALUES (?, ?, ?, ?, ?, ?)";
        String[][] foods = {
            {"米饭","116","2.6","25.9","0.3"}, {"面条","137","4.5","28.5","0.5"},
            {"馒头","221","7.0","47.0","1.0"}, {"包子","220","7.5","38.0","4.0"},
            {"饺子","250","8.0","30.0","10.0"}, {"油条","388","6.9","43.0","20.0"},
            {"豆浆","33","3.0","1.5","1.8"}, {"牛奶","54","3.0","4.5","3.0"},
            {"鸡蛋","144","13.0","1.5","9.0"}, {"鸡胸肉","133","31.0","0.0","2.8"},
            {"鸡腿肉","181","24.0","0.0","9.0"}, {"猪肉瘦","143","20.0","0.0","6.0"},
            {"猪肉肥","395","14.0","0.0","37.0"}, {"牛肉瘦","125","26.0","0.0","2.0"},
            {"牛肉肥","332","18.0","0.0","28.0"}, {"羊肉","205","20.0","0.0","13.0"},
            {"鱼肉","113","20.0","0.0","3.0"}, {"虾肉","85","18.0","0.0","1.0"},
            {"豆腐","81","8.0","3.5","4.0"}, {"豆腐干","140","16.0","5.0","6.0"},
            {"豆浆粉","422","18.0","55.0","12.0"}, {"白菜","13","1.5","2.0","0.1"},
            {"菠菜","23","2.5","3.0","0.3"}, {"西兰花","34","3.0","5.0","0.5"},
            {"菜花","25","2.0","4.0","0.3"}, {"黄瓜","15","0.8","3.0","0.1"},
            {"西红柿","18","0.9","4.0","0.2"}, {"茄子","25","1.0","5.0","0.2"},
            {"青椒","22","1.0","4.5","0.2"}, {"土豆","77","2.0","17.0","0.1"},
            {"红薯","86","1.6","20.0","0.1"}, {"山药","57","1.5","12.0","0.1"},
            {"南瓜","23","1.0","5.0","0.1"}, {"冬瓜","12","0.4","2.5","0.1"},
            {"萝卜","16","0.6","3.5","0.1"}, {"胡萝卜","41","1.0","9.5","0.2"},
            {"洋葱","40","1.1","9.0","0.1"}, {"大蒜","149","6.0","33.0","0.5"},
            {"姜","80","1.8","17.0","0.8"}, {"苹果","52","0.3","14.0","0.2"},
            {"香蕉","89","1.1","23.0","0.3"}, {"橙子","47","0.9","12.0","0.1"},
            {"西瓜","30","0.6","7.5","0.1"}, {"哈密瓜","34","0.5","8.0","0.1"},
            {"葡萄","69","0.7","18.0","0.2"}, {"草莓","32","0.7","7.5","0.3"},
            {"蓝莓","57","0.7","14.5","0.3"}, {"桃子","39","0.9","10.0","0.1"},
            {"梨","42","0.4","10.0","0.1"}, {"李子","46","0.7","12.0","0.3"},
            {"樱桃","50","1.0","12.0","0.3"}, {"芒果","60","0.8","15.0","0.4"},
            {"火龙果","60","1.2","14.0","0.4"}, {"猕猴桃","61","1.0","15.0","0.5"},
            {"柠檬","29","1.1","6.0","0.3"}, {"核桃","654","15.0","14.0","65.0"},
            {"花生","567","26.0","16.0","49.0"}, {"瓜子","600","24.0","12.0","53.0"},
            {"黑芝麻","559","17.0","12.0","50.0"}, {"小米","358","9.0","75.0","3.0"},
            {"玉米","112","3.5","22.0","1.5"}, {"燕麦","389","17.0","66.0","7.0"},
            {"荞麦","337","13.0","71.0","3.0"}, {"意面","131","5.0","25.0","0.5"},
            {"蛋糕","348","5.0","45.0","17.0"}, {"面包","265","9.0","50.0","3.0"},
            {"饼干","450","6.0","65.0","18.0"}, {"巧克力","546","4.0","60.0","32.0"},
            {"冰淇淋","207","3.5","24.0","11.0"}, {"酸奶","72","3.5","8.0","3.0"},
            {"奶酪","360","25.0","2.0","28.0"}, {"黄油","717","0.5","0.0","81.0"},
            {"植物油","884","0.0","0.0","100.0"}, {"酱油","60","2.0","10.0","0.0"},
            {"醋","18","0.4","4.0","0.0"}, {"蜂蜜","304","0.3","82.0","0.0"},
            {"白糖","387","0.0","100.0","0.0"}, {"红糖","380","0.0","95.0","0.0"},
            {"盐","0","0.0","0.0","0.0"}, {"茶叶","0","0.0","0.0","0.0"},
            {"咖啡","1","0.1","0.0","0.0"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (String[] food : foods) {
                pstmt.setString(1, food[0]);
                pstmt.setInt(2, Integer.parseInt(food[1]));
                pstmt.setDouble(3, Double.parseDouble(food[2]));
                pstmt.setDouble(4, Double.parseDouble(food[3]));
                pstmt.setDouble(5, Double.parseDouble(food[4]));
                pstmt.setInt(6, STD_PORTION_GRAMS.getOrDefault(food[0], 100));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
}
