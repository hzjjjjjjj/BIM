package com.bmi.db;

import com.bmi.util.ExcelUtil;
import com.bmi.util.HealthCalculator;
import com.bmi.util.ImageUtil;
import com.bmi.util.PasswordUtil;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.text.*;
import java.net.*;
import java.io.*;
import java.awt.image.BufferedImage;

public class DBUtil {
    /** 错误日志文件 (位于程序运行目录, 便于复制排查) */
    public static final String ERROR_LOG = System.getProperty("user.dir") + File.separator + "bmi_error.log";

    /** 把异常的完整堆栈写入日志文件, 方便用户复制排查 (弹窗文字默认不可选中) */
    public static void logError(String context, Throwable t) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ERROR_LOG, true))) {
            pw.println("==== " + new Timestamp(System.currentTimeMillis()) + " ====");
            pw.println("context: " + context);
            t.printStackTrace(pw);
            pw.println();
            pw.flush();
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }
    }

    public static String currentUsername = "";
    public static int currentInstitutionId = 0;
    public static String currentInstitutionName = "";
    public static String currentGender = "";
    public static int currentAge = 0;
    public static double currentHeight = 0;
    public static double currentWeight = 0;
    public static double currentWaist = 0;
    public static String currentActivityLevel = "久坐";
    public static String currentAllergies = "";
    public static String currentChronicDiseases = "";
    private static final String DB_URL = "jdbc:postgresql://localhost:5433/health_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "12345678";
    public static final DecimalFormat df1 = new DecimalFormat("#0.0");
    public static final DecimalFormat df2 = new DecimalFormat("#0.00");

        /** 获取数据库连接 */
        public static Connection getConnection() throws SQLException {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("PostgreSQL JDBC 驱动未找到, 请确认 postgresql-42.7.3.jar 在 classpath 中");
            }
            migrateSchema(); // 首次连接时幂等补齐缺失列, 使库结构与代码对齐
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }

        private static boolean schemaMigrated = false;

        /**
         * 启动时幂等迁移: 补齐运行中数据库因旧版建库脚本缺失的列,
         * 使库结构与代码 / 建表 SQL 对齐 (例如 goals.current_stage)。
         * 仅首次成功执行后标记; 失败则记录日志并在下次 getConnection 时重试, 不阻断业务。
         */
        public static void migrateSchema() {
            if (schemaMigrated) return;
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                return; // 驱动都没加载, 后续业务也会失败, 这里直接跳过
            }
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                conn.setAutoCommit(true);
                // goals: 目标分阶段进度列 (旧库建库脚本缺此列)
                execDDL(conn, "ALTER TABLE goals ADD COLUMN IF NOT EXISTS current_stage INT DEFAULT 1");
                // patients: 机构维度病人主表 (与个人 users 登录解耦)。幂等建表, 兼容已存在的旧库。
                createPatientsTable(conn);
                // health_records: 机构记录经 patient_id 关联到 patients 表 (与个人 users 解耦)
                execDDL(conn, "ALTER TABLE health_records ADD COLUMN IF NOT EXISTS patient_id INT");
                addFkIfMissing(conn, "health_records", "fk_hr_patient",
                        "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL");
                // users: 过敏源 / 慢性病 列 (个人用户健康画像)
                execDDL(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS allergies VARCHAR(255)");
                execDDL(conn, "ALTER TABLE users ADD COLUMN IF NOT EXISTS chronic_diseases VARCHAR(255)");
                // patients: 机构维度病人同样需要过敏源 / 慢性病 字段
                execDDL(conn, "ALTER TABLE patients ADD COLUMN IF NOT EXISTS allergies VARCHAR(255)");
                execDDL(conn, "ALTER TABLE patients ADD COLUMN IF NOT EXISTS chronic_diseases VARCHAR(255)");
                // health_articles: 投稿/审核治理字段 (用户/机构投稿→管理员审核→发布)
                execDDL(conn, "ALTER TABLE health_articles ADD COLUMN IF NOT EXISTS author VARCHAR(100)");
                execDDL(conn, "ALTER TABLE health_articles ADD COLUMN IF NOT EXISTS author_type VARCHAR(20)");
                execDDL(conn, "ALTER TABLE health_articles ADD COLUMN IF NOT EXISTS reviewer VARCHAR(100)");
                execDDL(conn, "ALTER TABLE health_articles ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP");
                execDDL(conn, "ALTER TABLE health_articles ADD COLUMN IF NOT EXISTS reject_reason TEXT");
                // 旧模型残留的 institution_patients 已废弃 (被 patients 表取代), 清理之
                execDDL(conn, "DROP TABLE IF EXISTS institution_patients");
                // 后续若发现其它列漂移, 在此追加 ALTER TABLE ... ADD COLUMN IF NOT EXISTS ... 即可
                schemaMigrated = true;
            } catch (SQLException e) {
                logError("migrateSchema", e);
                // 不抛异常, 不阻断业务; 下次 getConnection 时再尝试
            }
        }

        private static void execDDL(Connection conn, String sql) throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }

        /** 幂等创建 patients 表 (机构维度病人主表, 与个人 users 登录解耦) */
        private static void createPatientsTable(Connection conn) throws SQLException {
            execDDL(conn,
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
            execDDL(conn, "CREATE INDEX IF NOT EXISTS idx_patients_inst ON patients(institution_id, archived)");
        }

        /** 幂等添加外键: 仅当约束名不存在时才执行, 避免重复添加报错 */
        private static void addFkIfMissing(Connection conn, String table, String constraint, String def) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM information_schema.table_constraints WHERE table_name = ? AND constraint_name = ?")) {
                ps.setString(1, table);
                ps.setString(2, constraint);
                if (ps.executeQuery().next()) return; // 已存在
            } catch (SQLException ignore) {
                return; // 查询异常则跳过, 不阻断
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " " + def);
            } catch (SQLException ignore) {
                // 添加失败(如脏数据)则忽略, 不阻断业务
            }
        }

        /** 测试数据库连接 */
        public static boolean testConnection() {
            try (Connection conn = getConnection()) {
                return conn != null;
            } catch (SQLException e) {
                return false;
            }
        }

        // ==================== 用户相关操作 ====================

        /** 注册用户（含基础属性：性别/年龄/身高/体重/腰围/活动水平） */
        public static boolean registerUser(String username, String password, String gender,
                                     int age, double height, String activityLevel,
                                     double weight, Double waist, String allergies, String chronicDiseases) {
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash(password, salt);
            String sql = "INSERT INTO users (username, password, salt, gender, age, height, weight, waist, activity_level, allergies, chronic_diseases) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.setString(3, salt);
                ps.setString(4, gender);
                ps.setInt(5, age);
                ps.setDouble(6, height);
                if (weight > 0) ps.setDouble(7, weight); else ps.setNull(7, Types.DOUBLE);
                if (waist != null && waist > 0) ps.setDouble(8, waist); else ps.setNull(8, Types.DOUBLE);
                ps.setString(9, activityLevel);
                ps.setString(10, allergies != null ? allergies : "");
                ps.setString(11, chronicDiseases != null ? chronicDiseases : "");
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                if (e.getSQLState().equals("23505")) {
                    e.printStackTrace();
                } else {
                    e.printStackTrace();
                }
                return false;
            }
        }

        /** 验证登录 */
        public static boolean loginUser(String username, String password) {
            String sql = "SELECT password, salt, gender, age, height, weight, waist, activity_level, allergies, chronic_diseases FROM users WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String hash = rs.getString("password");
                    String salt = rs.getString("salt");
                    if (PasswordUtil.verify(password, salt, hash)) {
                        currentUsername = username;
                        currentGender = rs.getString("gender");
                        currentAge = rs.getInt("age");
                        currentHeight = rs.getDouble("height");
                        currentWeight = rs.getDouble("weight");
                        currentWaist = rs.getDouble("waist");
                        currentActivityLevel = rs.getString("activity_level");
                        if (currentActivityLevel == null) currentActivityLevel = "久坐";
                        currentAllergies = rs.getString("allergies");
                        currentChronicDiseases = rs.getString("chronic_diseases");
                        if (currentAllergies == null) currentAllergies = "";
                        if (currentChronicDiseases == null) currentChronicDiseases = "";
                        return true;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        /** 更新当前用户的过敏源与慢性病信息 (个人健康画像, 仅本人可改) */
        public static boolean updateUserAllergyChronic(String allergies, String chronicDiseases) {
            if (currentUsername == null || currentUsername.isEmpty()) return false;
            String sql = "UPDATE users SET allergies = ?, chronic_diseases = ? WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, allergies != null ? allergies : "");
                ps.setString(2, chronicDiseases != null ? chronicDiseases : "");
                ps.setString(3, currentUsername);
                int n = ps.executeUpdate();
                if (n > 0) {
                    currentAllergies = allergies != null ? allergies : "";
                    currentChronicDiseases = chronicDiseases != null ? chronicDiseases : "";
                    return true;
                }
                return false;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * 注册时生成初始健康记录：仅写基础属性(体重/腰围) + 由基础算出的 BMI/BMR/TDEE/粗略体型，
         * 仪器测量列(body_fat/water_rate/...) 留 NULL（Postgres 中 CHECK 对 NULL 放行）。
         * 让数据大屏/分析评估在注册后即可见数，无需先去数据录入逐项填仪器值。
         */
        public static boolean saveBaselineHealthRecord(String username, double weight, double height,
                int age, String gender, String activityLevel, Double waist) {
            if (weight <= 0) return false;
            double bmi = HealthCalculator.calcBMI(weight, height);
            double bmr = HealthCalculator.calcAvgBMR(weight, height, age, gender);
            double tdee = HealthCalculator.calcTDEE(bmr, activityLevel);
            String bodyType = HealthCalculator.classifyBMI(bmi); // 粗略体型/肥胖等级
            String sql = "INSERT INTO health_records (username, weight, waist, bmi, bmr, tdee, body_type) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setDouble(2, weight);
                if (waist != null && waist > 0) ps.setDouble(3, waist); else ps.setNull(3, Types.DOUBLE);
                ps.setDouble(4, bmi);
                ps.setDouble(5, bmr);
                ps.setDouble(6, tdee);
                ps.setString(7, bodyType);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        // ==================== 健康记录操作 ====================

        /** 保存健康记录 */
        public static boolean saveHealthRecord(double weight, double bodyFat, double waterRate,
                double proteinRate, double muscleRate, int visceralFat, double boneMuscle,
                double boneMass, double waist) {
            // 计算派生数据
            double bmi = HealthCalculator.calcBMI(weight, currentHeight);
            double bmr = HealthCalculator.calcAvgBMR(weight, currentHeight, currentAge, currentGender);
            double tdee = HealthCalculator.calcTDEE(bmr, currentActivityLevel);
            int bodyAge = HealthCalculator.calcBodyAge(currentAge, bodyFat, muscleRate, visceralFat, currentGender);
            String bodyType = HealthCalculator.classifyBodyType(bmi, bodyFat, currentGender);

            String sql = "INSERT INTO health_records (username, weight, body_fat, water_rate, protein_rate, " +
                         "muscle_rate, visceral_fat, bone_muscle, bone_mass, bmr, tdee, bmi, waist, body_age, body_type) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String updateCheckinSql = "UPDATE users SET checkin_days = (" +
                    "SELECT COUNT(DISTINCT record_date) FROM health_records WHERE username = ?" +
                    ") WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                ps.setString(1, currentUsername);
                ps.setDouble(2, weight);
                ps.setDouble(3, bodyFat);
                ps.setDouble(4, waterRate);
                ps.setDouble(5, proteinRate);
                ps.setDouble(6, muscleRate);
                ps.setInt(7, visceralFat);
                ps.setDouble(8, boneMuscle);
                ps.setDouble(9, boneMass);
                ps.setDouble(10, bmr);
                ps.setDouble(11, tdee);
                ps.setDouble(12, bmi);
                ps.setDouble(13, waist);
                ps.setInt(14, bodyAge);
                ps.setString(15, bodyType);
                ps.executeUpdate();

                try (PreparedStatement ps2 = conn.prepareStatement(updateCheckinSql)) {
                    ps2.setString(1, currentUsername);
                    ps2.setString(2, currentUsername);
                    ps2.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 机构批量导入结果 */
        public static class ImportResult {
            public int success = 0;
            public List<String> errors = new ArrayList<>();
        }

        // 机构健康记录写库 SQL (导入 / 手动新建病人 共用, 避免算法漂移)
        // 机构记录: username 为 NULL, patient_id 指向 patients 表 (与个人 users 登录解耦)
        private static final String SQL_INSERT_HEALTH =
                "INSERT INTO health_records (username, patient_id, weight, body_fat, water_rate, protein_rate, " +
                "muscle_rate, visceral_fat, bone_muscle, bone_mass, bmr, tdee, bmi, waist, body_age, body_type, record_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        private static final String SQL_UPDATE_CHECKIN =
                "UPDATE users SET checkin_days = (SELECT COUNT(DISTINCT record_date) FROM health_records WHERE username = ?) WHERE username = ?";

        /**
         * 医疗机构批量导入病人健康记录。
         * - 按 patient_code(机构内病人编号) 查找或自动建立 patients 行
         *   (不创建个人登录账号, 故不存在「未经授权替病人建号」的信任/合规问题)
         * - 写入 health_records (复用与 saveHealthRecord 完全一致的计算路径), patient_id 指向该病人
         * - 导入模板可含 性别/年龄/身高/活动水平, 用于建立病人档案; 缺省则留空
         * @param institutionId 机构 id
         * @param rows 每行: patient_code(String) + record_date(java.util.Date, 可空) + 数值字段
         */
        public static ImportResult importInstitutionRecords(int institutionId, List<Map<String, Object>> rows) {
            ImportResult result = new ImportResult();
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement insPs = conn.prepareStatement(SQL_INSERT_HEALTH);
                     PreparedStatement chkPs = conn.prepareStatement(SQL_UPDATE_CHECKIN)) {
                    for (Map<String, Object> row : rows) {
                        Object cObj = row.get("patient_code");
                        String code = cObj == null ? null : cObj.toString().trim();
                        if (code == null || code.isEmpty()) {
                            result.errors.add("缺病人编号, 已跳过");
                            continue;
                        }
                        double weight = toNum(row.get("weight"));
                        if (weight <= 0) {
                            result.errors.add(code + ": 体重无效, 已跳过");
                            continue;
                        }
                        // 读取行内可能的档案字段 (用于建立/更新病人主表)
                        String gender = row.get("gender") == null ? null : row.get("gender").toString().trim();
                        if (gender != null && gender.isEmpty()) gender = null;
                        int age = (int) toNum(row.get("age"));
                        double height = toNum(row.get("height"));
                        String activity = row.get("activity") == null ? null : row.get("activity").toString().trim();
                        if (activity == null || activity.isEmpty()) activity = "久坐";

                        int patientId = findOrCreatePatient(conn, institutionId, code, null, gender, age, height, activity);
                        if (patientId <= 0) {
                            result.errors.add(code + ": 建病人档案失败, 已跳过");
                            continue;
                        }
                        updatePatientProfileIfPresent(conn, patientId, gender, age, height, activity, weight, toNum(row.get("waist")));

                        // 复用同一套计算与写库逻辑
                        insertHealthRecord(conn, patientId, null, height, age, gender, activity, row, insPs, chkPs);
                        result.success++;
                    }
                    conn.commit();
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    result.errors.add("导入中断: " + e.getMessage());
                    logError("importInstitutionRecords", e);
                }
            } catch (SQLException e) {
                result.errors.add("连接失败: " + e.getMessage());
                logError("importInstitutionRecords", e);
            }
            return result;
        }

        private static double toNum(Object v) {
            return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
        }

        // ---- patients 表辅助方法 ----

        private static Integer findPatientId(Connection conn, int institutionId, String code) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM patients WHERE institution_id = ? AND patient_code = ?")) {
                ps.setInt(1, institutionId);
                ps.setString(2, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("id");
                }
            }
            return null;
        }

        /** 按 (机构, 病人编号) 查找病人, 不存在则新建。返回 patients.id, 失败返回 -1 */
        private static int findOrCreatePatient(Connection conn, int institutionId, String code, String name,
                String gender, int age, double height, String activity) throws SQLException {
            Integer id = findPatientId(conn, institutionId, code);
            if (id != null) return id;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO patients (institution_id, patient_code, name, gender, age, height, activity_level) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, institutionId);
                ps.setString(2, code);
                if (name != null) ps.setString(3, name); else ps.setNull(3, Types.VARCHAR);
                if (gender != null) ps.setString(4, gender); else ps.setNull(4, Types.VARCHAR);
                if (age > 0) ps.setInt(5, age); else ps.setNull(5, Types.INTEGER);
                if (height > 0) ps.setDouble(6, height); else ps.setNull(6, Types.DOUBLE);
                ps.setString(7, activity == null ? "久坐" : activity);
                ps.executeUpdate();
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (gk.next()) return gk.getInt(1);
                }
            }
            return -1;
        }

        /** 用导入行内提供的档案字段(若有)更新病人主表, 缺省字段不覆盖 */
        private static void updatePatientProfileIfPresent(Connection conn, int patientId,
                String gender, int age, double height, String activity, double weight, double waist) throws SQLException {
            if (patientId <= 0) return;
            StringBuilder sb = new StringBuilder("UPDATE patients SET updated_at = NOW()");
            List<Object> vals = new ArrayList<>();
            if (gender != null) { sb.append(", gender = ?"); vals.add(gender); }
            if (age > 0) { sb.append(", age = ?"); vals.add(age); }
            if (height > 0) { sb.append(", height = ?"); vals.add(height); }
            if (activity != null) { sb.append(", activity_level = ?"); vals.add(activity); }
            if (weight > 0) { sb.append(", weight = ?"); vals.add(weight); }
            if (waist > 0) { sb.append(", waist = ?"); vals.add(waist); }
            if (vals.isEmpty()) return;
            sb.append(" WHERE id = ?");
            vals.add(patientId);
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                for (int i = 0; i < vals.size() - 1; i++) ps.setObject(i + 1, vals.get(i));
                ps.setInt(vals.size(), patientId);
                ps.executeUpdate();
            }
        }

        /**
         * 写入一条健康记录 (导入 / 手动新建病人 共用)。
         * 计算 BMI/BMR/TDEE/身体年龄/体质类型, 与 saveHealthRecord 完全一致, 避免算法漂移。
         * 机构记录 username 为 NULL, patient_id 指向 patients; 仅当 username 非空(个人记录)才更新 users.checkin_days。
         */
        private static void insertHealthRecord(Connection conn, int patientId, String username,
                double height, int age, String gender, String activity, Map<String, Object> row,
                PreparedStatement insPs, PreparedStatement chkPs) throws SQLException {
            double weight = toNum(row.get("weight"));
            double bodyFat = toNum(row.get("body_fat"));
            double waterRate = toNum(row.get("water_rate"));
            double proteinRate = toNum(row.get("protein_rate"));
            double muscleRate = toNum(row.get("muscle_rate"));
            int visceralFat = (int) toNum(row.get("visceral_fat"));
            double boneMuscle = toNum(row.get("bone_muscle"));
            double boneMass = toNum(row.get("bone_mass"));
            double waist = toNum(row.get("waist"));
            Object dObj = row.get("record_date");
            java.sql.Date recordDate = (dObj instanceof java.util.Date)
                    ? new java.sql.Date(((java.util.Date) dObj).getTime())
                    : new java.sql.Date(System.currentTimeMillis());

            double bmi = height > 0 ? HealthCalculator.calcBMI(weight, height) : 0;
            double bmr = (height > 0 && age > 0) ? HealthCalculator.calcAvgBMR(weight, height, age, gender) : 0;
            double tdee = bmr > 0 ? HealthCalculator.calcTDEE(bmr, activity) : 0;
            int bodyAge = HealthCalculator.calcBodyAge(age, bodyFat, muscleRate, visceralFat, gender);
            String bodyType = HealthCalculator.classifyBodyType(bmi, bodyFat, gender);

            if (username != null && !username.isEmpty()) insPs.setString(1, username);
            else insPs.setNull(1, Types.VARCHAR);
            if (patientId > 0) insPs.setInt(2, patientId);
            else insPs.setNull(2, Types.INTEGER);
            insPs.setDouble(3, weight);
            insPs.setDouble(4, bodyFat);
            insPs.setDouble(5, waterRate);
            insPs.setDouble(6, proteinRate);
            insPs.setDouble(7, muscleRate);
            insPs.setInt(8, visceralFat);
            insPs.setDouble(9, boneMuscle);
            insPs.setDouble(10, boneMass);
            insPs.setDouble(11, bmr);
            insPs.setDouble(12, tdee);
            if (height > 0) insPs.setDouble(13, bmi); else insPs.setNull(13, Types.DOUBLE);
            insPs.setDouble(14, waist);
            insPs.setInt(15, bodyAge);
            insPs.setString(16, bodyType);
            insPs.setDate(17, recordDate);
            insPs.executeUpdate();

            // 仅有个人登录账号时才更新 users.checkin_days
            if (username != null && !username.isEmpty()) {
                chkPs.setString(1, username);
                chkPs.setString(2, username);
                chkPs.executeUpdate();
            }
        }

        /**
         * 机构手动新建病人: 在 patients 表建立机构维度的病人档案 (不创建个人登录账号),
         * 再写入一条健康记录并归属本机构 (经 patient_id)。
         * @param patientCode 机构内病人编号/病历号 (本机构内唯一)
         * @param metrics 体征指标 map (key 同健康记录列: body_fat/water_rate/...), 可不含 weight/waist(由形参提供)
         * @return 是否成功 (false 多为编号已存在或数据库异常)
         */
        public static boolean addInstitutionPatient(int institutionId, String patientCode, String gender, int age,
                double height, String activity, double weight, Double waist, Map<String, Object> metrics) {
            patientCode = patientCode == null ? "" : patientCode.trim();
            if (patientCode.isEmpty() || weight <= 0) return false;
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                // 1. 建立病人档案 (机构维度, 无登录账号) — 编号唯一性校验
                if (findPatientId(conn, institutionId, patientCode) != null) return false;
                int patientId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO patients (institution_id, patient_code, gender, age, height, weight, waist, activity_level) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, institutionId);
                    ps.setString(2, patientCode);
                    if (gender != null) ps.setString(3, gender); else ps.setNull(3, Types.VARCHAR);
                    if (age > 0) ps.setInt(4, age); else ps.setNull(4, Types.INTEGER);
                    if (height > 0) ps.setDouble(5, height); else ps.setNull(5, Types.DOUBLE);
                    if (weight > 0) ps.setDouble(6, weight); else ps.setNull(6, Types.DOUBLE);
                    if (waist != null && waist > 0) ps.setDouble(7, waist); else ps.setNull(7, Types.DOUBLE);
                    ps.setString(8, activity == null ? "久坐" : activity);
                    ps.executeUpdate();
                    try (ResultSet gk = ps.getGeneratedKeys()) {
                        if (gk.next()) patientId = gk.getInt(1);
                        else return false;
                    }
                }
                // 2. 写入健康记录 (复用同一套计算)
                Map<String, Object> row = new HashMap<>(metrics == null ? Collections.emptyMap() : metrics);
                row.put("weight", weight);
                if (waist != null) row.put("waist", waist);
                if (!row.containsKey("record_date")) row.put("record_date", new java.util.Date());
                try (PreparedStatement insPs = conn.prepareStatement(SQL_INSERT_HEALTH);
                     PreparedStatement chkPs = conn.prepareStatement(SQL_UPDATE_CHECKIN)) {
                    insertHealthRecord(conn, patientId, null, height, age, gender, activity, row, insPs, chkPs);
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                logError("addInstitutionPatient", e);
                return false;
            }
        }

        /** 批量移出本机构名单 (软删除: archived=TRUE, 保留病人档案与全部健康记录) */
        public static int removeInstitutionPatients(int institutionId, List<String> patientCodes) {
            if (patientCodes == null || patientCodes.isEmpty()) return 0;
            StringBuilder ph = new StringBuilder();
            for (int i = 0; i < patientCodes.size(); i++) ph.append(i == 0 ? "?" : ",?");
            String sql = "UPDATE patients SET archived = TRUE, updated_at = NOW() " +
                    "WHERE institution_id = ? AND patient_code IN (" + ph + ")";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, institutionId);
                for (int i = 0; i < patientCodes.size(); i++) ps.setString(2 + i, patientCodes.get(i));
                int n = ps.executeUpdate();
                logAction("INSTITUTION", DBUtil.currentInstitutionName, "批量移出病人", "移出 " + n + " 人");
                return n;
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        }

        /** 获取最新健康记录 (当前登录用户) */
        public static Map<String, Object> getLatestHealthRecord() {
            return getLatestHealthRecord(currentUsername);
        }

        /** 获取指定用户的最新健康记录 (机构端查看病人用) */
        public static Map<String, Object> getLatestHealthRecord(String username) {
            String sql = "SELECT * FROM health_records WHERE username = ? ORDER BY record_date DESC, id DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("water_rate", rs.getDouble("water_rate"));
                    map.put("muscle_rate", rs.getDouble("muscle_rate"));
                    map.put("visceral_fat", rs.getInt("visceral_fat"));
                    map.put("bone_muscle", rs.getDouble("bone_muscle"));
                    map.put("bone_mass", rs.getDouble("bone_mass"));
                    map.put("protein_rate", rs.getDouble("protein_rate"));
                    map.put("bmr", rs.getDouble("bmr"));
                    map.put("tdee", rs.getDouble("tdee"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("waist", rs.getDouble("waist"));
                    map.put("body_age", rs.getInt("body_age"));
                    try {
                        map.put("body_type", rs.getString("body_type"));
                    } catch (SQLException ex) {
                        map.put("body_type", "--");
                    }
                    map.put("record_date", rs.getDate("record_date"));
                    return map;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        /** 获取历史健康记录列表 */
        public static List<Map<String, Object>> getHealthRecords(int limit) {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT * FROM health_records WHERE username = ? ORDER BY record_date DESC, id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("record_date", rs.getDate("record_date"));
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("water_rate", rs.getDouble("water_rate"));
                    map.put("muscle_rate", rs.getDouble("muscle_rate"));
                    map.put("visceral_fat", rs.getInt("visceral_fat"));
                    map.put("bone_muscle", rs.getDouble("bone_muscle"));
                    map.put("bone_mass", rs.getDouble("bone_mass"));
                    map.put("protein_rate", rs.getDouble("protein_rate"));
                    map.put("bmr", rs.getDouble("bmr"));
                    map.put("tdee", rs.getDouble("tdee"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("waist", rs.getDouble("waist"));
                    map.put("body_age", rs.getInt("body_age"));
                    try {
                        map.put("body_type", rs.getString("body_type"));
                    } catch (SQLException ex) {
                        map.put("body_type", "--");
                    }
                    list.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /**
         * 获取指定用户的历史健康记录 (按时间正序, 用于趋势图)。与 getHealthRecords(int) 不同,
         * 此方法接受任意用户名(供医疗机构端查看其归属病人), 不依赖 currentUsername。
         */
        public static List<Map<String, Object>> getHealthRecords(String username, int limit) {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT * FROM health_records WHERE username = ? ORDER BY record_date ASC, id ASC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("record_date", rs.getDate("record_date"));
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("water_rate", rs.getDouble("water_rate"));
                    map.put("muscle_rate", rs.getDouble("muscle_rate"));
                    map.put("visceral_fat", rs.getInt("visceral_fat"));
                    map.put("bone_muscle", rs.getDouble("bone_muscle"));
                    map.put("bone_mass", rs.getDouble("bone_mass"));
                    map.put("protein_rate", rs.getDouble("protein_rate"));
                    map.put("bmr", rs.getDouble("bmr"));
                    map.put("tdee", rs.getDouble("tdee"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("waist", rs.getDouble("waist"));
                    map.put("body_age", rs.getInt("body_age"));
                    try {
                        map.put("body_type", rs.getString("body_type"));
                    } catch (SQLException ex) {
                        map.put("body_type", "--");
                    }
                    list.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取指定病人的最新健康记录 (机构端查看病人用, 经 patient_id) */
        public static Map<String, Object> getLatestHealthRecord(int patientId) {
            String sql = "SELECT * FROM health_records WHERE patient_id = ? ORDER BY record_date DESC, id DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, patientId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("water_rate", rs.getDouble("water_rate"));
                    map.put("muscle_rate", rs.getDouble("muscle_rate"));
                    map.put("visceral_fat", rs.getInt("visceral_fat"));
                    map.put("bone_muscle", rs.getDouble("bone_muscle"));
                    map.put("bone_mass", rs.getDouble("bone_mass"));
                    map.put("protein_rate", rs.getDouble("protein_rate"));
                    map.put("bmr", rs.getDouble("bmr"));
                    map.put("tdee", rs.getDouble("tdee"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("waist", rs.getDouble("waist"));
                    map.put("body_age", rs.getInt("body_age"));
                    try {
                        map.put("body_type", rs.getString("body_type"));
                    } catch (SQLException ex) {
                        map.put("body_type", "--");
                    }
                    map.put("record_date", rs.getDate("record_date"));
                    return map;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * 获取指定病人的历史健康记录 (按时间正序, 用于趋势图)。
         */
        public static List<Map<String, Object>> getHealthRecords(int patientId, int limit) {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT * FROM health_records WHERE patient_id = ? ORDER BY record_date ASC, id ASC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, patientId);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("record_date", rs.getDate("record_date"));
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("water_rate", rs.getDouble("water_rate"));
                    map.put("muscle_rate", rs.getDouble("muscle_rate"));
                    map.put("visceral_fat", rs.getInt("visceral_fat"));
                    map.put("bone_muscle", rs.getDouble("bone_muscle"));
                    map.put("bone_mass", rs.getDouble("bone_mass"));
                    map.put("protein_rate", rs.getDouble("protein_rate"));
                    map.put("bmr", rs.getDouble("bmr"));
                    map.put("tdee", rs.getDouble("tdee"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("waist", rs.getDouble("waist"));
                    map.put("body_age", rs.getInt("body_age"));
                    try {
                        map.put("body_type", rs.getString("body_type"));
                    } catch (SQLException ex) {
                        map.put("body_type", "--");
                    }
                    list.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 检查今天是否已打卡（有健康记录） */
        public static boolean isCheckedToday() {
            String sql = "SELECT 1 FROM health_records WHERE username = ? AND record_date = CURRENT_DATE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                return ps.executeQuery().next();
            } catch (SQLException e) {
                return false;
            }
        }

        // ==================== 运动记录操作 ====================

        /** 保存运动记录 */
        public static boolean saveExerciseRecord(String type, int duration, String intensity, int calories) {
            String sql = "INSERT INTO exercise_records (username, exercise_type, duration, intensity, calories_burned) " +
                         "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setString(2, type);
                ps.setInt(3, duration);
                ps.setString(4, intensity);
                ps.setInt(5, calories);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取今日运动消耗总热量 */
        public static int getTodayExerciseCalories() {
            String sql = "SELECT COALESCE(SUM(calories_burned), 0) FROM exercise_records " +
                         "WHERE username = ? AND record_date = CURRENT_DATE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }

        /** 获取今日运动记录列表 */
        public static List<String[]> getTodayExerciseList() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT exercise_type, duration, intensity, calories_burned FROM exercise_records " +
                         "WHERE username = ? AND record_date = CURRENT_DATE ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                        rs.getString("exercise_type"),
                        rs.getInt("duration") + "分钟",
                        rs.getString("intensity"),
                        rs.getInt("calories_burned") + "kcal"
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取当前用户全部运动记录（含日期） */
        public static List<String[]> getExerciseRecordsByUser(String username, int limit) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT record_date, exercise_type, duration, intensity, calories_burned FROM exercise_records " +
                         "WHERE username = ? ORDER BY record_date DESC, id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                while (rs.next()) {
                    list.add(new String[]{
                        sdf.format(rs.getDate("record_date")),
                        rs.getString("exercise_type"),
                        rs.getInt("duration") + "分钟",
                        rs.getString("intensity"),
                        rs.getInt("calories_burned") + "kcal"
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        // ==================== 饮食记录操作 ====================

        /** 保存饮食记录 */
        public static boolean saveDietRecord(String mealType, String foodName, int calories,
                                       double protein, double carbs, double fat) {
            String sql = "INSERT INTO diet_records (username, meal_type, food_name, calories, protein, carbs, fat) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setString(2, mealType);
                ps.setString(3, foodName);
                ps.setInt(4, calories);
                ps.setDouble(5, protein);
                ps.setDouble(6, carbs);
                ps.setDouble(7, fat);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取今日饮食汇总 */
        public static int[] getTodayDietSummary() {
            // 返回 [总热量, 蛋白质×100, 碳水×100, 脂肪×100] (乘100保留精度)
            String sql = "SELECT COALESCE(SUM(calories),0), COALESCE(SUM(protein),0), " +
                         "COALESCE(SUM(carbs),0), COALESCE(SUM(fat),0) FROM diet_records " +
                         "WHERE username = ? AND record_date = CURRENT_DATE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new int[]{ rs.getInt(1), (int)(rs.getDouble(2)*100),
                            (int)(rs.getDouble(3)*100), (int)(rs.getDouble(4)*100) };
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new int[]{0, 0, 0, 0};
        }

        /** 获取今日饮食记录明细 */
        public static List<String[]> getTodayDietRecords() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT meal_type, food_name, calories, protein, carbs, fat " +
                         "FROM diet_records WHERE username = ? AND record_date = CURRENT_DATE " +
                         "ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString("meal_type"),
                            rs.getString("food_name"),
                            String.valueOf(rs.getInt("calories")),
                            df2.format(rs.getDouble("protein")),
                            df2.format(rs.getDouble("carbs")),
                            df2.format(rs.getDouble("fat"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        // ==================== 饮水记录相关操作 ====================

        /** 饮水“太少”判定阈值比例（今日总量 < 目标 × 该比例 即提醒） */
        public static final double WATER_LOW_RATIO = 0.5;

        private static boolean waterTablesReady = false;

        /** 自愈合建表：首次调用时创建 water_records / water_goals（不会破坏已有数据） */
        public static void ensureWaterTables() {
            if (waterTablesReady) return;
            String sql1 = "CREATE TABLE IF NOT EXISTS water_records (" +
                    "  id SERIAL PRIMARY KEY," +
                    "  username VARCHAR(50) REFERENCES users(username)," +
                    "  record_date DATE DEFAULT CURRENT_DATE," +
                    "  amount_ml INT CHECK (amount_ml > 0 AND amount_ml <= 3000)," +
                    "  note VARCHAR(100)," +
                    "  created_at TIMESTAMP DEFAULT NOW()" +
                    ")";
            String sql2 = "CREATE TABLE IF NOT EXISTS water_goals (" +
                    "  username VARCHAR(50) PRIMARY KEY REFERENCES users(username)," +
                    "  goal_ml INT CHECK (goal_ml >= 500 AND goal_ml <= 6000)" +
                    ")";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql1);
                stmt.executeUpdate(sql2);
                try { stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_water_username_date ON water_records(username, record_date)"); } catch (SQLException ignore) {}
                waterTablesReady = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /** 记录一次饮水 */
        public static boolean saveWaterRecord(int amountMl, String note) {
            ensureWaterTables();
            if (amountMl <= 0 || amountMl > 3000) return false;
            String sql = "INSERT INTO water_records (username, amount_ml, note) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setInt(2, amountMl);
                ps.setString(3, (note == null || note.trim().isEmpty()) ? null : note.trim());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 今日饮水总量(ml) */
        public static int getTodayWaterTotal() {
            ensureWaterTables();
            String sql = "SELECT COALESCE(SUM(amount_ml),0) FROM water_records " +
                    "WHERE username = ? AND record_date = CURRENT_DATE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }

        /** 今日饮水记录明细: {时间, 水量ml, 备注} 按时间倒序 */
        public static List<String[]> getTodayWaterRecords() {
            ensureWaterTables();
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT TO_CHAR(created_at, 'HH24:MI'), amount_ml, note " +
                    "FROM water_records WHERE username = ? AND record_date = CURRENT_DATE " +
                    "ORDER BY created_at DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString(1),
                            String.valueOf(rs.getInt(2)),
                            rs.getString(3) == null ? "" : rs.getString(3)
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 每日饮水目标(ml): 优先用户自定义设定, 否则按体重估算, 再否则默认 2000 */
        public static int getDailyWaterGoal() {
            ensureWaterTables();
            String sql = "SELECT goal_ml FROM water_goals WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("goal_ml");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return HealthCalculator.calcDailyWaterGoal(currentWeight);
        }

        /** 保存用户自定义每日饮水目标(ml) */
        public static boolean saveWaterGoal(int goalMl) {
            ensureWaterTables();
            if (goalMl < 500 || goalMl > 6000) return false;
            String sql = "INSERT INTO water_goals (username, goal_ml) VALUES (?, ?) " +
                    "ON CONFLICT (username) DO UPDATE SET goal_ml = EXCLUDED.goal_ml";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setInt(2, goalMl);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 今日摄水量是否“太少”（低于目标 × WATER_LOW_RATIO） */
        public static boolean isWaterIntakeLow() {
            int goal = getDailyWaterGoal();
            if (goal <= 0) return false;
            return getTodayWaterTotal() < goal * WATER_LOW_RATIO;
        }

        /** 今日是否已有“喝水提醒”通知（按天去重，避免消息中心重复刷屏） */
        public static boolean hasWaterReminderToday() {
            String sql = "SELECT COUNT(*) FROM notifications WHERE receiver = ? AND title = '喝水提醒' " +
                    "AND created_at::date = CURRENT_DATE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1) > 0;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        /** 获取食物列表 */
        public static List<String[]> getAllFoods() {
            ensureFoodColumns();
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT food_name, calories_per_100g, protein, carbs, fat, default_grams FROM foods WHERE COALESCE(status,'已发布')<>'待确认' ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                        rs.getString("food_name"),
                        rs.getInt("calories_per_100g") + "",
                        df2.format(rs.getDouble("protein")),
                        df2.format(rs.getDouble("carbs")),
                        df2.format(rs.getDouble("fat")),
                        String.valueOf(rs.getInt("default_grams"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        // ==================== 目标操作 ====================

        /**
         * 保存或更新目标
         * @return null 表示成功, 否则返回失败原因 (含 SQLException 信息, 便于界面排查)
         */
        public static String saveGoal(String goalType, double targetValue) {
            // 先检查是否已有目标
            String checkSql = "SELECT id FROM goals WHERE username = ?";
            String updateSql = "UPDATE goals SET goal_type = ?, target_value = ?, start_date = CURRENT_DATE, " +
                               "end_date = NULL, current_stage = 1 WHERE username = ?";
            String insertSql = "INSERT INTO goals (username, goal_type, target_value, start_date) VALUES (?, ?, ?, CURRENT_DATE)";
            try (Connection conn = getConnection()) {
                PreparedStatement checkPs = conn.prepareStatement(checkSql);
                checkPs.setString(1, currentUsername);
                if (checkPs.executeQuery().next()) {
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, goalType);
                        ps.setDouble(2, targetValue);
                        ps.setString(3, currentUsername);
                        if (ps.executeUpdate() <= 0) return "更新目标失败: 未影响任何行";
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, currentUsername);
                        ps.setString(2, goalType);
                        ps.setDouble(3, targetValue);
                        ps.executeUpdate();
                    }
                }
                return null;
            } catch (SQLException e) {
                logError("saveGoal(username=" + currentUsername + ", goalType=" + goalType
                        + ", target=" + targetValue + ")", e);
                return "保存目标失败: " + e.getMessage()
                        + "\n(完整错误已写入 " + ERROR_LOG + ", 可打开复制给我们排查)";
            }
        }

        /** 更新目标当前阶段 */
        public static boolean updateGoalStage(int stage) {
            String sql = "UPDATE goals SET current_stage = ? WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, stage);
                ps.setString(2, currentUsername);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取指定日期范围内的运动统计 [次数, 总分钟] */
        public static int[] getExerciseStatsBetween(Date start, Date end) {
            int[] result = {0, 0};
            String sql = "SELECT COUNT(*), COALESCE(SUM(duration), 0) FROM exercise_records " +
                         "WHERE username = ? AND record_date >= ? AND record_date < ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setDate(2, new java.sql.Date(start.getTime()));
                ps.setDate(3, new java.sql.Date(end.getTime()));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    result[0] = rs.getInt(1);
                    result[1] = rs.getInt(2);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return result;
        }

        /** 获取目标 */
        public static Map<String, Object> getGoal() {
            String sql = "SELECT * FROM goals WHERE username = ? ORDER BY id DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("goal_type", rs.getString("goal_type"));
                    map.put("target_value", rs.getDouble("target_value"));
                    map.put("start_date", rs.getDate("start_date"));
                    map.put("end_date", rs.getDate("end_date"));
                    map.put("current_stage", rs.getInt("current_stage"));
                    return map;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        // ==================== 成就操作 ====================

        /** 检查并授予成就徽章 */
        public static void checkAndGrantAchievements() {
            try (Connection conn = getConnection()) {
                // 连续打卡天数
                int streak = getCheckInStreak(conn);
                if (streak >= 7) grantBadge(conn, "毅力之星");
                if (streak >= 30) grantBadge(conn, "坚持达人");

                // 饮食记录天数
                int dietDays = getDietDays(conn);
                if (dietDays >= 30) grantBadge(conn, "美食家");

                // 运动记录次数
                int exerciseCount = getExerciseCount(conn);
                if (exerciseCount >= 20) grantBadge(conn, "运动健将");

                // 累计记录饮水天数
                int waterDays = getWaterDays(conn);
                if (waterDays >= 7) grantBadge(conn, "补水达人");

                // 健康评分
                Map<String, Object> latest = getLatestHealthRecord();
                if (latest != null) {
                    int score = HealthCalculator.calcHealthScore(
                        (double)latest.get("bmi"), (double)latest.get("body_fat"),
                        (int)latest.get("visceral_fat"), (double)latest.get("muscle_rate"),
                        (double)latest.get("water_rate"), currentGender);
                    if (score >= 90) grantBadge(conn, "健康标兵");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private static int getCheckInStreak(Connection conn) throws SQLException {
            String sql = "SELECT COUNT(DISTINCT record_date) FROM health_records " +
                         "WHERE username = ? AND record_date >= CURRENT_DATE - INTERVAL '30 days'";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, currentUsername);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }

        private static int getDietDays(Connection conn) throws SQLException {
            String sql = "SELECT COUNT(DISTINCT record_date) FROM diet_records WHERE username = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, currentUsername);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }

        private static int getExerciseCount(Connection conn) throws SQLException {
            String sql = "SELECT COUNT(*) FROM exercise_records WHERE username = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, currentUsername);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }

        private static int getWaterDays(Connection conn) throws SQLException {
            String sql = "SELECT COUNT(DISTINCT record_date) FROM water_records WHERE username = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, currentUsername);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }

        /** 授予徽章（不存在才插入） */
        private static void grantBadge(Connection conn, String badgeName) throws SQLException {
            String checkSql = "SELECT 1 FROM achievements WHERE username = ? AND badge_name = ?";
            PreparedStatement checkPs = conn.prepareStatement(checkSql);
            checkPs.setString(1, currentUsername);
            checkPs.setString(2, badgeName);
            if (!checkPs.executeQuery().next()) {
                String insertSql = "INSERT INTO achievements (username, badge_name) VALUES (?, ?)";
                PreparedStatement insertPs = conn.prepareStatement(insertSql);
                insertPs.setString(1, currentUsername);
                insertPs.setString(2, badgeName);
                insertPs.executeUpdate();
            }
        }

        /** 获取已获得徽章列表 */
        public static List<String[]> getAchievements() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT badge_name, achieved_date FROM achievements WHERE username = ? ORDER BY achieved_date DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{ rs.getString("badge_name"), rs.getDate("achieved_date").toString() });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 各徽章当前进度 {current, target}；布尔型徽章 target=1 */
        public static Map<String, int[]> getBadgeProgress() {
            Map<String, int[]> map = new HashMap<>();
            try (Connection conn = getConnection()) {
                int streak = getCheckInStreak(conn);
                int dietDays = getDietDays(conn);
                int exerciseCount = getExerciseCount(conn);
                int waterDays = getWaterDays(conn);
                map.put("毅力之星", new int[]{streak, 7});
                map.put("坚持达人", new int[]{streak, 30});
                map.put("美食家", new int[]{dietDays, 30});
                map.put("运动健将", new int[]{exerciseCount, 20});
                map.put("补水达人", new int[]{waterDays, 7});

                Map<String, Object> latest = getLatestHealthRecord();
                if (latest != null) {
                    int score = HealthCalculator.calcHealthScore(
                        (double) latest.get("bmi"), (double) latest.get("body_fat"),
                        (int) latest.get("visceral_fat"), (double) latest.get("muscle_rate"),
                        (double) latest.get("water_rate"), currentGender);
                    map.put("健康标兵", new int[]{score, 90});
                    double bodyFat = (double) latest.get("body_fat");
                    map.put("蜕变之星", new int[]{isBodyFatNormal(bodyFat, currentGender) ? 1 : 0, 1});
                } else {
                    map.put("健康标兵", new int[]{0, 90});
                    map.put("蜕变之星", new int[]{0, 1});
                }

                Map<String, Object> goal = getGoal();
                boolean reached = goal != null && (int) goal.getOrDefault("current_stage", 0) >= 1;
                map.put("目标达成者", new int[]{reached ? 1 : 0, 1});
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        private static boolean isBodyFatNormal(double bodyFat, String gender) {
            if ("女".equals(gender)) return bodyFat >= 16 && bodyFat <= 28;
            return bodyFat >= 6 && bodyFat <= 20;
        }

        // ==================== AI 报告操作 ====================

        /** 保存 AI 报告 */
        public static boolean saveAIReport(String reportType, String content) {
            String sql = "INSERT INTO ai_reports (username, report_type, report_content) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ps.setString(2, reportType);
                ps.setString(3, content);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取历史 AI 报告列表 */
        public static List<String[]> getAIReports() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, report_type, generated_at FROM ai_reports WHERE username = ? ORDER BY generated_at DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, currentUsername);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("report_type"),
                        sdf.format(rs.getTimestamp("generated_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取 AI 报告内容 */
        public static String getAIReportContent(int reportId) {
            String sql = "SELECT report_content FROM ai_reports WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, reportId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getString("report_content");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return "";
        }

        // ==================== 管理员操作 ====================

        /** 管理员登录验证 */
        public static boolean loginAdmin(String username, String password) {
            String sql = "SELECT password, salt, role, status FROM admins WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String hash = rs.getString("password");
                    String salt = rs.getString("salt");
                    String status = rs.getString("status");
                    if (!"启用".equals(status)) return false;
                    if (PasswordUtil.verify(password, salt, hash)) {
                        currentUsername = username;
                        return true;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        /** 初始化默认管理员账号 */
        public static void initDefaultAdmin() {
            String sql = "INSERT INTO admins (username, password, salt, role, status) " +
                         "SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM admins)";
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash("admin123", salt);
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "admin");
                ps.setString(2, hash);
                ps.setString(3, salt);
                ps.setString(4, "super_admin");
                ps.setString(5, "启用");
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // ==================== 医疗机构登录 / 注册 ====================

        /** 机构注册 (org_code 唯一) */
        /** 机构登录验证 */
        public static boolean loginInstitution(String orgCode, String password) {
            if (orgCode == null || orgCode.trim().isEmpty() || password == null || password.isEmpty()) return false;
            String sql = "SELECT id, org_name, password, salt FROM institutions WHERE org_code = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, orgCode.trim());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String hash = rs.getString("password");
                    String salt = rs.getString("salt");
                    if (hash == null || hash.isEmpty()) return false;
                    if (PasswordUtil.verify(password, salt, hash)) {
                        currentInstitutionId = rs.getInt("id");
                        currentInstitutionName = rs.getString("org_name");
                        return true;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        // ==================== 机构入驻申请 / 审批 ====================

        /** 机构提交入驻申请 (状态 pending, 含机构自设密码的哈希; 不写入 institutions 表) */
        public static boolean submitInstitutionRequest(String orgName, String contact, String phone, String note, String password) {
            if (orgName == null || orgName.trim().isEmpty()) return false;
            if (password == null || password.isEmpty()) return false;
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash(password, salt);
            String sql = "INSERT INTO institution_requests (org_name, contact, phone, note, password, salt, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'pending')";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, orgName.trim());
                ps.setString(2, contact == null ? "" : contact.trim());
                ps.setString(3, phone == null ? "" : phone.trim());
                ps.setString(4, note == null ? "" : note.trim());
                ps.setString(5, hash);
                ps.setString(6, salt);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取入驻申请列表; status 为 null 时返回全部 */
        public static List<Map<String, Object>> getInstitutionRequests(String status) {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT * FROM institution_requests" + (status == null ? "" : " WHERE status = ?")
                    + " ORDER BY created_at DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (status != null) ps.setString(1, status);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("org_name", rs.getString("org_name"));
                    m.put("contact", rs.getString("contact"));
                    m.put("phone", rs.getString("phone"));
                    m.put("note", rs.getString("note"));
                    m.put("status", rs.getString("status"));
                    m.put("review_note", rs.getString("review_note"));
                    m.put("created_at", rs.getTimestamp("created_at"));
                    list.add(m);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 审批通过: 用申请中机构自设的密码哈希建机构, 仅返回生成的 org_code (密码不传递) */
        public static String approveInstitutionRequest(int id, String reviewer) {
            // 1. 读取申请 (含机构自设的密码哈希与盐)
            String orgName = null, contact = "", password = null, salt = null;
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT org_name, contact, password, salt FROM institution_requests WHERE id = ? AND status = 'pending'")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    orgName = rs.getString("org_name");
                    contact = rs.getString("contact");
                    password = rs.getString("password");
                    salt = rs.getString("salt");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            if (orgName == null || password == null || password.isEmpty()) return null;

            // 2. 生成唯一机构编码
            String orgCode = generateUniqueOrgCode();

            Connection conn = null;
            try {
                conn = getConnection();
                conn.setAutoCommit(false);
                // 写入机构 (复用申请时机构自设的密码哈希, 不重新生成)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO institutions (org_name, org_code, password, salt, contact) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, orgName);
                    ps.setString(2, orgCode);
                    ps.setString(3, password);
                    ps.setString(4, salt);
                    ps.setString(5, contact);
                    ps.executeUpdate();
                }
                // 更新申请状态
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE institution_requests SET status='approved', reviewer=?, reviewed_at=NOW() WHERE id=?")) {
                    ps.setString(1, reviewer);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                }
                conn.commit();
                logAction("INSTITUTION", reviewer, "审批通过机构入驻", orgName + " -> " + orgCode);
                return orgCode;
            } catch (SQLException e) {
                e.printStackTrace();
                if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
                return null;
            } finally {
                if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
            }
        }

        /** 拒绝入驻申请 */
        public static boolean rejectInstitutionRequest(int id, String reviewer, String note) {
            String sql = "UPDATE institution_requests SET status='rejected', reviewer=?, review_note=?, reviewed_at=NOW() WHERE id=? AND status='pending'";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reviewer);
                ps.setString(2, note == null ? "" : note.trim());
                ps.setInt(3, id);
                int n = ps.executeUpdate();
                if (n > 0) logAction("INSTITUTION", reviewer, "拒绝机构入驻", "request#" + id);
                return n > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 生成唯一机构编码 (ORG- + 4 位数字, 库内唯一) */
        private static String generateUniqueOrgCode() {
            java.security.SecureRandom rnd = new java.security.SecureRandom();
            String code;
            for (int attempt = 0; attempt < 20; attempt++) {
                code = "ORG-" + (1000 + rnd.nextInt(9000));
                if (!orgCodeExists(code)) return code;
            }
            return "ORG-" + System.currentTimeMillis() % 10000;
        }

        private static boolean orgCodeExists(String code) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM institutions WHERE org_code = ?")) {
                ps.setString(1, code);
                return ps.executeQuery().next();
            } catch (SQLException e) { e.printStackTrace(); }
            return false;
        }

        /** 获取某机构的病人列表 (含最新一次健康记录摘要, 不含已移出的) */
        public static List<Map<String, Object>> getInstitutionPatients(int institutionId) {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT p.id, p.patient_code, p.name, p.gender, p.age, p.height, " +
                    "(SELECT weight FROM health_records hr WHERE hr.patient_id = p.id ORDER BY record_date DESC, id DESC LIMIT 1) AS weight, " +
                    "(SELECT bmi FROM health_records hr WHERE hr.patient_id = p.id ORDER BY record_date DESC, id DESC LIMIT 1) AS bmi, " +
                    "(SELECT body_type FROM health_records hr WHERE hr.patient_id = p.id ORDER BY record_date DESC, id DESC LIMIT 1) AS body_type, " +
                    "(SELECT record_date FROM health_records hr WHERE hr.patient_id = p.id ORDER BY record_date DESC, id DESC LIMIT 1) AS last_date " +
                    "FROM patients p WHERE p.institution_id = ? AND p.archived = FALSE ORDER BY p.patient_code";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, institutionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("patient_code", rs.getString("patient_code"));
                    m.put("name", rs.getString("name"));
                    m.put("gender", rs.getString("gender"));
                    m.put("age", rs.getObject("age") == null ? 0 : rs.getInt("age"));
                    m.put("height", rs.getDouble("height"));
                    m.put("weight", rs.getDouble("weight"));
                    m.put("bmi", rs.getDouble("bmi"));
                    m.put("body_type", rs.getString("body_type"));
                    m.put("last_date", rs.getDate("last_date"));
                    list.add(m);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取所有用户（管理员） */
        public static List<Map<String, Object>> getAllUsers() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT id, username, gender, age, height, activity_level, account_status, " +
                         "created_at, last_login, deleted, " +
                         "(SELECT COUNT(DISTINCT record_date) FROM health_records WHERE username = u.username) AS checkin_days " +
                         "FROM users u ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("username", rs.getString("username"));
                    map.put("gender", rs.getString("gender"));
                    map.put("age", rs.getInt("age"));
                    map.put("height", rs.getDouble("height"));
                    map.put("activity_level", rs.getString("activity_level"));
                    map.put("account_status", rs.getString("account_status"));
                    map.put("created_at", rs.getTimestamp("created_at"));
                    map.put("last_login", rs.getTimestamp("last_login"));
                    map.put("checkin_days", rs.getInt("checkin_days"));
                    map.put("deleted", rs.getBoolean("deleted"));
                    list.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 更新用户账号状态 */
        public static boolean updateUserStatus(int userId, String status) {
            String sql = "UPDATE users SET account_status = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setInt(2, userId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 软删除用户 */
        public static boolean softDeleteUser(int userId) {
            String sql = "UPDATE users SET deleted = TRUE, account_status = '冻结' WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 硬删除用户 */
        public static boolean hardDeleteUser(int userId) {
            String sql = "DELETE FROM users WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取用户详细健康档案 */
        public static Map<String, Object> getUserHealthProfile(String username) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("username", username);

            // 最新健康记录
            String sql = "SELECT * FROM health_records WHERE username = ? ORDER BY record_date DESC, id DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> latest = new HashMap<>();
                    latest.put("weight", rs.getDouble("weight"));
                    latest.put("body_fat", rs.getDouble("body_fat"));
                    latest.put("bmi", rs.getDouble("bmi"));
                    latest.put("waist", rs.getDouble("waist"));
                    latest.put("record_date", rs.getDate("record_date"));
                    profile.put("latest_record", latest);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // 记录总数
            profile.put("record_count", countByUser("health_records", username));
            profile.put("diet_count", countByUser("diet_records", username));
            profile.put("exercise_count", countByUser("exercise_records", username));
            profile.put("achievement_count", countByUser("achievements", username));

            // 过敏源 / 慢性病 (个人健康画像, 来自 users 表)
            String sql2 = "SELECT allergies, chronic_diseases FROM users WHERE username = ?";
            try (Connection conn2 = getConnection();
                 PreparedStatement ps2 = conn2.prepareStatement(sql2)) {
                ps2.setString(1, username);
                ResultSet rs2 = ps2.executeQuery();
                if (rs2.next()) {
                    profile.put("allergies", rs2.getString("allergies"));
                    profile.put("chronic_diseases", rs2.getString("chronic_diseases"));
                }
            } catch (SQLException e) { e.printStackTrace(); }

            return profile;
        }

        private static int countByUser(String table, String username) {
            String sql = "SELECT COUNT(*) FROM " + table + " WHERE username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }

        /** 获取全局统计数据 */
        public static Map<String, Object> getGlobalStats() {
            Map<String, Object> stats = new HashMap<>();
            try (Connection conn = getConnection()) {
                stats.put("total_users", countQuery(conn, "SELECT COUNT(*) FROM users WHERE deleted = FALSE"));
                stats.put("active_users_7d", countQuery(conn, "SELECT COUNT(DISTINCT username) FROM health_records WHERE record_date >= CURRENT_DATE - INTERVAL '7 days'"));
                stats.put("today_checkin", countQuery(conn, "SELECT COUNT(DISTINCT username) FROM health_records WHERE record_date = CURRENT_DATE"));
                stats.put("avg_bmi", avgQuery(conn, "SELECT AVG(bmi) FROM health_records"));
                stats.put("avg_body_fat", avgQuery(conn, "SELECT AVG(body_fat) FROM health_records"));
                stats.put("abnormal_users", getAbnormalUsers().size());
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return stats;
        }

        /** 所有注册并激活的用户列表 */
        public static List<Map<String, Object>> getTotalUsersList() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT username, gender, age, height, activity_level, created_at FROM users WHERE deleted = FALSE ORDER BY username";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", rs.getString("username"));
                    map.put("gender", rs.getString("gender"));
                    map.put("age", rs.getInt("age"));
                    map.put("height", rs.getDouble("height"));
                    map.put("activity_level", rs.getString("activity_level"));
                    map.put("created_at", rs.getTimestamp("created_at"));
                    list.add(map);
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }

        /** 最近 7 天活跃（登录或打卡）用户列表 */
        public static List<Map<String, Object>> getActiveUsers7dList() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "WITH latest AS (" +
                    "SELECT username, MAX(record_date) AS last_date, COUNT(*) AS record_count " +
                    "FROM health_records WHERE record_date >= CURRENT_DATE - INTERVAL '7 days' " +
                    "GROUP BY username) " +
                    "SELECT u.username, u.age, u.gender, l.last_date, l.record_count " +
                    "FROM users u JOIN latest l ON u.username = l.username " +
                    "ORDER BY l.last_date DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", rs.getString("username"));
                    map.put("age", rs.getInt("age"));
                    map.put("gender", rs.getString("gender"));
                    map.put("last_date", rs.getDate("last_date"));
                    map.put("record_count", rs.getInt("record_count"));
                    list.add(map);
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }

        /** 今日打卡用户列表 */
        public static List<Map<String, Object>> getTodayCheckinUsersList() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "WITH today AS (" +
                    "SELECT username, MAX(record_date) AS record_date, AVG(bmi) AS bmi, " +
                    "COUNT(*) AS record_count FROM health_records WHERE record_date = CURRENT_DATE GROUP BY username) " +
                    "SELECT u.username, u.age, u.gender, t.record_date, t.record_count, t.bmi " +
                    "FROM users u JOIN today t ON u.username = t.username " +
                    "ORDER BY t.record_date DESC, u.username";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", rs.getString("username"));
                    map.put("age", rs.getInt("age"));
                    map.put("gender", rs.getString("gender"));
                    map.put("record_date", rs.getDate("record_date"));
                    map.put("record_count", rs.getInt("record_count"));
                    map.put("bmi", rs.getDouble("bmi"));
                    list.add(map);
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }

        /** 有 BMI 记录的用户列表（用于平均 BMI 卡片） */
        public static List<Map<String, Object>> getAvgBMIUsersList() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "WITH latest AS (" +
                    "SELECT username, bmi, weight, body_fat, record_date, " +
                    "ROW_NUMBER() OVER (PARTITION BY username ORDER BY record_date DESC) rn " +
                    "FROM health_records) " +
                    "SELECT u.username, u.age, u.gender, l.bmi, l.weight, l.body_fat, l.record_date " +
                    "FROM users u JOIN latest l ON u.username = l.username AND l.rn = 1 " +
                    "ORDER BY l.bmi DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", rs.getString("username"));
                    map.put("age", rs.getInt("age"));
                    map.put("gender", rs.getString("gender"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("weight", rs.getDouble("weight"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("record_date", rs.getDate("record_date"));
                    list.add(map);
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }

        private static int countQuery(Connection conn, String sql) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        private static double avgQuery(Connection conn, String sql) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }

        /** 获取异常用户列表 */
        public static List<Map<String, Object>> getAbnormalUsers() {
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "WITH latest AS (" +
                    "SELECT username, weight, bmi, body_fat, record_date, " +
                    "ROW_NUMBER() OVER (PARTITION BY username ORDER BY record_date DESC) rn " +
                    "FROM health_records), " +
                    "earliest AS (" +
                    "SELECT username, weight, record_date, " +
                    "ROW_NUMBER() OVER (PARTITION BY username ORDER BY record_date ASC) rn " +
                    "FROM health_records) " +
                    "SELECT u.username, u.age, u.gender, l.weight AS latest_weight, l.bmi, l.body_fat, " +
                    "l.record_date AS latest_date, e.weight AS earliest_weight, e.record_date AS earliest_date " +
                    "FROM users u JOIN latest l ON u.username = l.username AND l.rn = 1 " +
                    "LEFT JOIN earliest e ON u.username = e.username AND e.rn = 1 " +
                    "WHERE (l.weight - e.weight) > 5 " +
                    "OR l.bmi > 28 OR l.bmi < 18.5";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", rs.getString("username"));
                    map.put("age", rs.getInt("age"));
                    map.put("gender", rs.getString("gender"));
                    map.put("latest_weight", rs.getDouble("latest_weight"));
                    map.put("bmi", rs.getDouble("bmi"));
                    map.put("body_fat", rs.getDouble("body_fat"));
                    map.put("weight_diff", rs.getDouble("latest_weight") - rs.getDouble("earliest_weight"));
                    map.put("reason", rs.getDouble("bmi") > 28 ? "BMI超标" : rs.getDouble("bmi") < 18.5 ? "BMI偏低" : "体重骤变");
                    list.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取系统配置 */
        public static Map<String, String> getSystemConfig() {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT config_key, config_value FROM system_config";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    map.put(rs.getString("config_key"), rs.getString("config_value"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 更新系统配置 */
        public static boolean updateSystemConfig(String key, String value) {
            String sql = "INSERT INTO system_config (config_key, config_value, description) VALUES (?, ?, '') " +
                         "ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = NOW()";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, value);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 记录系统日志 */
        public static void logAction(String type, String operator, String action, String detail) {
            String sql = "INSERT INTO system_logs (log_type, operator, action, detail) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setString(2, operator);
                ps.setString(3, action);
                ps.setString(4, detail);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /** 获取系统日志 */
        public static List<String[]> getSystemLogs(String type) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT log_type, operator, action, detail, created_at FROM system_logs " +
                         "WHERE ? = '' OR log_type = ? ORDER BY created_at DESC LIMIT 200";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setString(2, type);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            rs.getString("log_type"),
                            rs.getString("operator"),
                            rs.getString("action"),
                            rs.getString("detail"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 导出所有用户数据为 CSV 内容字符串 */
        public static String exportUsersCSV() {
            StringBuilder sb = new StringBuilder();
            sb.append("ID,用户名,性别,年龄,身高,活动等级,账号状态,注册时间,打卡天数\n");
            List<Map<String, Object>> users = getAllUsers();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (Map<String, Object> u : users) {
                sb.append(u.get("id")).append(",");
                sb.append(u.get("username")).append(",");
                sb.append(u.get("gender")).append(",");
                sb.append(u.get("age")).append(",");
                sb.append(u.get("height")).append(",");
                sb.append(u.get("activity_level")).append(",");
                sb.append(u.get("account_status")).append(",");
                sb.append(u.get("created_at") == null ? "" : sdf.format(u.get("created_at"))).append(",");
                sb.append(u.get("checkin_days")).append("\n");
            }
            return sb.toString();
        }

        /** 导出所有健康打卡记录为 CSV 内容字符串 */
        public static String exportHealthRecordsCSV() {
            StringBuilder sb = new StringBuilder();
            sb.append("ID,用户名,记录日期,体重,体脂率,水分率,肌肉率,内脏脂肪,骨量,BMI,腰围,基础代谢,每日消耗,身体年龄,身体类型\n");
            String sql = "SELECT id, username, record_date, weight, body_fat, water_rate, muscle_rate, " +
                         "visceral_fat, bone_muscle, bmi, waist, bmr, tdee, body_age, body_type " +
                         "FROM health_records ORDER BY record_date DESC, id DESC";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getInt("id")).append(",");
                    sb.append(rs.getString("username")).append(",");
                    sb.append(rs.getTimestamp("record_date") == null ? "" : sdf.format(rs.getTimestamp("record_date"))).append(",");
                    sb.append(rs.getDouble("weight")).append(",");
                    sb.append(rs.getDouble("body_fat")).append(",");
                    sb.append(rs.getDouble("water_rate")).append(",");
                    sb.append(rs.getDouble("muscle_rate")).append(",");
                    sb.append(rs.getInt("visceral_fat")).append(",");
                    sb.append(rs.getDouble("bone_muscle")).append(",");
                    sb.append(rs.getDouble("bmi")).append(",");
                    sb.append(rs.getDouble("waist")).append(",");
                    sb.append(rs.getDouble("bmr")).append(",");
                    sb.append(rs.getDouble("tdee")).append(",");
                    sb.append(rs.getInt("body_age")).append(",");
                    sb.append(rs.getString("body_type")).append("\n");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return sb.toString();
        }

        /** 导出指定用户的健康打卡记录为 CSV（用户端导出本人数据，权限边界：仅本人）。 */
        public static String exportHealthRecordsCSV(String username) {
            StringBuilder sb = new StringBuilder();
            sb.append("ID,用户名,记录日期,体重,体脂率,水分率,肌肉率,内脏脂肪,骨量,BMI,腰围,基础代谢,每日消耗,身体年龄,身体类型\n");
            String sql = "SELECT id, username, record_date, weight, body_fat, water_rate, muscle_rate, " +
                         "visceral_fat, bone_muscle, bmi, waist, bmr, tdee, body_age, body_type " +
                         "FROM health_records WHERE username = ? ORDER BY record_date DESC, id DESC";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sb.append(rs.getInt("id")).append(",");
                        sb.append(rs.getString("username")).append(",");
                        sb.append(rs.getTimestamp("record_date") == null ? "" : sdf.format(rs.getTimestamp("record_date"))).append(",");
                        sb.append(rs.getDouble("weight")).append(",");
                        sb.append(rs.getDouble("body_fat")).append(",");
                        sb.append(rs.getDouble("water_rate")).append(",");
                        sb.append(rs.getDouble("muscle_rate")).append(",");
                        sb.append(rs.getInt("visceral_fat")).append(",");
                        sb.append(rs.getDouble("bone_muscle")).append(",");
                        sb.append(rs.getDouble("bmi")).append(",");
                        sb.append(rs.getDouble("waist")).append(",");
                        sb.append(rs.getDouble("bmr")).append(",");
                        sb.append(rs.getDouble("tdee")).append(",");
                        sb.append(rs.getInt("body_age")).append(",");
                        sb.append(rs.getString("body_type")).append("\n");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return sb.toString();
        }

        /** 保存通知消息 */
        public static boolean saveNotification(String sender, String receiver, String title, String content, String type) {
            String sql = "INSERT INTO notifications (sender, receiver, title, content, type) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sender);
                ps.setString(2, receiver);
                ps.setString(3, title);
                ps.setString(4, content);
                ps.setString(5, type);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取所有通知 */
        public static List<String[]> getAllNotifications() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, sender, receiver, title, type, status, created_at FROM notifications ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("title"),
                            rs.getString("type"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取当前用户收到的通知 */
        public static List<String[]> getMyNotifications(String username) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, sender, title, type, status, created_at FROM notifications WHERE receiver = ? ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("sender"),
                            rs.getString("title"),
                            rs.getString("type"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at") != null ? sdf.format(rs.getTimestamp("created_at")) : "-"
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 更新通知状态（标记已读） */
        public static boolean updateNotificationStatus(int id, String status) {
            String sql = "UPDATE notifications SET status = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取单条通知内容 */
        public static String getNotificationContent(int id) {
            String sql = "SELECT content FROM notifications WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getString("content");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return "";
        }

        /** 获取当前用户未读消息数量 */
        public static int getUnreadNotificationCount(String username) {
            int count = 0;
            String sql = "SELECT COUNT(*) FROM notifications WHERE receiver = ? AND status = '未读'";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) count = rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return count;
        }

        /** 获取所有食物 */
        public static List<String[]> getFoods() {
            ensureFoodColumns();
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, food_name, calories_per_100g, protein, carbs, fat FROM foods WHERE COALESCE(status,'已发布')<>'待确认' ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("food_name"),
                            String.valueOf(rs.getInt("calories_per_100g")),
                            df2.format(rs.getDouble("protein")),
                            df2.format(rs.getDouble("carbs")),
                            df2.format(rs.getDouble("fat"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 添加/更新食物 */
        public static boolean saveFood(int id, String name, int calories, double protein, double carbs, double fat) {
            try (Connection conn = getConnection()) {
                if (id <= 0) {
                    String sql = "INSERT INTO foods (food_name, calories_per_100g, protein, carbs, fat) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setInt(2, calories);
                    ps.setDouble(3, protein);
                    ps.setDouble(4, carbs);
                    ps.setDouble(5, fat);
                    return ps.executeUpdate() > 0;
                } else {
                    String sql = "UPDATE foods SET food_name = ?, calories_per_100g = ?, protein = ?, carbs = ?, fat = ? WHERE id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setInt(2, calories);
                    ps.setDouble(3, protein);
                    ps.setDouble(4, carbs);
                    ps.setDouble(5, fat);
                    ps.setInt(6, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 删除食物 */
        public static boolean deleteFood(int id) {
            String sql = "DELETE FROM foods WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * 批量导入食物（按名称 upsert：存在则更新，不存在则新增）。
         * 行数据来自 ExcelUtil，营养值已是「每100g」口径，并携带标准份量 default_grams 与图片字节。
         * 计算实际摄入时由 DietPanel 按 克数/100 缩放，因此此处的 per-100g 值即可直接使用。
         * @param rows ExcelUtil.FoodImportRow 列表
         * @return 结果描述字符串
         */
        public static String importFoods(List<ExcelUtil.FoodImportRow> rows) throws SQLException {
            int inserted = 0, updated = 0, skipped = 0;
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                String sel = "SELECT id FROM foods WHERE food_name = ?";
                String ins = "INSERT INTO foods (food_name, calories_per_100g, protein, carbs, fat, default_grams, food_image, food_phash) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                String updNoImg = "UPDATE foods SET calories_per_100g = ?, protein = ?, carbs = ?, fat = ?, default_grams = ? WHERE food_name = ?";
                String updWithImg = "UPDATE foods SET calories_per_100g = ?, protein = ?, carbs = ?, fat = ?, default_grams = ?, food_image = ?, food_phash = ? WHERE food_name = ?";
                try (PreparedStatement psSel = conn.prepareStatement(sel);
                     PreparedStatement psIns = conn.prepareStatement(ins);
                     PreparedStatement psUpd = conn.prepareStatement(updNoImg);
                     PreparedStatement psUpdImg = conn.prepareStatement(updWithImg)) {
                    for (ExcelUtil.FoodImportRow r : rows) {
                        String name = (r.name == null) ? "" : r.name.trim();
                        if (name.isEmpty()) { skipped++; continue; }
                        int cal = r.cal100;
                        double p = r.protein, c = r.carbs, f = r.fat;
                        int defG = r.portionG > 0 ? r.portionG : 100;
                        byte[] img = (r.image != null && r.image.length > 0) ? r.image : null;
                        Long phash = null;
                        if (img != null) {
                            try {
                                BufferedImage bi = ImageUtil.bufferedImageFromBytes(img);
                                phash = (bi == null) ? null : ImageUtil.perceptualHash(bi);
                            } catch (Exception ignore) { phash = null; }
                        }
                        psSel.setString(1, name);
                        try (ResultSet rs = psSel.executeQuery()) {
                            if (rs.next()) {
                                if (img != null) {
                                    psUpdImg.setInt(1, cal);
                                    psUpdImg.setDouble(2, p);
                                    psUpdImg.setDouble(3, c);
                                    psUpdImg.setDouble(4, f);
                                    psUpdImg.setInt(5, defG);
                                    psUpdImg.setBytes(6, img);
                                    psUpdImg.setObject(7, phash);
                                    psUpdImg.setString(8, name);
                                    psUpdImg.executeUpdate();
                                } else {
                                    psUpd.setInt(1, cal);
                                    psUpd.setDouble(2, p);
                                    psUpd.setDouble(3, c);
                                    psUpd.setDouble(4, f);
                                    psUpd.setInt(5, defG);
                                    psUpd.setString(6, name);
                                    psUpd.executeUpdate();
                                }
                                updated++;
                            } else {
                                psIns.setString(1, name);
                                psIns.setInt(2, cal);
                                psIns.setDouble(3, p);
                                psIns.setDouble(4, c);
                                psIns.setDouble(5, f);
                                psIns.setInt(6, defG);
                                psIns.setBytes(7, img);
                                psIns.setObject(8, phash);
                                psIns.executeUpdate();
                                inserted++;
                            }
                        }
                    }
                }
                conn.commit();
            }
            return String.format("新增 %d 条，更新 %d 条，跳过 %d 条", inserted, updated, skipped);
        }

        /** 食物图片相似度哈希阈值：汉明距离 ≤ 该值视为同一/近似食物 */
        public static final int FOOD_PHASH_THRESHOLD = 10;
        /** 用图片"覆盖"一个确信的名称匹配时所需更接近的阈值（近原图，避免相似图误覆盖正确命名） */
        public static final int FOOD_PHASH_EXACT = 4;

        private static boolean foodColumnsReady = false;

        /** 自愈合：首次调用时为 foods 表补 图片/phash/status 列，并把含糊的 calories 重命名为 calories_per_100g（兼容老库，不破坏已有数据） */
        public static void ensureFoodColumns() {
            if (foodColumnsReady) return;
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                try { stmt.executeUpdate("ALTER TABLE foods ADD COLUMN IF NOT EXISTS food_image BYTEA"); } catch (SQLException ignore) {}
                try { stmt.executeUpdate("ALTER TABLE foods ADD COLUMN IF NOT EXISTS food_phash BIGINT"); } catch (SQLException ignore) {}
                try { stmt.executeUpdate("ALTER TABLE foods ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '已发布'"); } catch (SQLException ignore) {}
                try { stmt.executeUpdate("ALTER TABLE foods ADD COLUMN IF NOT EXISTS default_grams INT DEFAULT 100"); } catch (SQLException ignore) {}
                try { stmt.executeUpdate("UPDATE foods SET status='已发布' WHERE status IS NULL"); } catch (SQLException ignore) {}
                // 食物热量语义显式化：calories(含糊) -> calories_per_100g（数据本就是每100g）
                try { stmt.executeUpdate("DO $$ BEGIN IF EXISTS ("
                        + "SELECT 1 FROM information_schema.columns "
                        + "WHERE table_name='foods' AND column_name='calories') "
                        + "THEN ALTER TABLE foods RENAME COLUMN calories TO calories_per_100g; END IF; END $$"); } catch (SQLException ignore) {}
                foodColumnsReady = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /** 带图食物行（含感知哈希与状态、标准份量克数）。图片为 BYTEA 原始字节。 */
        public record FoodRow(int id, String name, int cal, double protein, double carbs, double fat,
                              byte[] image, Long phash, String status, int defaultGrams) {}

        private static FoodRow foodRowFromRs(ResultSet rs) throws SQLException {
            return new FoodRow(
                    rs.getInt("id"),
                    rs.getString("food_name"),
                    rs.getInt("calories_per_100g"),
                    rs.getDouble("protein"),
                    rs.getDouble("carbs"),
                    rs.getDouble("fat"),
                    rs.getBytes("food_image"),
                    (Long) rs.getObject("food_phash"),
                    rs.getString("status"),
                    rs.getInt("default_grams"));
        }

        private static final String FOOD_SELECT =
                "SELECT id, food_name, calories_per_100g, protein, carbs, fat, food_image, food_phash, COALESCE(status,'已发布') AS status, default_grams FROM foods ";

        /** 已发布食物（含图/phash），供管理面板与识图匹配（排除草稿）。 */
        public static List<FoodRow> getFoodsWithImage() {
            ensureFoodColumns();
            List<FoodRow> list = new ArrayList<>();
            String sql = FOOD_SELECT + "WHERE COALESCE(status,'已发布')<>'待确认' ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(foodRowFromRs(rs));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 待确认草稿食物（AI 识图新增，待审核）。 */
        public static List<FoodRow> getDraftFoods() {
            ensureFoodColumns();
            List<FoodRow> list = new ArrayList<>();
            String sql = FOOD_SELECT + "WHERE status='待确认' ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(foodRowFromRs(rs));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 模糊候选：food_name 双向 LIKE（解决「香辣鸡腿堡」↔「塔斯汀香辣鸡腿堡」）。 */
        public static List<FoodRow> searchFoodsFuzzy(String name) {
            ensureFoodColumns();
            List<FoodRow> list = new ArrayList<>();
            if (name == null || name.trim().isEmpty()) return list;
            String n = name.trim();
            String sql = FOOD_SELECT
                    + "WHERE COALESCE(status,'已发布')<>'待确认' AND (food_name ILIKE ? OR ? ILIKE '%'||food_name||'%') ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "%" + n + "%");
                ps.setString(2, n);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(foodRowFromRs(rs));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /**
         * 识图匹配：1) 精确名 → 2) 全局 pHash 最近邻 → 3) 模糊候选 → 4) pHash 在候选间消歧。
         * 命中返回食物行；无匹配返回 null（调用方应建草稿）。
         *
         * 关键修正：原实现仅在"名称模糊候选"内部用 pHash 消歧，导致即使上传了库里原图，
         * 只要模型返回的名字没把正确食物带进候选集，原图就永远不被使用，从而误判成别的食物。
         * 现把 pHash 提升为全局最近邻匹配——原图/高度相似图在「名称无候选或模糊」时可直接命中。
         *
         * 防误判权衡：当模型给出"唯一且有把握"的名称匹配时，默认信任命名；仅当上传图与库中
         * 另一食物近乎完全一致（汉明距离 ≤ FOOD_PHASH_EXACT，即近原图）才以图覆盖，
         * 避免两张视觉相似的汉堡照被误判成对方（图优先的已知风险）。
         */
        public static FoodRow matchFood(String name, long uploadHash) {
            if (name == null || name.trim().isEmpty()) return null;
            String n = name.trim();
            List<FoodRow> all = getFoodsWithImage();
            // 1. 精确名（最可靠身份）
            for (FoodRow fr : all) {
                if (fr.name() != null && fr.name().trim().equalsIgnoreCase(n)) return fr;
            }
            // 2. 全局 pHash 最近邻：上传图与库中原图高度相似时直接按图匹配
            FoodRow bestImg = null;
            int bestImgDist = Integer.MAX_VALUE;
            for (FoodRow fr : all) {
                if (fr.phash() == null) continue;
                int d = ImageUtil.hamming(uploadHash, fr.phash());
                if (d < bestImgDist) { bestImgDist = d; bestImg = fr; }
            }
            // 3. 模糊名候选
            List<FoodRow> cands = searchFoodsFuzzy(n);
            if (cands.isEmpty()) {
                // 名称在库中无任何候选 → 一律视为库中不存在的新食物，交由调用方建草稿。
                // 绝不用图片相似度兜底指派身份：感知哈希仅在"近原图复件"时可靠，拿去给未知食物推断
                // 身份会产生语义错配（如冰红茶被误判成视觉相近的苹果）。食物身份必须来自模型返回的名字。
                return null;
            }
            if (cands.size() == 1) {
                // 名称唯一候选：默认信任模型命名。仅当上传图与库中"另一"食物近乎完全一致
                // （近原图，汉明距离 ≤ FOOD_PHASH_EXACT）时才以图为准，避免视觉相似图误覆盖正确命名。
                if (bestImg != null && bestImgDist <= FOOD_PHASH_EXACT && bestImg.id() != cands.get(0).id()) {
                    return bestImg;
                }
                return cands.get(0);
            }
            // 4. 多个候选 → pHash 在候选间消歧
            FoodRow best = null;
            int bestDist = Integer.MAX_VALUE;
            for (FoodRow fr : cands) {
                if (fr.phash() == null) continue;
                int d = ImageUtil.hamming(uploadHash, fr.phash());
                if (d < bestDist) { bestDist = d; best = fr; }
            }
            if (best != null && bestDist <= FOOD_PHASH_THRESHOLD) return best;
            // 候选内无法用图消歧：仅当上传图与库中"另一"食物近乎完全一致(近原图)才以图覆盖，
            // 否则不采信松散的视觉相似，避免误匹配到语义不同的食物。
            return (bestImg != null && bestImgDist <= FOOD_PHASH_EXACT) ? bestImg : null;
        }

        /** 保存食物（带图）。有图时计算 pHash 一并写入；无图则写 NULL。 */
        public static boolean saveFood(int id, String name, int calories, double protein, double carbs, double fat, byte[] image) {
            ensureFoodColumns();
            try (Connection conn = getConnection()) {
                if (id <= 0) {
                    Long phash = (image != null && image.length > 0)
                            ? ImageUtil.perceptualHash(ImageUtil.bufferedImageFromBytes(image)) : null;
                    String sql = "INSERT INTO foods (food_name, calories_per_100g, protein, carbs, fat, food_image, food_phash, status) "
                            + "VALUES (?,?,?,?,?,?,?,'已发布')";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setInt(2, calories);
                    ps.setDouble(3, protein);
                    ps.setDouble(4, carbs);
                    ps.setDouble(5, fat);
                    if (image != null && image.length > 0) {
                        ps.setBytes(6, image);
                        ps.setObject(7, phash);
                    } else {
                        ps.setNull(6, Types.BINARY);
                        ps.setNull(7, Types.BIGINT);
                    }
                    return ps.executeUpdate() > 0;
                } else {
                    if (image != null && image.length > 0) {
                        Long phash = ImageUtil.perceptualHash(ImageUtil.bufferedImageFromBytes(image));
                        String sql = "UPDATE foods SET food_name=?, calories_per_100g=?, protein=?, carbs=?, fat=?, food_image=?, food_phash=? WHERE id=?";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, name);
                        ps.setInt(2, calories);
                        ps.setDouble(3, protein);
                        ps.setDouble(4, carbs);
                        ps.setDouble(5, fat);
                        ps.setBytes(6, image);
                        ps.setObject(7, phash);
                        ps.setInt(8, id);
                        return ps.executeUpdate() > 0;
                    }
                    // 无新图：保留原图与 phash，仅更新文本字段
                    String sql = "UPDATE foods SET food_name=?, calories_per_100g=?, protein=?, carbs=?, fat=? WHERE id=?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setInt(2, calories);
                    ps.setDouble(3, protein);
                    ps.setDouble(4, carbs);
                    ps.setDouble(5, fat);
                    ps.setInt(6, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 命中但库图为空时，用上传图补充（"相似则加上"）。 */
        public static boolean updateFoodImage(int id, byte[] image) {
            ensureFoodColumns();
            if (image == null || image.length == 0) return false;
            try (Connection conn = getConnection()) {
                Long phash = ImageUtil.perceptualHash(ImageUtil.bufferedImageFromBytes(image));
                String sql = "UPDATE foods SET food_image=?, food_phash=? WHERE id=?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setBytes(1, image);
                ps.setObject(2, phash);
                ps.setInt(3, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** AI 识图无匹配时，建一条「待确认」草稿（带图+phash），返回新 id。
         *  AI 返回的是「每份」数值，这里统一折算成每100g（与食物库模型一致），
         *  并把 AI 的每份克数存入 default_grams 作为标准份量；approveFood 仅需翻转状态即可。 */
        public static int saveDraftFood(String name, int grams, int cal, double p, double c, double f, byte[] image) {
            ensureFoodColumns();
            try (Connection conn = getConnection()) {
                Long phash = (image != null && image.length > 0)
                        ? ImageUtil.perceptualHash(ImageUtil.bufferedImageFromBytes(image)) : null;
                // 每份 → 每100g 折算（无份量信息时退化为原值，default_grams 取 100）
                int defG = grams > 0 ? grams : 100;
                int cal100 = (cal > 0 && grams > 0) ? (int) Math.round(cal * 100.0 / grams) : cal;
                double p100 = (p > 0 && grams > 0) ? p * 100.0 / grams : p;
                double c100 = (c > 0 && grams > 0) ? c * 100.0 / grams : c;
                double f100 = (f > 0 && grams > 0) ? f * 100.0 / grams : f;
                String sql = "INSERT INTO foods (food_name, calories_per_100g, protein, carbs, fat, default_grams, food_image, food_phash, status) "
                            + "VALUES (?,?,?,?,?,?,?,?,'待确认') RETURNING id";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, name);
                ps.setInt(2, cal100);
                ps.setDouble(3, p100);
                ps.setDouble(4, c100);
                ps.setDouble(5, f100);
                ps.setInt(6, defG);
                if (image != null && image.length > 0) {
                    ps.setBytes(7, image);
                    ps.setObject(8, phash);
                } else {
                    ps.setNull(7, Types.BINARY);
                    ps.setNull(8, Types.BIGINT);
                }
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return -1;
        }

        /** 审核通过草稿 → 转为已发布。 */
        public static boolean approveFood(int id) {
            ensureFoodColumns();
            String sql = "UPDATE foods SET status='已发布' WHERE id=?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 拒绝草稿 → 删除。 */
        public static boolean rejectFood(int id) {
            return deleteFood(id);
        }

        private static int parseIntSafe(String s) {
            return (int) Math.round(parseDblSafe(s));
        }

        private static double parseDblSafe(String s) {
            if (s == null) return 0;
            String t = s.trim().replaceAll("[^0-9.\\-]", "");
            if (t.isEmpty()) return 0;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** 获取所有运动库项目 */
        public static List<String[]> getExerciseLibrary() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, exercise_name, exercise_type, calories_per_hour, intensity_level, description FROM exercise_library ORDER BY id";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("exercise_name"),
                            rs.getString("exercise_type"),
                            String.valueOf(rs.getInt("calories_per_hour")),
                            rs.getString("intensity_level"),
                            rs.getString("description")
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 保存运动库项目 */
        public static boolean saveExerciseLibrary(int id, String name, String type, int calories, String intensity, String desc) {
            try (Connection conn = getConnection()) {
                if (id <= 0) {
                    String sql = "INSERT INTO exercise_library (exercise_name, exercise_type, calories_per_hour, intensity_level, description) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, type);
                    ps.setInt(3, calories);
                    ps.setString(4, intensity);
                    ps.setString(5, desc);
                    return ps.executeUpdate() > 0;
                } else {
                    String sql = "UPDATE exercise_library SET exercise_name = ?, exercise_type = ?, calories_per_hour = ?, intensity_level = ?, description = ? WHERE id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, type);
                    ps.setInt(3, calories);
                    ps.setString(4, intensity);
                    ps.setString(5, desc);
                    ps.setInt(6, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 删除运动库项目 */
        public static boolean deleteExerciseLibrary(int id) {
            String sql = "DELETE FROM exercise_library WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取所有健康文章（不含待审核投稿；待审核仅在审核队列） */
        public static List<String[]> getHealthArticles() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, title, category, status, author, author_type, published_at FROM health_articles WHERE status <> '待审核' ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    String at = rs.getString("author_type");
                    String author = rs.getString("author");
                    if (at == null || at.isEmpty()) { at = "admin"; if (author == null) author = "官方"; }
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getString("status"),
                            author == null ? "" : author,
                            at,
                            sdf.format(rs.getTimestamp("published_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取所有已发布健康文章（用户端展示，仅返回已发布状态） */
        public static List<String[]> getPublishedHealthArticles() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, title, category, author, author_type, published_at FROM health_articles WHERE status = '已发布' ORDER BY published_at DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    String at = rs.getString("author_type");
                    String author = rs.getString("author");
                    if (at == null || at.isEmpty()) { at = "admin"; if (author == null) author = "官方"; }
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("title"),
                            rs.getString("category"),
                            author == null ? "" : author,
                            at,
                            sdf.format(rs.getTimestamp("published_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 根据 ID 获取文章正文 */
        public static Map<String, String> getHealthArticleById(int id) {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT title, content, category, author, author_type, status, published_at FROM health_articles WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    map.put("title", rs.getString("title"));
                    map.put("content", rs.getString("content"));
                    map.put("category", rs.getString("category"));
                    map.put("author", rs.getString("author"));
                    map.put("author_type", rs.getString("author_type"));
                    map.put("status", rs.getString("status"));
                    map.put("published_at", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(rs.getTimestamp("published_at")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 保存健康文章 */
        public static boolean saveHealthArticle(int id, String title, String content, String category,
                                                String author, String authorType, String status) {
            try (Connection conn = getConnection()) {
                String at = (authorType == null || authorType.isEmpty()) ? "admin" : authorType;
                String st = (status == null || status.isEmpty()) ? "已发布" : status;
                if (id <= 0) {
                    String sql = "INSERT INTO health_articles (title, content, category, author, author_type, status) VALUES (?, ?, ?, ?, ?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, title);
                    ps.setString(2, content);
                    ps.setString(3, category);
                    ps.setString(4, author);
                    ps.setString(5, at);
                    ps.setString(6, st);
                    return ps.executeUpdate() > 0;
                } else {
                    String sql = "UPDATE health_articles SET title = ?, content = ?, category = ?, author = ?, author_type = ?, status = ? WHERE id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, title);
                    ps.setString(2, content);
                    ps.setString(3, category);
                    ps.setString(4, author);
                    ps.setString(5, at);
                    ps.setString(6, st);
                    ps.setInt(7, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 待审核文章队列（用户/机构投稿，供管理员审核）。返回 id,title,category,author,author_type,submitted_at */
        public static List<String[]> getPendingArticles() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, title, category, author, author_type, published_at FROM health_articles WHERE status = '待审核' ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    String at = rs.getString("author_type");
                    if (at == null || at.isEmpty()) at = "user";
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getString("author") == null ? "" : rs.getString("author"),
                            at,
                            sdf.format(rs.getTimestamp("published_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 审核通过：转为已发布，记录审核人/时间 */
        public static boolean approveArticle(int id, String reviewer) {
            String sql = "UPDATE health_articles SET status = '已发布', reviewer = ?, reviewed_at = NOW() WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reviewer);
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 审核驳回：记录审核人/时间/原因，转为已驳回 */
        public static boolean rejectArticle(int id, String reviewer, String reason) {
            String sql = "UPDATE health_articles SET status = '已驳回', reviewer = ?, reviewed_at = NOW(), reject_reason = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reviewer);
                ps.setString(2, reason);
                ps.setInt(3, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 某作者(用户/机构)的投稿列表(含状态)，用于「我的投稿」 */
        public static List<String[]> getMyArticles(String author, String authorType) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, title, category, status, published_at FROM health_articles WHERE author = ? AND author_type = ? ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, author);
                ps.setString(2, authorType);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("published_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取 AI 模板 */
        public static List<String[]> getAITemplates() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, template_name, template_type, status, updated_at FROM ai_templates ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("template_name"),
                            rs.getString("template_type"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("updated_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 保存 AI 模板 */
        public static boolean saveAITemplate(int id, String name, String type, String content) {
            try (Connection conn = getConnection()) {
                if (id <= 0) {
                    String sql = "INSERT INTO ai_templates (template_name, template_type, prompt_text) VALUES (?, ?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, type);
                    ps.setString(3, content);
                    return ps.executeUpdate() > 0;
                } else {
                    String sql = "UPDATE ai_templates SET template_name = ?, template_type = ?, prompt_text = ? WHERE id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, type);
                    ps.setString(3, content);
                    ps.setInt(4, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取单个 AI 模板详情 */
        public static Map<String, String> getAITemplateById(int id) {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT id, template_name, template_type, prompt_text, status, updated_at FROM ai_templates WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                if (rs.next()) {
                    map.put("id", String.valueOf(rs.getInt("id")));
                    map.put("template_name", rs.getString("template_name"));
                    map.put("template_type", rs.getString("template_type"));
                    map.put("prompt_text", rs.getString("prompt_text"));
                    map.put("status", rs.getString("status"));
                    map.put("updated_at", rs.getTimestamp("updated_at") != null ? sdf.format(rs.getTimestamp("updated_at")) : "-");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 获取所有 AI 问答记录（管理员，过滤已标记无效的记录） */
        public static List<String[]> getAIChatRecords() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, username, question, status, created_at FROM ai_chat_records WHERE COALESCE(status,'有效') <> '无效' ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("username"),
                            rs.getString("question"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取所有 AI 饮食推荐记录（管理员，过滤已标记无效的记录） */
        public static List<String[]> getAIDietRecords() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, username, query, status, created_at FROM ai_diet_records WHERE COALESCE(status,'有效') <> '无效' ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("username"),
                            rs.getString("query"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取所有 AI 菜谱生成记录（管理员，过滤已标记无效的记录） */
        public static List<String[]> getAICookbookRecords() {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, username, ingredients, flavor, meal, people, status, created_at FROM ai_cookbook_records WHERE COALESCE(status,'有效') <> '无效' ORDER BY id DESC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("username"),
                            rs.getString("ingredients"),
                            rs.getString("flavor"),
                            rs.getString("meal"),
                            String.valueOf(rs.getInt("people")),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 保存 AI 问答记录 */
        public static boolean saveAIChatRecord(String username, String question, String answer) {
            String sql = "INSERT INTO ai_chat_records (username, question, answer) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, question);
                ps.setString(3, answer);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 保存 AI 饮食推荐记录（自动建表） */
        public static boolean saveAIDietRecord(String username, String query, String result) {
            String createSql = "CREATE TABLE IF NOT EXISTS ai_diet_records (" +
                    "id SERIAL PRIMARY KEY, " +
                    "username VARCHAR(50) REFERENCES users(username), " +
                    "query TEXT, " +
                    "result TEXT, " +
                    "status VARCHAR(20) DEFAULT '有效', " +
                    "created_at TIMESTAMP DEFAULT NOW())";
            String alterSql = "ALTER TABLE ai_diet_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '有效'";
            String insertSql = "INSERT INTO ai_diet_records (username, query, result) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 PreparedStatement ps = conn.prepareStatement(insertSql)) {
                stmt.executeUpdate(createSql);
                stmt.executeUpdate(alterSql);
                ps.setString(1, username);
                ps.setString(2, query);
                ps.setString(3, result);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 保存 AI 菜谱生成记录（自动建表） */
        public static boolean saveAICookbookRecord(String username, String ingredients, String flavor, String meal, int people, String result) {
            String createSql = "CREATE TABLE IF NOT EXISTS ai_cookbook_records (" +
                    "id SERIAL PRIMARY KEY, " +
                    "username VARCHAR(50) REFERENCES users(username), " +
                    "ingredients TEXT, " +
                    "flavor VARCHAR(50), " +
                    "meal VARCHAR(20), " +
                    "people INT, " +
                    "result TEXT, " +
                    "status VARCHAR(20) DEFAULT '有效', " +
                    "created_at TIMESTAMP DEFAULT NOW())";
            String alterSql = "ALTER TABLE ai_cookbook_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT '有效'";
            String insertSql = "INSERT INTO ai_cookbook_records (username, ingredients, flavor, meal, people, result) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 PreparedStatement ps = conn.prepareStatement(insertSql)) {
                stmt.executeUpdate(createSql);
                stmt.executeUpdate(alterSql);
                ps.setString(1, username);
                ps.setString(2, ingredients);
                ps.setString(3, flavor);
                ps.setString(4, meal);
                ps.setInt(5, people);
                ps.setString(6, result);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 删除指定用户的某条菜谱记录（按 id + username 双重限定，防止越权） */
        public static boolean deleteAICookbookRecord(int id, String username) {
            String sql = "DELETE FROM ai_cookbook_records WHERE id = ? AND username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 删除指定用户的某条饮食方案记录（id + username 双重限定） */
        public static boolean deleteAIDietRecord(int id, String username) {
            String sql = "DELETE FROM ai_diet_records WHERE id = ? AND username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 删除指定用户的某条问答记录（id + username 双重限定） */
        public static boolean deleteAIChatRecord(int id, String username) {
            String sql = "DELETE FROM ai_chat_records WHERE id = ? AND username = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取 AI 使用统计（管理员） */
        public static List<String[]> getAIUsageStats() {
            List<String[]> list = new ArrayList<>();
            String createChat = "CREATE TABLE IF NOT EXISTS ai_chat_records (id SERIAL PRIMARY KEY, username VARCHAR(50) REFERENCES users(username), question TEXT, answer TEXT, status VARCHAR(20) DEFAULT '有效', created_at TIMESTAMP DEFAULT NOW())";
            String createDiet = "CREATE TABLE IF NOT EXISTS ai_diet_records (id SERIAL PRIMARY KEY, username VARCHAR(50) REFERENCES users(username), query TEXT, result TEXT, status VARCHAR(20) DEFAULT '有效', created_at TIMESTAMP DEFAULT NOW())";
            String createCookbook = "CREATE TABLE IF NOT EXISTS ai_cookbook_records (id SERIAL PRIMARY KEY, username VARCHAR(50) REFERENCES users(username), ingredients TEXT, flavor VARCHAR(50), meal VARCHAR(20), people INT, result TEXT, status VARCHAR(20) DEFAULT '有效', created_at TIMESTAMP DEFAULT NOW())";
            String sql = "SELECT u.username, " +
                    "COALESCE(c.chat_count, 0) AS chat_count, " +
                    "COALESCE(d.diet_count, 0) AS diet_count, " +
                    "COALESCE(b.cookbook_count, 0) AS cookbook_count, " +
                    "GREATEST(COALESCE(c.last_time, TIMESTAMP '1970-01-01 00:00:00'), " +
                            "COALESCE(d.last_time, TIMESTAMP '1970-01-01 00:00:00'), " +
                            "COALESCE(b.last_time, TIMESTAMP '1970-01-01 00:00:00')) AS last_time " +
                    "FROM users u " +
                    "LEFT JOIN (SELECT username, COUNT(*) AS chat_count, MAX(created_at) AS last_time FROM ai_chat_records WHERE COALESCE(status,'有效')<>'无效' GROUP BY username) c ON u.username = c.username " +
                    "LEFT JOIN (SELECT username, COUNT(*) AS diet_count, MAX(created_at) AS last_time FROM ai_diet_records WHERE COALESCE(status,'有效')<>'无效' GROUP BY username) d ON u.username = d.username " +
                    "LEFT JOIN (SELECT username, COUNT(*) AS cookbook_count, MAX(created_at) AS last_time FROM ai_cookbook_records WHERE COALESCE(status,'有效')<>'无效' GROUP BY username) b ON u.username = b.username " +
                    "ORDER BY u.username";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                stmt.executeUpdate(createChat);
                stmt.executeUpdate(createDiet);
                stmt.executeUpdate(createCookbook);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    Timestamp lastT = rs.getTimestamp("last_time");
                    String lastStr = (lastT != null && lastT.getTime() > 0) ? sdf.format(lastT) : "-";
                    list.add(new String[]{
                            rs.getString("username"),
                            String.valueOf(rs.getInt("chat_count")),
                            String.valueOf(rs.getInt("diet_count")),
                            String.valueOf(rs.getInt("cookbook_count")),
                            lastStr
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取当前用户的 AI 问答记录（隐藏被管理员标记为无效的记录） */
        public static List<String[]> getAIChatRecordsByUser(String username, int limit) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, question, answer, status, created_at FROM ai_chat_records " +
                         "WHERE username = ? AND COALESCE(status,'有效') <> '无效' ORDER BY id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("question"),
                            rs.getString("answer"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取当前用户的 AI 饮食记录（隐藏被标记为无效的记录） */
        public static List<String[]> getAIDietRecordsByUser(String username, int limit) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, query, result, status, created_at FROM ai_diet_records " +
                         "WHERE username = ? AND COALESCE(status,'有效') <> '无效' ORDER BY id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("query"),
                            rs.getString("result"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 获取当前用户的 AI 菜谱记录（隐藏被标记为无效的记录） */
        public static List<String[]> getAICookbookRecordsByUser(String username, int limit) {
            List<String[]> list = new ArrayList<>();
            String sql = "SELECT id, ingredients, flavor, meal, people, result, status, created_at FROM ai_cookbook_records " +
                         "WHERE username = ? AND COALESCE(status,'有效') <> '无效' ORDER BY id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("ingredients"),
                            rs.getString("flavor"),
                            rs.getString("meal"),
                            String.valueOf(rs.getInt("people")),
                            rs.getString("result"),
                            rs.getString("status"),
                            sdf.format(rs.getTimestamp("created_at"))
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        /** 根据 ID 查询 AI 问答记录详情 */
        public static Map<String, String> getAIChatRecordById(int id) {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT id, username, question, answer, status, created_at FROM ai_chat_records WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                if (rs.next()) {
                    map.put("id", String.valueOf(rs.getInt("id")));
                    map.put("username", rs.getString("username"));
                    map.put("question", rs.getString("question"));
                    map.put("answer", rs.getString("answer"));
                    map.put("status", rs.getString("status"));
                    map.put("created_at", sdf.format(rs.getTimestamp("created_at")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 更新 AI 问答状态（带状态一致校验与重复通知防护） */
        public static boolean updateAIChatStatus(int id, String status) {
            String select = "SELECT status, notified FROM ai_chat_records WHERE id = ?";
            String update = "UPDATE ai_chat_records SET status = ?, notified = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement psSel = conn.prepareStatement(select)) {
                psSel.setInt(1, id);
                ResultSet rs = psSel.executeQuery();
                if (!rs.next()) return false;
                String curStatus = rs.getString("status");
                boolean notified = rs.getBoolean("notified");
                if (status.equals(curStatus)) return false; // 已是目标状态，不重复操作
                boolean newNotified = "无效".equals(status) || notified; // 标记无效时锁定已通知
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, status);
                    ps.setBoolean(2, newNotified);
                    ps.setInt(3, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 根据 ID 查询 AI 饮食推荐记录详情 */
        public static Map<String, String> getAIDietRecordById(int id) {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT id, username, query, result, status, created_at FROM ai_diet_records WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                if (rs.next()) {
                    map.put("id", String.valueOf(rs.getInt("id")));
                    map.put("username", rs.getString("username"));
                    map.put("query", rs.getString("query"));
                    map.put("result", rs.getString("result"));
                    map.put("status", rs.getString("status"));
                    map.put("created_at", sdf.format(rs.getTimestamp("created_at")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 根据 ID 查询 AI 菜谱生成记录详情 */
        public static Map<String, String> getAICookbookRecordById(int id) {
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT id, username, ingredients, flavor, meal, people, result, status, created_at FROM ai_cookbook_records WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                if (rs.next()) {
                    map.put("id", String.valueOf(rs.getInt("id")));
                    map.put("username", rs.getString("username"));
                    map.put("ingredients", rs.getString("ingredients"));
                    map.put("flavor", rs.getString("flavor"));
                    map.put("meal", rs.getString("meal"));
                    map.put("people", String.valueOf(rs.getInt("people")));
                    map.put("result", rs.getString("result"));
                    map.put("status", rs.getString("status"));
                    map.put("created_at", sdf.format(rs.getTimestamp("created_at")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        /** 更新 AI 饮食推荐状态（带状态一致校验与重复通知防护） */
        public static boolean updateAIDietStatus(int id, String status) {
            String select = "SELECT status, notified FROM ai_diet_records WHERE id = ?";
            String update = "UPDATE ai_diet_records SET status = ?, notified = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement psSel = conn.prepareStatement(select)) {
                psSel.setInt(1, id);
                ResultSet rs = psSel.executeQuery();
                if (!rs.next()) return false;
                String curStatus = rs.getString("status");
                boolean notified = rs.getBoolean("notified");
                if (status.equals(curStatus)) return false;
                boolean newNotified = "无效".equals(status) || notified;
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, status);
                    ps.setBoolean(2, newNotified);
                    ps.setInt(3, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 更新 AI 菜谱生成状态（带状态一致校验与重复通知防护） */
        public static boolean updateAICookbookStatus(int id, String status) {
            String select = "SELECT status, notified FROM ai_cookbook_records WHERE id = ?";
            String update = "UPDATE ai_cookbook_records SET status = ?, notified = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement psSel = conn.prepareStatement(select)) {
                psSel.setInt(1, id);
                ResultSet rs = psSel.executeQuery();
                if (!rs.next()) return false;
                String curStatus = rs.getString("status");
                boolean notified = rs.getBoolean("notified");
                if (status.equals(curStatus)) return false;
                boolean newNotified = "无效".equals(status) || notified;
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, status);
                    ps.setBoolean(2, newNotified);
                    ps.setInt(3, id);
                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 获取 AI API 配置（默认使用智谱 GLM-4.7-Flash） */
        public static Map<String, String> getAIApiConfig() {
            Map<String, String> cfg = new HashMap<>();
            cfg.put("provider_name", "zhipu");
            cfg.put("api_key", "");
            cfg.put("model_name", "glm-4.7-flash");
            cfg.put("vision_model", "glm-4v-flash");
            cfg.put("endpoint_url", "https://open.bigmodel.cn/api/paas/v4");
            cfg.put("proxy_host", "");
            cfg.put("proxy_port", "");
            String sql = "SELECT provider AS provider_name, api_key, model_name, vision_model, endpoint AS endpoint_url, proxy_host, proxy_port FROM ai_api_config WHERE provider = ? ORDER BY id DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "zhipu");
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    cfg.put("provider_name", rs.getString("provider_name"));
                    cfg.put("api_key", rs.getString("api_key") == null ? "" : rs.getString("api_key"));
                    cfg.put("model_name", rs.getString("model_name") == null ? "glm-4.7-flash" : rs.getString("model_name"));
                    cfg.put("vision_model", rs.getString("vision_model") == null ? "glm-4v-flash" : rs.getString("vision_model"));
                    cfg.put("endpoint_url", rs.getString("endpoint_url") == null ? "https://open.bigmodel.cn/api/paas/v4" : rs.getString("endpoint_url"));
                    cfg.put("proxy_host", rs.getString("proxy_host") == null ? "" : rs.getString("proxy_host"));
                    cfg.put("proxy_port", rs.getString("proxy_port") == null ? "" : rs.getString("proxy_port"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return cfg;
        }

        /** 通用 OpenAI 兼容对话调用（智谱 / 硅基流动等任意兼容服务均可） */
        public static String callOpenAIChat(String apiKey, String prompt) throws Exception {
            Map<String, String> cfg = getAIApiConfig();
            String endpoint = cfg.getOrDefault("endpoint_url", "https://open.bigmodel.cn/api/paas/v4");
            String model = cfg.getOrDefault("model_name", "glm-4.7-flash");
            String fullUrl = endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions";
            Proxy proxy = buildProxy(cfg);
            for (int attempt = 0; attempt < 2; attempt++) {
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (proxy == null)
                    ? (HttpURLConnection) url.openConnection()
                    : (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            String escaped = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            String body = "{\"model\":\"" + model.replace("\\", "\\\\").replace("\"", "\\\"") + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}]}";
            try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            String resp = response.toString();
            reader.close();
            if (code == 429 && attempt == 0) { try { Thread.sleep(2000); } catch (InterruptedException ie) {} continue; }
            if (code >= 400) {
                if (code == 429) return "AI 调用被速率限制（HTTP 429）：您的账户请求过于频繁，请稍候再试，或升级套餐提高额度。";
                return "AI 调用失败 (HTTP " + code + "): " + resp;
            }
            String content = extractContent(resp);
            return content != null ? content : ("AI 返回: " + resp);
            }
            return "AI 调用失败：速率限制重试后仍失败，请稍候再试。";
        }

        /** 多模态视觉调用：传入图片 base64，让模型识别食物并返回结构化文本 */
        public static String callVision(String apiKey, String visionModel, String prompt, String base64Image) throws Exception {
            Map<String, String> cfg = getAIApiConfig();
            String endpoint = cfg.getOrDefault("endpoint_url", "https://open.bigmodel.cn/api/paas/v4");
            String model = (visionModel == null || visionModel.trim().isEmpty()) ? cfg.getOrDefault("vision_model", "glm-4v-flash") : visionModel.trim();
            String fullUrl = endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions";
            Proxy proxy = buildProxy(cfg);
            for (int attempt = 0; attempt < 2; attempt++) {
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (proxy == null)
                    ? (HttpURLConnection) url.openConnection()
                    : (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            String p = escJson(prompt);
            String img = base64Image.replace("\\", "\\\\").replace("\"", "\\\"");
            String body = "{\"model\":\"" + escJson(model) + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":["
                    + "{\"type\":\"text\",\"text\":\"" + p + "\"},"
                    + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64," + img + "\"}}"
                    + "]}]}";
            try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            String resp = response.toString();
            reader.close();
            if (code == 429 && attempt == 0) { try { Thread.sleep(2000); } catch (InterruptedException ie) {} continue; }
            if (code >= 400) {
                if (code == 429) return "AI 调用被速率限制（HTTP 429）：您的账户请求过于频繁，请稍候再试，或升级套餐提高额度。";
                return "AI 调用失败 (HTTP " + code + "): " + resp;
            }
            String content = extractContent(resp);
            return content != null ? content : ("AI 返回: " + resp);
            }
            return "AI 调用失败：速率限制重试后仍失败，请稍候再试。";
        }

        /** 构造 HTTP 代理；未配置时返回 null（交由 openConnection() 走直连或系统代理） */
        private static Proxy buildProxy(Map<String, String> cfg) {
            String ph = cfg == null ? "" : cfg.getOrDefault("proxy_host", "");
            if (ph == null || ph.trim().isEmpty()) return null;
            int port;
            try {
                String pp = cfg.getOrDefault("proxy_port", "").trim();
                port = pp.isEmpty() ? 80 : Integer.parseInt(pp);
            } catch (NumberFormatException e) {
                port = 80;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ph.trim(), port));
        }

        private static String escJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }

        /** 从 OpenAI 兼容响应中提取 message.content（支持转义字符） */
        public static String extractContent(String json) {
            int idx = json.indexOf("\"content\":\"");
            if (idx < 0) {
                int m = json.indexOf("\"message\"");
                if (m >= 0) {
                    int c = json.indexOf("\"content\":\"", m);
                    if (c < 0) return null;
                    idx = c;
                } else {
                    return null;
                }
            }
            int start = idx + 11;
            StringBuilder sb = new StringBuilder();
            int i = start;
            while (i < json.length()) {
                char ch = json.charAt(i);
                if (ch == '\\') {
                    if (i + 1 < json.length()) {
                        char n = json.charAt(i + 1);
                        switch (n) {
                            case 'n': sb.append('\n'); break;
                            case 't': sb.append('\t'); break;
                            case 'r': sb.append('\r'); break;
                            case '"': sb.append('"'); break;
                            case '\\': sb.append('\\'); break;
                            default: sb.append(n);
                        }
                        i += 2;
                        continue;
                    }
                    sb.append(ch);
                } else if (ch == '"') {
                    break;
                } else {
                    sb.append(ch);
                }
                i++;
            }
            return sb.toString();
        }

        /** 保存 AI API 配置（含视觉模型） */
        public static boolean saveAIApiConfig(String apiKey, String modelName, String visionModel, String endpointUrl, String proxyHost, String proxyPort) {
            Map<String, String> existing = getAIApiConfig();
            String sql;
            boolean hasRecord = existing != null && !existing.getOrDefault("api_key", "").isEmpty();
            try (Connection conn = getConnection()) {
                if (hasRecord) {
                    sql = "UPDATE ai_api_config SET api_key = ?, model_name = ?, vision_model = ?, endpoint = ?, proxy_host = ?, proxy_port = ?, updated_at = CURRENT_TIMESTAMP WHERE provider = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, apiKey);
                        ps.setString(2, modelName);
                        ps.setString(3, visionModel);
                        ps.setString(4, endpointUrl);
                        ps.setString(5, proxyHost);
                        ps.setString(6, proxyPort);
                        ps.setString(7, "zhipu");
                        return ps.executeUpdate() > 0;
                    }
                } else {
                    sql = "INSERT INTO ai_api_config (provider, api_key, model_name, vision_model, endpoint, proxy_host, proxy_port) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, "zhipu");
                        ps.setString(2, apiKey);
                        ps.setString(3, modelName);
                        ps.setString(4, visionModel);
                        ps.setString(5, endpointUrl);
                        ps.setString(6, proxyHost);
                        ps.setString(7, proxyPort);
                        return ps.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /** 执行数据库备份（导出 SQL 到文件） */
        public static boolean backupDatabase(String outputPath) {
            try (Connection conn = getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                StringBuilder sb = new StringBuilder();
                sb.append("-- Backup generated at ").append(new Date()).append("\n\n");
                // 简单导出用户表、健康记录、饮食、运动、成就数据
                exportTable(conn, sb, "users");
                exportTable(conn, sb, "health_records");
                exportTable(conn, sb, "diet_records");
                exportTable(conn, sb, "exercise_records");
                exportTable(conn, sb, "goals");
                exportTable(conn, sb, "achievements");
                exportTable(conn, sb, "ai_reports");
                exportTable(conn, sb, "system_config");
                exportTable(conn, sb, "foods");
                exportTable(conn, sb, "exercise_library");
                exportTable(conn, sb, "health_articles");
                exportTable(conn, sb, "message_templates");
                exportTable(conn, sb, "notifications");
                exportTable(conn, sb, "ai_templates");
                exportTable(conn, sb, "ai_chat_records");

                try (FileWriter fw = new FileWriter(outputPath)) {
                    fw.write(sb.toString());
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        private static void exportTable(Connection conn, StringBuilder sb, String table) throws SQLException {
            sb.append("-- Table: ").append(table).append("\n");
            Statement st = conn.createStatement();
            try {
                ResultSet rs = st.executeQuery("SELECT * FROM " + table);
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                while (rs.next()) {
                    sb.append("INSERT INTO ").append(table).append(" VALUES (");
                    for (int i = 1; i <= colCount; i++) {
                        String val = rs.getString(i);
                        sb.append(val == null ? "NULL" : "'" + val.replace("'", "''") + "'");
                        if (i < colCount) sb.append(",");
                    }
                    sb.append(");\n");
                }
            } catch (SQLException e) {
                // 表可能不存在，跳过
                sb.append("-- skipped or empty\n");
            }
            sb.append("\n");
        }
    }

    // ================================================================
    //                  第三部分: 健康计算工具类
    // ================================================================

    /**
     * 健康计算工具类 — 封装所有健康指标计算逻辑
     * 包含: BMI / BMR(三公式) / TDEE / 身体年龄 / 体质分类 / 健康评分 / 预测
     */
