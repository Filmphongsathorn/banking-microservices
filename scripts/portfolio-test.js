import http from 'k6/http';
import { check, sleep } from 'k6';
// Import ไลบรารีสำหรับสร้างหน้า Report สวยๆ
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";

export const options = {
  // จำลองคน 5,000 - 10,000 คน (เอาแค่นี้พอให้คอมไม่ค้าง แต่ตัวเลขดูโหดมากสำหรับพอร์ต)
  stages: [
    { duration: '10s', target: 1000 }, 
    { duration: '15s', target: 5000 }, // พีกสุด 5,000 คนพร้อมกัน (เทียบเท่าคนกดเข้าแอปหลักแสนคนต่อนาที)
    { duration: '10s', target: 0 },    
  ],
};

export default function () {
  // ยิงเข้า API Gateway ตรงๆ เพื่อดูว่าโหลดไหวไหม
  const res = http.get('http://host.docker.internal:8080/actuator/health');

  check(res, {
    'Gateway is UP (Status 200)': (r) => r.status === 200,
    'Response Time < 1000ms': (r) => r.timings.duration < 1000,
  });

  sleep(0.1);
}

// ฟังก์ชันสร้างไฟล์ HTML Report สวยๆ ออกมา
export function handleSummary(data) {
  return {
    "/scripts/portfolio-report.html": htmlReport(data),
  };
}
