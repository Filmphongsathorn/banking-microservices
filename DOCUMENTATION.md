# NexusBank System Documentation & Code Review

## 1. System Overview (ภาพรวมระบบ)
NexusBank เป็นระบบ Core Banking สมัยใหม่ที่ถูกออกแบบด้วยสถาปัตยกรรม Microservices โดยเน้นความปลอดภัย (Security), ความสอดคล้องของข้อมูล (Data Consistency), และความสามารถในการขยายตัว (Scalability) 

ระบบแบ่งออกเป็น Services ย่อยๆ ดังนี้:
1. **API Gateway (`api-gateway`)**: ทำหน้าที่เป็นประตูทางเข้าของระบบ จัดการ Routing, Rate Limiting และ Validate JWT Token เบื้องต้น
2. **Service Registry (`service-registry` / Eureka)**: จัดการ Service Discovery ให้แต่ละ Microservice ค้นหากันเจอ
3. **Config Server (`config-server`)**: รวมศูนย์การจัดการ Configuration (ไฟล์ `.yml`) ของทุก Services
4. **Auth Service (`auth-service`)**: ดูแลเรื่องการสมัครสมาชิก (Register) สร้างและตรวจสอบ JWT Token รวมถึงเข้ารหัส Password
5. **Account Service (`account-service`)**: จัดการบัญชีธนาคาร ยอดเงิน และระบบ Ledger (สมุดบัญชีคู่) เพื่อบันทึกประวัติการเปลี่ยนแปลงของยอดเงิน
6. **Transaction Service (`transaction-service`)**: จัดการการฝาก ถอน โอนเงิน มีระบบ Saga Pattern และ Idempotency Key ป้องกันการทำรายการซ้ำซ้อน
7. **Frontend (`frontend`)**: Web Application พัฒนาด้วย React (Vite) นำเสนอ UI ที่สวยงาม ทันสมัย พร้อมระบบแจ้งเตือนด้วย SweetAlert2

---

## 2. Code Review & Key Engineering Practices (รีวิวโค้ดและเทคนิคเชิงวิศวกรรม)

### 2.1 Security & Data Consistency (ความปลอดภัยและความถูกต้องของข้อมูล)
- **Optimistic Locking (`@Version`)**: ใน Entity ของ `Account` มีการใส่เวอร์ชันไว้ หากมีผู้ใช้พยายามถอนเงินพร้อมกันจาก 2 อุปกรณ์ ระบบจะปฏิเสธรายการที่ช้ากว่าทันที ป้องกันปัญหา "เงินติดลบ" (Double Spending/Race Condition)
- **Idempotency Key**: ใน Transaction Service มีการทำตารางและ Constraint `UNIQUE` สำหรับ `idempotency_key` ควบคู่กับการใช้ Redis ทำให้การกดปุ่มโอนเงินเบิ้ล หรือเน็ตกระตุกแล้วส่ง Request ซ้ำ จะไม่ทำให้เงินถูกโอนออกไปสองรอบ
- **Double-Entry Ledger**: มีการสร้างตาราง `ledger_entries` เพื่อบันทึกประวัติทุกความเคลื่อนไหวของยอดเงิน (ใครฝาก ใครถอน ยอดก่อน/หลังทำรายการคือเท่าไหร่) ทำให้ตรวจสอบย้อนหลังได้ 100%
- **JWT & BCrypt**: รหัสผ่านของผู้ใช้ถูกเข้ารหัสด้วย BCrypt และการยืนยันตัวตนใช้ JWT Token ที่รัดกุม

### 2.2 Architecture & Messaging (สถาปัตยกรรมและการสื่อสาร)
- **Event-Driven ด้วย Apache Kafka**: เมื่อมีเหตุการณ์สำคัญเกิดขึ้น (เช่น สร้างบัญชีสำเร็จ, โอนเงินสำเร็จ) ระบบจะส่ง Event ไปยัง Kafka Message Broker เพื่อให้ Service อื่นๆ (เช่น Notification Service) รับไปประมวลผลต่อโดยไม่ทำให้ Request หลักต้องรอ (Asynchronous Processing)
- **Saga Pattern Recovery**: หากการโอนเงินข้าม Service ขัดข้อง (เช่น โอนเงินออกไปแล้วแต่ฝั่งผู้รับ Server ล่ม) จะมี `@Scheduled` Recovery Job คอยรันอยู่เบื้องหลังเพื่อ Retry หรือทำ Compensation (ชดเชยยอดเงินคืน)
- **Spring Cloud Netflix Eureka**: จัดการ Load Balancing และค้นหา IP ภายในกันเอง ทำให้ง่ายต่อการสเกลระบบ

### 2.3 Frontend UI/UX
- **React + Vite**: รันได้รวดเร็ว Hot-reload ทันใจ
- **Modern UI & CSS**: ออกแบบ UI ด้วยสไตล์ Glassmorphism, เงา (Shadows), และโทนสีที่สอดคล้องกับธนาคารจริงๆ
- **SweetAlert2**: เปลี่ยนระบบ Alert ธรรมดาให้เป็น Popup ที่มีแอนิเมชันสวยงาม แบ่งแยกสีชัดเจน (เขียว=สำเร็จ, แดง=ผิดพลาด) 
- **Client-Side Validation**: ตรวจสอบความถูกต้องเบื้องต้นจากฝั่งผู้ใช้ (เช่น เลขบัญชี 10 หลัก) ช่วยลดภาระการประมวลผลของ Server

---

## 3. How to Run (คู่มือการรันระบบ)

ระบบทั้งหมดทำงานอยู่บน Docker Compose เพื่อให้ง่ายต่อการจำลอง Environment

### การสตาร์ทระบบทั้งหมด:
1. เปิด Terminal ในโฟลเดอร์โปรเจกต์ `banking-system`
2. รันคำสั่ง:
   ```bash
   docker-compose up -d --build
   ```
3. รอประมาณ 30 วินาที - 1 นาที เพื่อให้ Services ทั้งหมดสตาร์ทเสร็จสิ้นสมบูรณ์ (โดยเฉพาะ Kafka และ Database)

### การตรวจสอบสถานะ:
- **Eureka Dashboard**: `http://localhost:8761` (เข้าไปดูว่า Service ไหนออนอยู่บ้าง)
- **API Gateway**: `http://localhost:8080` (เข้าถึง API ผ่านพอร์ตนี้)
- **Frontend App**: `http://localhost:3000` (กรณีรันด้วย `npm run dev`)

### การรัน Frontend (ฝั่ง UI):
1. เปิด Terminal ใหม่เข้าไปที่โฟลเดอร์ `frontend`
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
2. เปิดเบราว์เซอร์เข้าไปที่ `http://localhost:3000`

---

## 4. User Manual (คู่มือการใช้งานสำหรับผู้ใช้)

1. **การสมัครสมาชิก (Register)**:
   - เข้าไปที่หน้าเว็บ เลือก "Sign up"
   - กรอก Username และ Password (ต้องตรงกันสองช่อง)
   - หากสำเร็จ ระบบจะขึ้น popup สีเขียว และพาไปหน้า Login

2. **การเปิดบัญชี (Open Account)**:
   - หลังจาก Login เข้ามา จะเจอหน้า Dashboard แจ้งว่า "No accounts found"
   - คลิกปุ่ม "Open Account" ใส่ตัวเลข 10 หลัก (เช่น `1234567890`)
   - ระบบจะตรวจเช็ค หากรหัสบัญชีซ้ำหรือความยาวไม่ถึง 10 หลัก จะมี popup แจ้งเตือน

3. **การฝากเงิน (Deposit)**:
   - ในหัวข้อ "Quick Actions" เลือก "Deposit"
   - เลือกบัญชีปลายทาง และระบุจำนวนเงิน
   - กด Confirm ยอดเงินจะอัปเดตทันทีพร้อมแจ้งเตือนสีเขียว

4. **การโอนเงิน (Transfer)**:
   - เลือก "Transfer Money"
   - เลือกบัญชีต้นทาง (บัญชีของคุณ) 
   - กรอกเลขบัญชีปลายทาง (10 หลัก) และระบุยอดเงินที่ต้องการโอน
   - ระบบจะทำการหักเงิน และเพิ่มเงินในบัญชีปลายทางอย่างปลอดภัย หากบัญชีปลายทางไม่มีอยู่จริง ระบบจะ Reject รายการและคืนเงินให้

---

## 5. Areas for Future Improvement (ส่วนที่สามารถพัฒนาต่อยอดได้)
- **API Documentation**: ควรติดตั้ง Swagger (OpenAPI) สำหรับทุก Microservice เพื่อง่ายต่อการทดสอบ API ของนักพัฒนา
- **CI/CD Pipeline**: ควรเขียน GitHub Actions หรือ GitLab CI เพื่อทำ Automated Testing ทุกครั้งที่มีการ Push Code
- **Monitoring & Tracing**: ติดตั้ง Prometheus, Grafana และ Zipkin (หรือ Jaeger) เพื่อจับตาดู Performance ของระบบ และ Trace Request ว่าวิ่งข้าม Service ไหนใช้เวลาเท่าไหร่

*Documented by Antigravity AI*
