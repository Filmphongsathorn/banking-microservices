import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // จำลองคนใช้งานระดับโหด (1,000 Concurrent Users)
  stages: [
    { duration: '10s', target: 200 },  // ไต่ระดับไปที่ 200 คนพร้อมกัน ภายใน 10 วินาที
    { duration: '20s', target: 1000 }, // อัดไปที่ 1,000 คนพร้อมกัน ค้างไว้ 20 วินาที
    { duration: '5s', target: 0 },     // ค่อยๆ ลดลงจนเหลือ 0
  ],
};

export default function () {
  // ยิงทะลุตรงไปที่ Account Service (พอร์ต 8083) เพื่อข้ามการเช็ค JWT Token และทดสอบว่าต่อให้ยิงรัวแค่ไหน ระบบก็ต้องไม่ล่ม
  const res = http.get('http://host.docker.internal:8083/api/v1/accounts/9999999999');

  check(res, {
    'Response Status is 200 (Found) or 404 (Not Found)': (r) => r.status === 200 || r.status === 404,
    'Response Time < 500ms': (r) => r.timings.duration < 500, // ต้องตอบกลับเร็วกว่าครึ่งวินาที
  });

  // หน่วงเวลา 0.1 วินาที สมมติเป็นจังหวะกดรัวๆ
  sleep(0.1);
}
