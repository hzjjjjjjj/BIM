-- ============================================================
-- BMI 体质评估与预测系统 — 数据库初始化脚本
-- 数据库: health_db  |  用户: postgres  |  端口: 5432
-- ============================================================

-- 创建数据库
CREATE DATABASE health_db;
\c health_db;

-- ============ 用户表 ============
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,              -- SHA-256 加盐哈希
    salt VARCHAR(64) NOT NULL,                   -- 随机盐值
    gender VARCHAR(10),
    age INT CHECK (age > 0 AND age < 150),
    height DECIMAL(5,2) CHECK (height > 50 AND height < 300),
    activity_level VARCHAR(20) DEFAULT '久坐',
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============ 健康记录表 ============
CREATE TABLE health_records (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    record_date DATE DEFAULT CURRENT_DATE,
    weight DECIMAL(5,2) CHECK (weight > 0 AND weight < 500),
    body_fat DECIMAL(4,2) CHECK (body_fat >= 0 AND body_fat <= 100),
    water_rate DECIMAL(4,2) CHECK (water_rate >= 0 AND water_rate <= 100),
    muscle_rate DECIMAL(4,2) CHECK (muscle_rate >= 0 AND muscle_rate <= 100),
    visceral_fat INT CHECK (visceral_fat >= 0 AND visceral_fat <= 30),
    bone_muscle DECIMAL(4,2),
    bmr DECIMAL(6,2),
    tdee DECIMAL(6,2),
    bmi DECIMAL(4,2),
    waist DECIMAL(5,2) CHECK (waist > 0 AND waist < 200),
    body_age INT,
    body_type VARCHAR(20),
    patient_id INT                                    -- 机构记录关联到 patients (个人记录为 NULL)
);

-- ============ 运动记录表 ============
CREATE TABLE exercise_records (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    record_date DATE DEFAULT CURRENT_DATE,
    exercise_type VARCHAR(50),
    duration INT CHECK (duration > 0 AND duration < 600),
    intensity VARCHAR(10) CHECK (intensity IN ('低', '中', '高')),
    calories_burned INT
);

-- ============ 目标表 ============
CREATE TABLE goals (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    goal_type VARCHAR(20),
    target_value DECIMAL(5,2),
    start_date DATE DEFAULT CURRENT_DATE,
    end_date DATE,
    current_stage INT DEFAULT 1
);

-- ============ 医疗机构表 ============
CREATE TABLE institutions (
    id SERIAL PRIMARY KEY,
    org_name VARCHAR(100) NOT NULL,
    org_code VARCHAR(50) UNIQUE,                       -- 机构编码 / 统一社会信用代码
    password VARCHAR(255) NOT NULL DEFAULT '',         -- SHA-256 加盐哈希
    salt VARCHAR(64) NOT NULL DEFAULT '',
    contact VARCHAR(50),
    email VARCHAR(120),                                -- 机构联系邮箱 (审批通过后向其发送机构编号)
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============ 机构维度病人主表 (归属某机构, 不持有登录账号 — 与个人 users 解耦) ============
-- archived=TRUE 表示已被机构「移出名单」(软删除), 其健康记录仍保留。
CREATE TABLE patients (
    id SERIAL PRIMARY KEY,
    institution_id INT NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    patient_code VARCHAR(50) NOT NULL,
    name VARCHAR(100),
    gender VARCHAR(10),
    age INT,
    height DECIMAL(5,2),
    weight DECIMAL(5,2),
    waist DECIMAL(5,2),
    activity_level VARCHAR(20) DEFAULT '久坐',
    archived BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (institution_id, patient_code)
);
CREATE INDEX idx_patients_inst ON patients(institution_id, archived);
ALTER TABLE health_records ADD CONSTRAINT fk_hr_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL;

-- ============ 机构入驻申请表 (待管理员审批) ============
CREATE TABLE institution_requests (
    id SERIAL PRIMARY KEY,
    org_name VARCHAR(100) NOT NULL,
    contact VARCHAR(50),
    phone VARCHAR(30),
    email VARCHAR(120),                                -- 机构联系邮箱 (审批通过后向其发送机构编号)
    note TEXT,
    password VARCHAR(255) NOT NULL DEFAULT '',         -- 机构自设密码的 SHA-256 加盐哈希
    salt VARCHAR(64) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',   -- pending / approved / rejected
    reviewer VARCHAR(50),
    review_note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP
);

-- ============ 饮食记录表 ============
CREATE TABLE diet_records (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    record_date DATE DEFAULT CURRENT_DATE,
    meal_type VARCHAR(10),
    food_name VARCHAR(100),
    calories INT,
    protein DECIMAL(5,2),
    carbs DECIMAL(5,2),
    fat DECIMAL(5,2)
);

-- ============ 成就表 ============
CREATE TABLE achievements (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    badge_name VARCHAR(50),
    achieved_date DATE DEFAULT CURRENT_DATE
);

-- ============ AI 报告表 ============
CREATE TABLE ai_reports (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    report_type VARCHAR(20),
    report_content TEXT,
    generated_at TIMESTAMP DEFAULT NOW()
);

-- ============ 食物数据库表 ============
CREATE TABLE foods (
    id SERIAL PRIMARY KEY,
    food_name VARCHAR(100),
    calories INT,
    protein DECIMAL(5,2),
    carbs DECIMAL(5,2),
    fat DECIMAL(5,2)
);

-- ============ 索引 ============
CREATE INDEX idx_hr_username_date ON health_records(username, record_date);
CREATE INDEX idx_diet_username_date ON diet_records(username, record_date);
CREATE INDEX idx_exer_username_date ON exercise_records(username, record_date);

-- ============ 饮水记录表 ============
CREATE TABLE IF NOT EXISTS water_records (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) REFERENCES users(username),
    record_date DATE DEFAULT CURRENT_DATE,
    amount_ml INT CHECK (amount_ml > 0 AND amount_ml <= 3000),
    note VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============ 每日饮水目标表（用户自定义目标，覆盖按体重估算值） ============
CREATE TABLE IF NOT EXISTS water_goals (
    username VARCHAR(50) PRIMARY KEY REFERENCES users(username),
    goal_ml INT CHECK (goal_ml >= 500 AND goal_ml <= 6000)
);

CREATE INDEX IF NOT EXISTS idx_water_username_date ON water_records(username, record_date);

-- ============ 插入 80 种常见食物数据 ============
INSERT INTO foods (food_name, calories, protein, carbs, fat) VALUES
('米饭', 116, 2.6, 25.9, 0.3),
('面条', 137, 4.5, 28.5, 0.5),
('馒头', 221, 7.0, 47.0, 1.0),
('包子', 220, 7.5, 38.0, 4.0),
('饺子', 250, 8.0, 30.0, 10.0),
('油条', 388, 6.9, 43.0, 20.0),
('豆浆', 33, 3.0, 1.5, 1.8),
('牛奶', 54, 3.0, 4.5, 3.0),
('鸡蛋', 144, 13.0, 1.5, 9.0),
('鸡胸肉', 133, 31.0, 0.0, 2.8),
('鸡腿肉', 181, 24.0, 0.0, 9.0),
('猪肉瘦', 143, 20.0, 0.0, 6.0),
('猪肉肥', 395, 14.0, 0.0, 37.0),
('牛肉瘦', 125, 26.0, 0.0, 2.0),
('牛肉肥', 332, 18.0, 0.0, 28.0),
('羊肉', 205, 20.0, 0.0, 13.0),
('鱼肉', 113, 20.0, 0.0, 3.0),
('虾肉', 85, 18.0, 0.0, 1.0),
('豆腐', 81, 8.0, 3.5, 4.0),
('豆腐干', 140, 16.0, 5.0, 6.0),
('豆浆粉', 422, 18.0, 55.0, 12.0),
('白菜', 13, 1.5, 2.0, 0.1),
('菠菜', 23, 2.5, 3.0, 0.3),
('西兰花', 34, 3.0, 5.0, 0.5),
('菜花', 25, 2.0, 4.0, 0.3),
('黄瓜', 15, 0.8, 3.0, 0.1),
('西红柿', 18, 0.9, 4.0, 0.2),
('茄子', 25, 1.0, 5.0, 0.2),
('青椒', 22, 1.0, 4.5, 0.2),
('土豆', 77, 2.0, 17.0, 0.1),
('红薯', 86, 1.6, 20.0, 0.1),
('山药', 57, 1.5, 12.0, 0.1),
('南瓜', 23, 1.0, 5.0, 0.1),
('冬瓜', 12, 0.4, 2.5, 0.1),
('萝卜', 16, 0.6, 3.5, 0.1),
('胡萝卜', 41, 1.0, 9.5, 0.2),
('洋葱', 40, 1.1, 9.0, 0.1),
('大蒜', 149, 6.0, 33.0, 0.5),
('姜', 80, 1.8, 17.0, 0.8),
('苹果', 52, 0.3, 14.0, 0.2),
('香蕉', 89, 1.1, 23.0, 0.3),
('橙子', 47, 0.9, 12.0, 0.1),
('西瓜', 30, 0.6, 7.5, 0.1),
('哈密瓜', 34, 0.5, 8.0, 0.1),
('葡萄', 69, 0.7, 18.0, 0.2),
('草莓', 32, 0.7, 7.5, 0.3),
('蓝莓', 57, 0.7, 14.5, 0.3),
('桃子', 39, 0.9, 10.0, 0.1),
('梨', 42, 0.4, 10.0, 0.1),
('李子', 46, 0.7, 12.0, 0.3),
('樱桃', 50, 1.0, 12.0, 0.3),
('芒果', 60, 0.8, 15.0, 0.4),
('火龙果', 60, 1.2, 14.0, 0.4),
('猕猴桃', 61, 1.0, 15.0, 0.5),
('柠檬', 29, 1.1, 6.0, 0.3),
('核桃', 654, 15.0, 14.0, 65.0),
('花生', 567, 26.0, 16.0, 49.0),
('瓜子', 600, 24.0, 12.0, 53.0),
('黑芝麻', 559, 17.0, 12.0, 50.0),
('小米', 358, 9.0, 75.0, 3.0),
('玉米', 112, 3.5, 22.0, 1.5),
('燕麦', 389, 17.0, 66.0, 7.0),
('荞麦', 337, 13.0, 71.0, 3.0),
('意面', 131, 5.0, 25.0, 0.5),
('蛋糕', 348, 5.0, 45.0, 17.0),
('面包', 265, 9.0, 50.0, 3.0),
('饼干', 450, 6.0, 65.0, 18.0),
('巧克力', 546, 4.0, 60.0, 32.0),
('冰淇淋', 207, 3.5, 24.0, 11.0),
('酸奶', 72, 3.5, 8.0, 3.0),
('奶酪', 360, 25.0, 2.0, 28.0),
('黄油', 717, 0.5, 0.0, 81.0),
('植物油', 884, 0.0, 0.0, 100.0),
('酱油', 60, 2.0, 10.0, 0.0),
('醋', 18, 0.4, 4.0, 0.0),
('蜂蜜', 304, 0.3, 82.0, 0.0),
('白糖', 387, 0.0, 100.0, 0.0),
('红糖', 380, 0.0, 95.0, 0.0),
('盐', 0, 0.0, 0.0, 0.0),
('茶叶', 0, 0.0, 0.0, 0.0),
('咖啡', 1, 0.1, 0.0, 0.0);
