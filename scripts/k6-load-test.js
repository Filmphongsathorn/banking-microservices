import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // จำลองคนใช้งานพร้อมกัน 500 คน (ถ้าใส่ 100,000 เครื่องคอมพิวเตอร์ของคุณจะค้างแน่นอน)
  vus: 500,
  // ยิงโหลดต่อเนื่องเป็นเวลา 30 วินาที
  duration: '30s',
};

export default function () {
  // ยิง Request ไปที่ API Gateway เพื่อดึงข้อมูลบัญชี (สมมติบัญชี ID: 1)
  const res = http.get('http://localhost:8080/api/v1/accounts/1');

  // ตรวจสอบว่า HTTP Status ต้องเป็น 200 หรือ 404 (มีบัญชีหรือไม่มีบัญชีก็ได้ ขอแค่ระบบไม่ล่ม)
  check(res, {
    'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'transaction time OK': (r) => r.timings.duration < 2000, // Response ควรเร็วกว่า 2 วินาที
  });

  // หน่วงเวลาเล็กน้อยเพื่อให้เหมือนพฤติกรรมคนจริงๆ ที่ต้องมีจังหวะอ่านหน้าจอ
  sleep(1);
}
